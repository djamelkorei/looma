package io.github.djamelkorei.looma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Boolean attributes, conditional includes, form binding and permission checks. */
class FormFeaturesTest {

    final Map<String, String> files = new HashMap<>();
    final TemplateLoader loader = new TemplateLoader() {
        public boolean exists(String name) { return files.containsKey(name); }
        public String load(String name) {
            String s = files.get(name);
            if (s == null) throw new LoomaException("Template not found: " + name);
            return s;
        }
    };
    LoomaEngine engine = new LoomaEngine(loader, false);

    String render(String template, Map<String, Object> model) {
        files.put("t", template);
        return engine.render("t", model);
    }

    @Test
    void booleanAttributesAppearOnlyWhenTruthy() {
        Map<String, Object> m = Map.of("on", true, "off", false, "lang", "fr");
        assertEquals("<input checked> <input >", render("<input @checked($on)> <input @checked($off)>", m));
        assertEquals("<option selected>", render("<option @selected($lang == 'fr')>", m));
        assertEquals("<button disabled readonly required>", render("<button @disabled($on) @readonly(1) @required('x')>", m));
        assertEquals("<button >", render("<button @disabled($missing)>", m));
    }

    @Test
    void booleanAttributesNeedACondition() {
        assertThrows(LoomaException.class, () -> render("<input @checked()>", Map.of()));
        // the same guard covers every directive, e.g. an empty @if()
        LoomaException e = assertThrows(LoomaException.class, () -> render("a\n@if()x@endif", Map.of()));
        assertEquals(2, e.line());
    }
}
