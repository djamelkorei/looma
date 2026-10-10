package io.github.djamelkorei.looma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.djamelkorei.looma.directive.BuiltinDirectives;
import io.github.djamelkorei.looma.spi.Directive;
import io.github.djamelkorei.looma.spi.Expr;
import io.github.djamelkorei.looma.spi.Node;
import io.github.djamelkorei.looma.spi.ParseContext;
import io.github.djamelkorei.looma.spi.Syntax;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Custom directives use exactly the same SPI as the built-in ones. */
class CustomDirectiveTest {

    /** {@code @upper($x)}: the smallest useful directive. */
    static final Directive UPPER = new Directive() {
        public String name() { return "upper"; }
        public Node parse(ParseContext p) {
            Expr value = p.expr();
            return ctx -> ctx.writeEscaped(String.valueOf(ctx.eval(value)).toUpperCase());
        }
    };

    /** {@code @repeat(3) ... @endrepeat}: a block directive that renders its body several times. */
    static final Directive REPEAT = new Directive() {
        public String name() { return "repeat"; }
        public Map<String, Syntax> tags() { return Map.of("endrepeat", Syntax.NONE); }
        public Node parse(ParseContext p) {
            Expr times = p.expr();
            List<Node> body = p.block("endrepeat").nodes();
            return ctx -> {
                int n = ((Number) ctx.eval(times)).intValue();
                for (int i = 1; i <= n; i++) {
                    var inner = ctx.child();
                    inner.set("n", i);
                    inner.render(body);
                }
            };
        }
    };

    /** {@code @note('...')}: produces no output at all (parse returns null). */
    static final Directive NOTE = new Directive() {
        public String name() { return "note"; }
        public Node parse(ParseContext p) {
            p.literal(p.args());
            return null;
        }
    };

    private final Map<String, String> files = new HashMap<>();
    private final TemplateLoader loader = new TemplateLoader() {
        public boolean exists(String name) { return files.containsKey(name); }
        public String load(String name) { return files.get(name); }
    };

    private String render(Directive d, String template, Map<String, Object> model) {
        files.put("t", template);
        return LoomaEngine.builder(loader).cache(false).directive(d).build().render("t", model);
    }

    @Test
    void inlineDirective() {
        assertEquals("HELLO &lt;B&gt;", render(UPPER, "@upper($s)", Map.of("s", "hello <b>")));
    }

    @Test
    void blockDirectiveWithItsOwnScopeAndNestedBuiltins() {
        assertEquals("1a 2a ", render(REPEAT, "@repeat(2){{ $n }}@if($x)a@endif @endrepeat", Map.of("x", true)));
    }

    @Test
    void directiveMayProduceNoOutput() {
        assertEquals("ab", render(NOTE, "a@note('todo')b", Map.of()));
    }

    @Test
    void customDirectivesCoexistWithBuiltInsAndReportErrorsWithLines() {
        LoomaException e = assertThrows(LoomaException.class,
                () -> render(REPEAT, "line1\n@repeat(2)\nnever closed", Map.of()));
        assertTrue(e.getMessage().contains("Unclosed block"), e.getMessage());
        // a stray closing tag is reported instead of silently printed
        LoomaException stray = assertThrows(LoomaException.class, () -> render(REPEAT, "x\n@endrepeat", Map.of()));
        assertEquals(2, stray.line());
    }

    @Test
    void clashesAreRejectedWhenTheEngineIsBuilt() {
        Directive clash = new Directive() {
            public String name() { return "if"; }
            public Node parse(ParseContext p) { return null; }
        };
        assertThrows(IllegalArgumentException.class, () -> LoomaEngine.builder(loader).directive(clash).build());
        assertThrows(IllegalArgumentException.class,
                () -> LoomaEngine.builder(loader).directive(UPPER).directive(UPPER).build());
        Directive reserved = new Directive() {
            public String name() { return "verbatim"; }
            public Node parse(ParseContext p) { return null; }
        };
        assertThrows(IllegalArgumentException.class, () -> LoomaEngine.builder(loader).directive(reserved).build());
        Directive badName = new Directive() {
            public String name() { return "my-dir"; }
            public Node parse(ParseContext p) { return null; }
        };
        assertThrows(IllegalArgumentException.class, () -> LoomaEngine.builder(loader).directive(badName).build());
    }

    @Test
    void perRenderStateAndFinishHook() {
        Directive counter = new Directive() {
            public String name() { return "count"; }
            public Syntax syntax() { return Syntax.NONE; }
            public Node parse(ParseContext p) {
                return ctx -> {
                    int[] n = ctx.state(int[].class, () -> new int[1]);
                    n[0]++;
                    if (n[0] == 1) ctx.onFinish(page -> page + "|total=" + n[0]);
                    ctx.write(String.valueOf(n[0]));
                };
            }
        };
        assertEquals("123|total=3", render(counter, "@count@count@count", Map.of()));
        // state does not leak between renders
        files.put("t", "@count");
        LoomaEngine e = LoomaEngine.builder(loader).cache(false).directive(counter).build();
        assertEquals("1|total=1", e.render("t", Map.of()));
        assertEquals("1|total=1", e.render("t", Map.of()));
    }

    @Test
    void builtInListIsTheEngineFeatureSet() {
        List<String> names = BuiltinDirectives.all().stream().map(Directive::name).toList();
        assertTrue(names.containsAll(List.of("if", "foreach", "for", "while", "include", "extends", "section",
                "push", "csrf", "lang", "switch")), names.toString());
        assertEquals(names.size(), names.stream().distinct().count(), "built-in names must be unique");
    }
}
