package io.github.djamelkorei.looma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Abuse cases for the expression sandbox and output escaping. */
class SecurityTest {

    private final Map<String, String> files = new HashMap<>();
    private final LoomaEngine engine = new LoomaEngine(new TemplateLoader() {
        public boolean exists(String name) { return files.containsKey(name); }
        public String load(String name) { return files.get(name); }
    }, true);

    private String render(String template, Map<String, Object> model) {
        files.put("t", template);
        return engine.render("t", model);
    }

    /** An innocent bean that happens to have the same property names as java.lang.Class. */
    public static class Innocent {
        public String getName() { return "innocent"; }
        public String getClassLoader() { return "not a class loader"; }
    }

    @Test
    void cachedPropertyAccessCannotBeUsedToReachBlockedTypes() {
        // SpEL caches the accessor chosen for the first item and reuses it for later items without asking
        // canRead() again; the sandbox must still refuse the second item, which is a java.lang.Class.
        String t = "@foreach($items as $i)[{{ $i.classLoader }}]@endforeach";
        LoomaException e = assertThrows(LoomaException.class,
                () -> render(t, Map.of("items", List.of(new Innocent(), String.class))));
        assertTrue(e.getMessage().contains("classLoader"), e.getMessage());
    }

    @Test
    void cachedPropertyAccessOnBlockedInstancesIsRefused() {
        // Same idea with an instance of a blocked type (a Thread), which SpEL does not treat as static access.
        String t = "@foreach($items as $i)[{{ $i.name }}]@endforeach";
        LoomaException e = assertThrows(LoomaException.class,
                () -> render(t, Map.of("items", List.of(new Innocent(), Thread.currentThread()))));
        assertTrue(e.getMessage().contains("name"), e.getMessage());
    }

    @Test
    void templatesCannotMutateTheirOwnScope() {
        assertThrows(LoomaException.class, () -> render("{{ #root.put('x', 1) }}", Map.of()));
        assertThrows(LoomaException.class, () -> render("{{ #this.putAll({'x': 1}) }}", Map.of()));
    }

    @Test
    void outputIsEscapedInTextAndQuotedAttributes() {
        String evil = "\"><script>alert('x')</script>&";
        String html = render("<a title=\"{{ $v }}\">{{ $v }}</a>", Map.of("v", evil));
        assertFalse(html.contains("<script>"), html);
        assertEquals("<a title=\"&quot;&gt;&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;&amp;\">"
                + "&quot;&gt;&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;&amp;</a>", html);
    }

    @Test
    void jsonCannotBreakOutOfAScriptTag() {
        String html = render("<script>var d = @json($d);</script>", Map.of("d", Map.of("k", "</script><script>alert(1)//")));
        assertEquals(1, html.split("</script>", -1).length - 1, html);
    }

    @Test
    void templateNamesCannotEscapeTheTemplateFolder() {
        for (String name : List.of("../secret", "/etc/passwd", "a/../../b", "..", "a\\..\\b", "a%2e%2e/b", "")) {
            assertFalse(new ClasspathTemplateLoader().exists(name), name);
        }
    }

    @Test
    void dataIsNeverEvaluatedAsAnExpression() {
        // A value that looks like an expression or directive must come out as text.
        assertEquals("{{ T(java.lang.Runtime) }} @if(true)", render("{!! $v !!}", Map.of("v", "{{ T(java.lang.Runtime) }} @if(true)")));
    }

    @Test
    void absurdlyDeepNestingFailsCleanly() {
        String deep = "@if(true)".repeat(5000) + "x" + "@endif".repeat(5000);
        LoomaException e = assertThrows(LoomaException.class, () -> render(deep, Map.of()));
        assertTrue(e.getMessage().contains("nested more than 200"), e.getMessage());
        // reasonable nesting is fine
        assertEquals("x", render("@if(true)".repeat(150) + "x" + "@endif".repeat(150), Map.of()));
    }

    @Test
    void strayComponentLikeTextStaysFast() {
        String junk = "<x-a ".repeat(40_000); // never closed: must be plain text, without quadratic scanning
        long start = System.nanoTime();
        assertEquals(junk, render(junk, Map.of()));
        assertTrue(System.nanoTime() - start < 5_000_000_000L, "lexing took too long");
    }
}
