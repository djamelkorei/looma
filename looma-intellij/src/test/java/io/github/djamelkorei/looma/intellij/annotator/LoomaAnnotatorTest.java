package io.github.djamelkorei.looma.intellij.annotator;

import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class LoomaAnnotatorTest extends BasePlatformTestCase {

    private void check(String text) {
        myFixture.configureByText("t.looma.html", text);
        myFixture.checkHighlighting(false, false, false);
    }

    public void testValidTemplateHasNoErrors() {
        check("""
                @if($a) x @elseif($b) y @else z @endif
                @foreach($xs as $x) @if($loop->first) @continue @endif @break($x > 3) @endforeach
                @switch($v) @case(1) one @break @default other @endswitch
                @section('title', 'Home')
                @section('content') x @show
                @auth('ADMIN') a @else b @endauth
                @verbatim @if @endverbatim
                @can('posts:edit') e @else n @endcan @cannot('edit', $post) x @endcannot @canany('a', 'b') y @endcanany
                """);
    }

    public void testUnclosedBlock() {
        check("<error descr=\"Unclosed block: @foreach is never closed, expected @endforeach\">@foreach</error>($xs as $x) x");
    }

    public void testInnerBlockUnclosed() {
        check("@foreach($xs as $x) <error descr=\"Unclosed block: @if is never closed, expected @endif\">@if</error>($x) @endforeach");
    }

    public void testStrayTags() {
        check("<error descr=\"Unexpected @endif\">@endif</error> @foreach($a as $b) <error descr=\"Unexpected @else\">@else</error> @endforeach");
    }

    public void testBreakOutsideLoop() {
        check("<error descr=\"@continue can only be used inside a loop\">@continue</error>"
                + " <error descr=\"@break can only be used inside a loop or @switch\">@break</error>");
    }

    public void testUnclosedEcho() {
        check("<error descr=\"Unclosed tag, expected '}}'\">{{</error> $a\n");
    }

    public void testSectionNeedsLiteral() {
        check("@yield<error descr=\"@yield needs a quoted name, e.g. @yield('content')\">($name)</error>");
    }

    public void testMissingTemplate() {
        check("@include('<error descr=\"Template 'nope' not found\">nope</error>')");
    }

    public void testConditionalIncludes() {
        myFixture.addFileToProject("templates/partials/row.looma.html", "{{ $order }}");
        var file = myFixture.addFileToProject("templates/page.looma.html", """
                @includeIf('partials.maybe')
                @includeFirst({'themes.dark.nav', 'partials.row'})
                @includeWhen($a, 'partials.row', {'x': 1})
                @includeUnless($a, '<error descr="Template 'partials.nope' not found">partials.nope</error>')
                @each('partials.row', $orders, 'order', '<error descr="Template 'partials.none' not found">partials.none</error>')
                <input type="checkbox" @checked($on) @disabled(!$on)>
                """);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        myFixture.checkHighlighting(false, false, false);
    }
}
