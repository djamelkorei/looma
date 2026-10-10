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

    @Test
    void conditionalIncludes() {
        files.put("promo", "[promo {{ $x ?? '' }}]");
        files.put("admin", "[admin]");
        files.put("nav", "[nav]");
        assertEquals("a[promo 1]b", render("a@includeIf('promo', {'x': 1})b", Map.of()));
        assertEquals("ab", render("a@includeIf('nope')b", Map.of()));
        assertEquals("[admin]", render("@includeWhen($isAdmin, 'admin')", Map.of("isAdmin", true)));
        assertEquals("", render("@includeWhen($isAdmin, 'admin')", Map.of("isAdmin", false)));
        assertEquals("[admin]", render("@includeUnless($isAdmin, 'admin')", Map.of("isAdmin", false)));
        assertEquals("[nav]", render("@includeFirst({'themes.dark.nav', 'nav'})", Map.of()));
        LoomaException e = assertThrows(LoomaException.class, () -> render("@includeFirst({'x', 'y'})", Map.of()));
        assertEquals(true, e.getMessage().contains("[x, y]"), e.getMessage());
    }

    @Test
    void eachRendersAViewPerItemOrTheEmptyView() {
        files.put("row", "<li>{{ $loop->iteration }}:{{ $order }}</li>");
        files.put("none", "<li>none</li>");
        assertEquals("<li>1:a</li><li>2:b</li>", render("@each('row', $orders, 'order', 'none')", Map.of("orders", java.util.List.of("a", "b"))));
        assertEquals("<li>none</li>", render("@each('row', $orders, 'order', 'none')", Map.of("orders", java.util.List.of())));
        assertEquals("", render("@each('row', $orders, 'order')", Map.of("orders", java.util.List.of())));
    }

    @Test
    void startupCheckKnowsWhichIncludesMustExist() {
        files.put("page", "@includeIf('optional')@includeWhen(true, 'required.one')@each('required.two', $x, 'i')");
        java.util.List<String> missing = engine.check(java.util.List.of("page")).stream()
                .map(LoomaException::getMessage).toList();
        assertEquals(2, missing.size(), missing.toString());
        assertEquals(true, missing.toString().contains("required.one") && missing.toString().contains("required.two")
                && !missing.toString().contains("optional"), missing.toString());
    }

    record Post(String owner) {}

    /** Authorities "posts:edit" and "reports:view"; may edit a post only if it owns it. */
    static final io.github.djamelkorei.looma.spi.Authorizer ADA = new io.github.djamelkorei.looma.spi.Authorizer() {
        public boolean can(String authority) { return java.util.Set.of("posts:edit", "reports:view").contains(authority); }
        public boolean can(String permission, Object target) {
            return permission.equals("edit") && target instanceof Post p && p.owner().equals("ada");
        }
    };

    @Test
    void permissionChecks() {
        Map<String, Object> m = Map.of("_authorizer", ADA, "mine", new Post("ada"), "theirs", new Post("bob"));
        assertEquals("edit", render("@can('posts:edit')edit@endcan", m));
        assertEquals(" no", render("@can('posts:delete')delete@else no@endcan", m));
        assertEquals("mine", render("@can('edit', $mine)mine@endcan@can('edit', $theirs)theirs@endcan", m));
        assertEquals("read-only", render("@cannot('edit', $theirs)read-only@endcannot", m));
        assertEquals("any", render("@canany('posts:delete', 'reports:view')any@endcanany", m));
        assertEquals("", render("@canany('a', 'b')any@endcanany", m));
    }

    @Test
    void permissionChecksFailClosedWithoutAnAuthorizer() {
        assertEquals(" denied", render("@can('anything')yes@else denied@endcan", Map.of()));
        assertEquals("hidden?", render("@cannot('anything')hidden?@endcannot", Map.of()));
        assertThrows(LoomaException.class, () -> render("@can('a', $b, $c)x@endcan", Map.of()));
    }
}
