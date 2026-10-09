package io.github.djamelkorei.looma.directive;

import io.github.djamelkorei.looma.spi.Directive;
import io.github.djamelkorei.looma.spi.Expr;
import io.github.djamelkorei.looma.spi.Node;
import io.github.djamelkorei.looma.spi.ParseContext;
import io.github.djamelkorei.looma.spi.ParseContext.Block;
import io.github.djamelkorei.looma.spi.ParseContext.Tag;
import io.github.djamelkorei.looma.spi.RenderContext;
import io.github.djamelkorei.looma.spi.Syntax;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@code @switch($x) @case(1) ... @break @case(2) @case(3) ... @default ... @endswitch}.
 * Consecutive empty cases share the next body; there is no other fall-through.
 */
public final class SwitchDirective implements Directive {

    /** A case with no tests is the default. */
    private record Case(List<Expr> tests, List<Node> body) {}

    private record SwitchNode(Expr subject, List<Case> cases) implements Node {
        @Override
        public void render(RenderContext ctx) {
            Object value = ctx.eval(subject);
            Case fallback = null;
            for (Case c : cases) {
                if (c.tests().isEmpty()) {
                    fallback = c;
                    continue;
                }
                for (Expr test : c.tests()) {
                    if (looselyEqual(value, ctx.eval(test))) {
                        ctx.runSwitchBody(c.body());
                        return;
                    }
                }
            }
            if (fallback != null) ctx.runSwitchBody(fallback.body());
        }

        private static boolean looselyEqual(Object a, Object b) {
            if (a instanceof Number x && b instanceof Number y) return x.doubleValue() == y.doubleValue();
            if (a instanceof Enum<?> e && b instanceof String s) return e.name().equals(s);
            if (a instanceof String s && b instanceof Enum<?> e) return e.name().equals(s);
            return Objects.equals(a, b);
        }
    }

    @Override public String name() { return "switch"; }

    @Override
    public Map<String, Syntax> tags() {
        return Map.of("case", Syntax.REQUIRED, "default", Syntax.NONE, "endswitch", Syntax.NONE);
    }

    @Override
    public Node parse(ParseContext p) {
        Expr subject = p.expr();
        List<Case> cases = new ArrayList<>();
        List<Expr> pending = new ArrayList<>();
        Block block = next(p);
        if (!p.isBlank(block.nodes())) {
            throw p.error("Only @case and @default are allowed directly inside @switch");
        }
        while (true) {
            Tag tag = block.end();
            switch (tag.name()) {
                case "endswitch" -> {
                    if (!pending.isEmpty()) cases.add(new Case(List.copyOf(pending), List.of()));
                    return new SwitchNode(subject, cases);
                }
                case "case" -> {
                    pending.add(p.expr(tag));
                    block = next(p);
                    if (!p.isBlank(block.nodes())) {
                        cases.add(new Case(List.copyOf(pending), block.nodes()));
                        pending.clear();
                    }
                }
                default -> { // "default"
                    block = next(p);
                    if (!pending.isEmpty()) cases.add(new Case(List.copyOf(pending), block.nodes()));
                    pending.clear();
                    cases.add(new Case(List.of(), block.nodes()));
                }
            }
        }
    }

    private static Block next(ParseContext p) {
        return p.within("switch", () -> p.block("case", "default", "endswitch"));
    }
}
