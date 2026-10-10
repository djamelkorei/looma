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

    @Test
    void errorsIsAlwaysAMap() {
        assertEquals("false 0", render("{{ $errors.containsKey('email') }} {{ $errors.size() }}", Map.of()));
        assertEquals("true", render("{{ $errors.containsKey('email') }}", Map.of("errors", Map.of("email", "bad"))));
    }

    @Test
    void unlessUsesTruthinessForAnyValue() {
        Map<String, Object> m = Map.of("items", java.util.List.of(), "name", "", "full", java.util.List.of(1));
        assertEquals("none", render("@unless($items)none@endunless", m));
        assertEquals("no name", render("@unless($name)no name@endunless", m));
        assertEquals(" has items", render("@unless($full)none@else has items@endunless", m));
    }

    @Test
    void emptyDirective() {
        Map<String, Object> m = Map.of("items", java.util.List.of(), "zero", 0, "text", "x", "map", Map.of());
        assertEquals("[empty][zero][map]", render("@empty($items)[empty]@endempty@empty($zero)[zero]@endempty"
                + "@empty($text)[text]@endempty@empty($map)[map]@endempty@empty($missing)@endempty", m));
        assertEquals(" has", render("@empty($text)none@else has@endempty", m));
    }

    @Test
    void emptyStillSeparatesForelseEvenWhenNested() {
        Map<String, Object> m = Map.of("rows", java.util.List.of(java.util.List.of(), java.util.List.of(1)));
        assertEquals("(no cells)(1)", render("@forelse($rows as $r)(@empty($r)no cells@else{{ $r[0] }}@endempty)"
                + "@empty nothing@endforelse", m));
        assertEquals(" nothing", render("@forelse($rows as $r)x@empty nothing@endforelse", Map.of("rows", java.util.List.of())));
        LoomaException e = assertThrows(LoomaException.class, () -> render("@empty x", Map.of()));
        assertEquals(true, e.getMessage().contains("only belongs inside @forelse"), e.getMessage());
    }

    @Test
    void propsGiveDefaultsAndLeaveOnlyRealAttributes() {
        files.put("components.alert", "@props({'type': 'info', 'dismissible': false, 'alertTitle': null})"
                + "<div {{ $attributes->merge({'class': 'alert alert-' + $type}) }}>{{ $alertTitle ?? '' }}{{ $slot }}"
                + "@if($dismissible)[x]@endif</div>");
        assertEquals("<div class=\"alert alert-info\">Hi</div>", render("<x-alert>Hi</x-alert>", Map.of()));
        assertEquals("<div class=\"alert alert-error mb-2\" id=\"a1\">T: Oops[x]</div>",
                render("<x-alert type=\"error\" class=\"mb-2\" id=\"a1\" :dismissible=\"true\" alert-title=\"T: \">Oops</x-alert>", Map.of()));
    }

    @Test
    void propsWithNamesOnlyAndInvalidSpecs() {
        files.put("components.card", "@props({'title'})<section {{ $attributes }}>{{ $title ?? 'untitled' }}</section>");
        assertEquals("<section data-x=\"1\">Hello</section>", render("<x-card title=\"Hello\" data-x=\"1\" />", Map.of()));
        assertEquals("<section >untitled</section>", render("<x-card />", Map.of()));
        files.put("components.bad", "@props('title')x");
        assertThrows(LoomaException.class, () -> render("<x-bad />", Map.of()));
    }

    @Test
    void fragmentsRenderOnTheirOwnOrInvisiblyInPlace() {
        files.put("layout", "<html>@yield('content')@stack('js')</html>");
        files.put("rows", "<tr>{{ $r }}</tr>");
        files.put("contacts", "@extends('layout')@section('content')<h1>Contacts</h1><table>@fragment('rows')"
                + "@foreach($list as $r)@include('rows')@endforeach@push('js')<s>@endpush@endfragment</table>@endsection");
        Map<String, Object> m = Map.of("list", java.util.List.of("a", "b"));
        assertEquals("<html><h1>Contacts</h1><table><tr>a</tr><tr>b</tr></table><s></html>", engine.render("contacts", m));
        assertEquals("<tr>a</tr><tr>b</tr>", engine.renderFragment("contacts", "rows", m));
        LoomaException e = assertThrows(LoomaException.class, () -> engine.renderFragment("contacts", "nope", m));
        assertEquals(true, e.getMessage().contains("No @fragment('nope')") && "contacts".equals(e.template()), e.getMessage());
    }

    @Test
    void errorsInsideSectionsNameThePageNotTheLayout() {
        files.put("layout", "<html>\n<main>@yield('content')</main>\n</html>");
        files.put("page", "@extends('layout')\n@section('content')\nok\n{{ $user.name }}\n@endsection");
        LoomaException e = assertThrows(LoomaException.class, () -> engine.render("page", Map.of()));
        assertEquals("page", e.template(), e.getMessage());
        assertEquals(4, e.line(), e.getMessage());
        files.put("components.box", "{{ $slot }}");
        files.put("caller", "a\nb\n<x-box>{{ $missing.prop }}</x-box>");
        LoomaException slot = assertThrows(LoomaException.class, () -> engine.render("caller", Map.of()));
        assertEquals("caller", slot.template(), "slot content belongs to the caller: " + slot.getMessage());
        assertEquals(3, slot.line());
    }

    @Test
    void aClosingTagOfAnOuterBlockPointsAtTheUnclosedInnerBlock() {
        files.put("layout", "@yield('content')");
        files.put("page", "@extends('layout')\n@section('content')\n<ul>\n@foreach($xs as $x)\n<li>{{ $x }}</li>\n</ul>\n@endsection");
        LoomaException e = assertThrows(LoomaException.class, () -> engine.render("page", Map.of()));
        assertEquals(4, e.line(), e.getMessage());
        assertEquals(true, e.getMessage().contains("@foreach is never closed (found @endsection at line 7)"), e.getMessage());
        // a stray closing tag with nothing open is still reported as unexpected
        LoomaException stray = assertThrows(LoomaException.class, () -> render("x\n@endforeach", Map.of()));
        assertEquals(true, stray.getMessage().contains("Unexpected @endforeach") && stray.line() == 2, stray.getMessage());
    }
}
