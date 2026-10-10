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

    @Test
    void reflectionEscapeHatchesAreBlocked() {
        Map<String, Object> m = Map.of("user", new User(), "n", 1);
        for (String expr : List.of("$user.class", "$user->getClass()", "'x'.class.name",
                "$n.class.forName('java.lang.Runtime')", "$user.class.classLoader")) {
            assertThrows(LoomaException.class, () -> render("{{ " + expr + " }}", m), expr);
        }
        // ordinary property and method access still works
        assertEquals("3", render("{{ 'abc'.length() }}", m));
        assertEquals("ABC", render("{{ 'abc'.toUpperCase() }}", m));
    }

    @Test
    void closingBracesInsideStringLiterals() {
        assertEquals("}} and !!}", render("{{ '}}' }} and {!! '!!}' !!}", Map.of()));
    }

    @Test
    void nestedLoopsExposeParentLoop() {
        String t = "@foreach($rows as $r)@foreach($r as $c){{ $loop->parent->iteration }}.{{ $loop->iteration }}"
                + "@if($loop->depth == 2)d@endif @endforeach@endforeach";
        assertEquals("1.1d 1.2d 2.1d ", render(t, Map.of("rows", List.of(List.of("a", "b"), List.of("c")))));
    }

    @Test
    void iteratesArraysAndNullsGracefully() {
        assertEquals("123", render("@foreach($a as $x){{ $x }}@endforeach", Map.of("a", new int[] {1, 2, 3})));
        assertEquals(" none", render("@forelse($missing as $x)x@empty none@endforelse", Map.of()));
    }

    @Test
    void crlfLineNumbersAndUnicode() {
        files.put("crlf", "a\r\nb\r\n{{ 1 + }}");
        assertEquals(3, assertThrows(LoomaException.class, () -> engine.render("crlf", Map.of())).line());
        assertEquals("héllo ✓ 日本", render("héllo {{ $s }} 日本", Map.of("s", "✓")));
    }

    @Test
    void emptyAndPlainTemplates() {
        assertEquals("", render("", Map.of()));
        assertEquals("<p>just html & text</p>", render("<p>just html & text</p>", Map.of()));
        assertEquals("a@", render("a@", Map.of()));
        assertEquals("@unknown(x)", render("@unknown(x)", Map.of()));
    }

    @Test
    void missingIncludeNamesTheTemplate() {
        files.put("page", "@include('ghost')");
        LoomaException e = assertThrows(LoomaException.class, () -> engine.render("page", Map.of()));
        assertTrue(e.getMessage().contains("ghost"), e.getMessage());
    }

    @Test
    void langUsesMessageResolverAndFallsBackToKey() {
        MessageResolver r = (key, args) -> key.equals("hi") ? "Hello <" + args[0] + ">" : key;
        assertEquals("Hello &lt;Ann&gt;", render("@lang('hi', $n)", Map.of("_messages", r, "n", "Ann")));
        assertEquals("unknown.key", render("@lang('unknown.key')", Map.of("_messages", r)));
        assertEquals("no.resolver", render("@lang('no.resolver')", Map.of()));
    }

    @Test
    void forLoopCountsUpDownAndByStep() {
        assertEquals("012", render("@for($i = 0; $i < 3; $i++){{ $i }}@endfor", Map.of()));
        assertEquals("321", render("@for($i = 3; $i > 0; $i--){{ $i }}@endfor", Map.of()));
        assertEquals("0,5,10,", render("@for($i = 0; $i <= 10; $i += 5){{ $i }},@endfor", Map.of()));
        assertEquals("4,2,0,", render("@for($i = 4; $i >= 0; $i -= 2){{ $i }},@endfor", Map.of()));
        assertEquals("0.0 0.5 1.0 ", render("@for($x = 0.0; $x <= 1; $x += 0.5){{ $x }} @endfor", Map.of()));
        assertEquals("ab", render("@for($i = 0; $i < $items.size(); $i++){{ $items[$i] }}@endfor",
                Map.of("items", List.of("a", "b"))));
        assertEquals("", render("@for($i = 5; $i < 3; $i++)never@endfor", Map.of()));
    }

    @Test
    void forLoopVariableDoesNotLeakOutAndLoopsNest() {
        assertEquals("00 01 10 11 [none]",
                render("@for($i = 0; $i < 2; $i++)@for($j = 0; $j < 2; $j++){{ $i }}{{ $j }} @endfor@endfor[{{ $i ?? 'none' }}]", Map.of()));
    }

    @Test
    void whileLoopsOverAnIteratorStyleCondition() {
        Map<String, Object> m = Map.of("it", List.of("x", "y", "z").iterator());
        assertEquals("x-y-z-", render("@while($it.hasNext()){{ $it.next() }}-@endwhile", m));
    }

    @Test
    void breakAndContinueWork() {
        List<Integer> nums = List.of(1, 2, 3, 4, 5, 6);
        Map<String, Object> m = Map.of("nums", nums);
        assertEquals("123", render("@foreach($nums as $n)@if($n > 3)@break@endif{{ $n }}@endforeach", m));
        assertEquals("135", render("@foreach($nums as $n)@if($n % 2 == 0)@continue@endif{{ $n }}@endforeach", m));
        assertEquals("1234", render("@foreach($nums as $n){{ $n }}@break($n == 4)@endforeach", m));
        assertEquals("135", render("@foreach($nums as $n)@continue($n % 2 == 0){{ $n }}@endforeach", m));
        assertEquals("0 1 2 ", render("@for($i = 0; $i < 10; $i++)@break($i == 3){{ $i }} @endfor", Map.of()));
        assertEquals("a", render("@forelse($nums as $n)a@break@empty none@endforelse", m));
    }

    @Test
    void breakOnlyLeavesTheInnermostLoop() {
        String t = "@foreach($rows as $r)@foreach($r as $c)@break($c == 'stop'){{ $c }}@endforeach|@endforeach";
        assertEquals("a|c|", render(t, Map.of("rows", List.of(List.of("a", "stop", "b"), List.of("c")))));
    }

    @Test
    void breakInsideSwitchEndsTheSwitchNotTheLoop() {
        String t = "@foreach($xs as $x)@switch($x)@case(1)one@break@case(2)two@break@default other@endswitch,@endforeach";
        assertEquals("one,two, other,", render(t, Map.of("xs", List.of(1, 2, 3))));
        // @continue inside a switch still reaches the loop
        String c = "@foreach($xs as $x)@switch($x)@case(2)@continue@endswitch{{ $x }}@endforeach";
        assertEquals("13", render(c, Map.of("xs", List.of(1, 2, 3))));
    }

    @Test
    void breakAndContinueOutsideLoopsAreSyntaxErrors() {
        assertThrows(LoomaException.class, () -> render("@break", Map.of()));
        assertThrows(LoomaException.class, () -> render("@continue", Map.of()));
        assertThrows(LoomaException.class, () -> render("@switch(1)@case(1)@continue@endswitch", Map.of()));
        assertEquals("@break (draft)", render("@verbatim@break (draft)@endverbatim", Map.of()));
    }

    @Test
    void malformedForAndRunawayLoopsFailClearly() {
        for (String bad : List.of("@for($i = 0; $i < 3)x@endfor", "@for($i < 3; $i < 3; $i++)x@endfor",
                "@for($i = 0; $i < 3; $j++)x@endfor", "@for($i = 0; $i < 3; $i += )x@endfor")) {
            assertThrows(LoomaException.class, () -> render(bad, Map.of()), bad);
        }
        LoomaException e = assertThrows(LoomaException.class,
                () -> render("@while(true)x@endwhile", Map.of()));
        assertTrue(e.getMessage().contains("100000 iterations"), e.getMessage());
        assertThrows(LoomaException.class, () -> render("@for($i = 0; $i < 200000; $i++)@endfor", Map.of()));
        assertThrows(LoomaException.class, () -> render("@for($i = 'a'; $i != 'b'; $i++)x@endfor", Map.of()));
    }

    @Test
    void checkReportsEverySyntaxErrorWithoutRendering() {
        files.put("good", "@if($x){{ $y }}@endif");
        files.put("bad1", "line\n@foreach($a as $b)never closed");
        files.put("bad2", "{{ 1 + }}");
        List<LoomaException> errors = engine.check(List.of("good", "bad1", "bad2", "missing"));
        assertEquals(3, errors.size());
        assertEquals(List.of("bad1", "bad2", "missing"), errors.stream().map(LoomaException::template).toList());
        assertTrue(engine.check(List.of("good")).isEmpty());
    }

    @Test
    void unclosedBlocksPointAtTheOpeningTag() {
        LoomaException e = assertThrows(LoomaException.class,
                () -> render("ok\nok\n@if($x)\nbody\nbody\nbody\nbody", Map.of()));
        assertEquals(3, e.line());
        assertTrue(e.getMessage().contains("@if is never closed"), e.getMessage());
        files.put("c", "a\n<x-card>\nbody");
        LoomaException c = assertThrows(LoomaException.class, () -> engine.render("c", Map.of()));
        assertEquals(2, c.line());
        assertTrue(c.getMessage().contains("</x-card>"), c.getMessage());
    }

    @Test
    void checkFindsMissingIncludesLayoutsAndComponents() {
        files.put("page", "ok\n@include('partials.gone')\n@include($dynamic)\n<x-ghost />\n@include('page')");
        files.put("child", "@extends('no.layout')");
        files.put("components.real", "x");
        files.put("uses", "<x-real />");
        List<LoomaException> errors = engine.check(List.of("page", "child", "uses"));
        assertEquals(3, errors.size(), errors.toString());
        assertTrue(errors.get(0).getMessage().contains("In template 'page' at line 2: References missing template 'partials.gone'"),
                errors.get(0).getMessage());
        assertTrue(errors.get(1).getMessage().contains("'components.ghost'") && errors.get(1).line() == 4);
        assertTrue(errors.get(2).getMessage().contains("In template 'child'") && errors.get(2).getMessage().contains("'no.layout'"));
    }
}
