package io.github.djamelkorei.looma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.djamelkorei.looma.spi.TemplateHelper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** String/Math helpers and custom helpers: class-level functionality without opening T(...). */
class HelperTest {

    private final Map<String, String> files = new HashMap<>();
    private final TemplateLoader loader = new TemplateLoader() {
        public boolean exists(String name) { return files.containsKey(name); }
        public String load(String name) { return files.get(name); }
    };

    private String render(LoomaEngine engine, String template, Map<String, Object> model) {
        files.put("t", template);
        return engine.render("t", model);
    }

    private String render(String template, Map<String, Object> model) {
        return render(new LoomaEngine(loader, false), template, model);
    }

    @Test
    void stringHelperCoversTheStaticStringMethods() {
        assertEquals("1.50 €", render("{{ String.format('%.2f €', $price) }}", Map.of("price", 1.5)));
        assertEquals("1,50", render("{{ String.formatLocale('fr', '%.2f', $price) }}", Map.of("price", 1.5)));
        assertEquals("a, b, c", render("{{ String.join(', ', $tags) }}", Map.of("tags", List.of("a", "b", "c"))));
        assertEquals("x-y", render("{{ String.join('-', 'x', 'y') }}", Map.of()));
        assertEquals("42", render("{{ String.valueOf(42) }}", Map.of()));
        assertEquals("true true false", render("{{ String.isBlank($none) }} {{ String.isBlank('  ') }} {{ String.isEmpty(' ') }}", Map.of()));
        assertEquals("&lt;b&gt;", render("{{ String.format('%s', '<b>') }}", Map.of()), "helper output is escaped like anything else");
    }

    @Test
    void mathHelperKeepsWholeNumbersWhole() {
        assertEquals("5 5.5 3 3 2 3 8.0 3.0", render("{{ Math.max(2, 5) }} {{ Math.max(2, 5.5) }} {{ Math.abs(-3) }} "
                + "{{ Math.round(2.6) }} {{ Math.floor(2.9) }} {{ Math.ceil($n / 10.0) }} {{ Math.pow(2, 3) }} {{ Math.sqrt(9) }}",
                Map.of("n", 21)));
        assertEquals("1", render("{{ Math.min(1, 7) }}", Map.of()));
    }

    @Test
    void helpersWorkInIncludesAndComponentsButTheModelWins() {
        files.put("part", "{{ String.valueOf(1) }}");
        files.put("components.box", "[{{ Math.max(1, 2) }}]");
        assertEquals("1[2]", render("@include('part')<x-box />", Map.of()));
        assertEquals("mine", render("{{ String }}", Map.of("String", "mine")));
    }

    public static class Price implements TemplateHelper {
        public String name() { return "Price"; }
        public String eur(Number amount) { return String.format("%.2f €", amount.doubleValue()); }
    }

    @Test
    void customHelpersAreRegisteredByName() {
        LoomaEngine engine = LoomaEngine.builder(loader).cache(false).helper(new Price()).build();
        assertEquals("12.50 €", render(engine, "{{ Price.eur($total) }}", Map.of("total", 12.5)));
    }

    private static class Hidden implements TemplateHelper { public String name() { return "Hidden"; } }

    @Test
    void invalidHelpersAreRejectedWhenTheEngineIsBuilt() {
        assertThrows(IllegalArgumentException.class, () -> LoomaEngine.builder(loader).helper(new Hidden()).build());
        assertThrows(IllegalArgumentException.class, () -> LoomaEngine.builder(loader).helper(() -> "bad name").build());
    }

    @Test
    void helpersAreNotAWayAroundTheSandbox() {
        for (String expr : List.of("String.getClass()", "String.class", "String.valueOf(1).getClass()",
                "Math.class.forName('java.lang.Runtime')", "T(String).format('%s', 1)", "T(java.lang.Runtime).getRuntime()")) {
            assertThrows(LoomaException.class, () -> render("{{ " + expr + " }}", Map.of()), expr);
        }
    }
}
