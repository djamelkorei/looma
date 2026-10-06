package io.github.djamelkorei.looma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LoomaEngineTest {

    private final Map<String, String> files = new HashMap<>();
    private final LoomaEngine engine = new LoomaEngine(new TemplateLoader() {
        @Override public boolean exists(String name) { return files.containsKey(name); }
        @Override public String load(String name) {
            String s = files.get(name);
            if (s == null) throw new LoomaException("Template not found: " + name);
            return s;
        }
    }, false);

    public static class User {
        public String getName() { return "<Ann>"; }
        public boolean isAdmin() { return true; }
    }

    private String render(String template, Map<String, Object> model) {
        files.put("t", template);
        return engine.render("t", model);
    }

    @Test
    void echoEscapesAndRawDoesNot() {
        assertEquals("&lt;b&gt; / <b>", render("{{ x }} / {!! x !!}", Map.of("x", "<b>")));
    }

    @Test
    void commentsAndEscapes() {
        assertEquals("mail a@b.com, @home {{ x }}", render("mail a@b.com, @@home {{-- hidden --}}@{{ x }}", Map.of()));
    }

    @Test
    void bladeStyleExpressions() {
        Map<String, Object> m = Map.of("user", new User());
        assertEquals("&lt;Ann&gt;", render("{{ $user->name }}", m));
        assertEquals("guest", render("{{ $missing ?? 'guest' }}", m));
        assertEquals("yes", render("@if($user->admin && true)yes@endif", m));
    }

    @Test
    void conditionals() {
        String t = "@if($n == 1)one@elseif($n == 2)two@else many@endif";
        assertEquals("one", render(t, Map.of("n", 1)));
        assertEquals("two", render(t, Map.of("n", 2)));
        assertEquals(" many", render(t, Map.of("n", 3)));
        assertEquals("shown", render("@unless($hide)shown@endunless", Map.of("hide", false)));
        assertEquals(" other", render("@unless($hide)shown@else other@endunless", Map.of("hide", true)));
    }

    @Test
    void foreachWithLoopVariable() {
        String t = "@foreach($items as $i)@if($loop->first)[@endif{{ $i }}@if(!$loop->last),@endif@endforeach]";
        assertEquals("[a,b,c]", render(t, Map.of("items", List.of("a", "b", "c"))));
    }

    @Test
    void forelseAndMaps() {
        String t = "@forelse($items as $i){{ $i }}@empty none@endforelse";
        assertEquals("x", render(t, Map.of("items", List.of("x"))));
        assertEquals(" none", render(t, Map.of("items", List.of())));
        assertEquals("a=1;", render("@foreach($m as $e){{ $e->key }}={{ $e->value }};@endforeach", Map.of("m", Map.of("a", 1))));
    }

    @Test
    void layoutsSectionsAndIncludes() {
        files.put("layout", "<title>@yield('title', 'Default')</title><main>@yield('content')</main>");
        files.put("page", "@extends('layout')@section('title', 'Home')@section('content')Hi {{ $who }} @include('part', {'n': 2})@endsection");
        files.put("part", "[part {{ $n }} {{ $who }}]");
        assertEquals("<title>Home</title><main>Hi bob [part 2 bob]</main>", engine.render("page", Map.of("who", "bob")));
    }

    @Test
    void syntaxErrorsReportLine() {
        LoomaException e = assertThrows(LoomaException.class, () -> render("a\nb\n@if($x)never closed", Map.of()));
        assertTrue(e.getMessage().contains("Unclosed block"), e.getMessage());
        assertThrows(LoomaException.class, () -> engine.render("nope", Map.of()));
    }

    @Test
    void templatesCannotReachStaticsOrConstructors() {
        assertThrows(LoomaException.class, () -> render("{{ T(java.lang.System).getenv() }}", Map.of()));
        assertThrows(LoomaException.class, () -> render("{{ new java.io.File('x') }}", Map.of()));
    }

    @Test
    void issetAuthGuestAndVerbatim() {
        assertEquals("set", render("@isset($a)set@endisset", Map.of("a", 1)));
        assertEquals("", render("@isset($a)set@endisset", Map.of()));
        assertEquals(" in", render("@auth in@else out@endauth", Map.of("principal", "bob")));
        assertEquals(" out", render("@auth in@else out@endauth", Map.of()));
        assertEquals(" g", render("@guest g@endguest", Map.of()));
        assertEquals("@if({{ x }})", render("@verbatim@if({{ x }})@endverbatim", Map.of("x", 1)));
        assertEquals("a@empty.com", render("a@empty.com", Map.of()));
    }

    @Test
    void switchCases() {
        String t = "@switch($n)@case(1)one@break@case(2)@case(3)two-or-three@break@default other@endswitch";
        assertEquals("one", render(t, Map.of("n", 1)));
        assertEquals("two-or-three", render(t, Map.of("n", 2)));
        assertEquals("two-or-three", render(t, Map.of("n", 3)));
        assertEquals(" other", render(t, Map.of("n", 9)));
    }

    @Test
    void stacksAreFilledEvenWhenDeclaredBeforePushes() {
        files.put("layout", "<head>@stack('js')</head>@yield('content')");
        files.put("page", "@extends('layout')@section('content')body@push('js')<a>@endpush@push('js')<b>@endpush"
                + "@prepend('js')<0>@endprepend@endsection");
        assertEquals("<head><0><a><b></head>body", engine.render("page", Map.of()));
        files.put("empty", "[@stack('none')]");
        assertEquals("[]", engine.render("empty", Map.of()));
    }

    @Test
    void parentAppendsToLayoutSection() {
        files.put("base", "@section('side')base@endsection");
        files.put("mid", "@extends('base')@section('side')mid+@parent@endsection");
        files.put("leaf", "@extends('mid')@section('side')leaf+@parent@endsection");
        assertEquals("leaf+mid+base", engine.render("leaf", Map.of()));
    }

    @Test
    void componentsWithAttributesAndDefaultSlot() {
        files.put("components.alert", "<div {{ $attributes->except('type')->merge({'class': 'alert'}) }}>{{ $type }}: {{ $slot }}</div>");
        assertEquals("<div class=\"alert mb\" id=\"x\">err: hi &lt;b&gt; Ann</div>",
                render("<x-alert type=\"err\" class=\"mb\" id=\"x\">hi {{ '<b>' }} {{ $n }}</x-alert>", Map.of("n", "Ann")));
    }

    @Test
    void componentsWithBoundPropsNamedSlotsAndNesting() {
        files.put("components.card", "<section><h1>{{ $title }}</h1>{{ $slot }}<footer>{{ $footer }}</footer></section>");
        files.put("components.ui.badge", "<b>{{ $count }}</b>");
        String t = "<x-card><x-slot:title>T</x-slot:title>body <x-ui.badge :count=\"items.size()\" /> "
                + "<x-slot:footer>F {{ $n }}</x-slot:footer></x-card>";
        assertEquals("<section><h1>T</h1>body <b>2</b> <footer>F Ann</footer></section>",
                render(t, Map.of("items", List.of(1, 2), "n", "Ann")));
    }

    @Test
    void componentScopeIsIsolated() {
        files.put("components.peek", "[{{ $secret ?? 'none' }}]");
        assertEquals("[none]", render("<x-peek />", Map.of("secret", "s")));
        assertThrows(LoomaException.class, () -> render("<x-peek>text</x-other>", Map.of()));
    }

    @Test
    void csrfAndMethodSpoofing() {
        assertEquals("<input type=\"hidden\" name=\"_token\" value=\"a&quot;b\">",
                render("@csrf", Map.of("_csrf", new CsrfToken("_token", "a\"b"))));
        assertEquals("", render("@csrf", Map.of()));
        assertEquals("<input type=\"hidden\" name=\"_method\" value=\"PUT\">", render("@method('PUT')", Map.of()));
    }

    @Test
    void runtimeErrorsNameTemplateAndLine() {
        files.put("inner", "line1\nline2 {{ $x.nope.deeper }}");
        files.put("outer", "@include('inner')");
        LoomaException e = assertThrows(LoomaException.class, () -> engine.render("outer", Map.of("x", "s")));
        assertEquals("inner", e.template());
        assertEquals(2, e.line());
        assertTrue(e.getMessage().contains("In template 'inner' at line 2"), e.getMessage());
    }

    @Test
    void parseErrorsNameTemplateAndLine() {
        files.put("bad", "ok\n@if($x)\n{{ 1 + }}\n@endif");
        LoomaException e = assertThrows(LoomaException.class, () -> engine.render("bad", Map.of()));
        assertEquals(3, e.line());
        assertTrue(e.getMessage().startsWith("In template 'bad' at line 3"), e.getMessage());
    }

    @Test
    void runawayRecursionIsStopped() {
        files.put("loop", "x@include('loop')");
        LoomaException e = assertThrows(LoomaException.class, () -> engine.render("loop", Map.of()));
        assertTrue(e.getMessage().contains("Maximum nesting depth"), e.getMessage());
    }
}
