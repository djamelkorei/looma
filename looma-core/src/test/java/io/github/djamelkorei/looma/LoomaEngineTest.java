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
}
