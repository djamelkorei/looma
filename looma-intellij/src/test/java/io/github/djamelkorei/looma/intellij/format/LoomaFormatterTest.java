package io.github.djamelkorei.looma.intellij.format;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

public class LoomaFormatterTest extends BasePlatformTestCase {

    private void check(String before, String after) {
        myFixture.configureByText("t.looma.html", before);
        WriteCommandAction.runWriteCommandAction(getProject(), (Runnable)
                () -> CodeStyleManager.getInstance(getProject()).reformat(myFixture.getFile()));
        myFixture.checkResult(after);
    }

    /** On the repository's templates, formatting only changes whitespace, and a second pass changes nothing. */
    public void testRealTemplatesOnlyChangeWhitespaceAndAreStable() throws java.io.IOException {
        java.util.List<java.nio.file.Path> files;
        try (var walk = java.nio.file.Files.walk(java.nio.file.Path.of("../looma-demo/src/main/resources/templates"))) {
            files = walk.filter(p -> p.toString().endsWith(".looma.html")).toList();
        }
        assertFalse(files.isEmpty());
        for (java.nio.file.Path path : files) {
            String original = java.nio.file.Files.readString(path);
            myFixture.configureByText("t" + Math.abs(path.hashCode()) + ".looma.html", original);
            String once = reformat();
            assertEquals(path.toString(), original.replaceAll("\\s+", ""), once.replaceAll("\\s+", ""));
            assertEquals(path + " is not stable", once, reformat());
        }
    }

    private String reformat() {
        WriteCommandAction.runWriteCommandAction(getProject(), (Runnable)
                () -> CodeStyleManager.getInstance(getProject()).reformat(myFixture.getFile()));
        return myFixture.getEditor().getDocument().getText();
    }

    public void testBlockBodiesAreIndented() {
        check("""
                @if($a)
                <p>yes</p>
                @else
                <p>no</p>
                @endif
                """, """
                @if($a)
                    <p>yes</p>
                @else
                    <p>no</p>
                @endif
                """);
    }

    public void testLoopInsideHtml() {
        check("""
                <ul>
                @foreach($posts as $post)
                <li>{{ $post->title }}</li>
                @endforeach
                </ul>
                """, """
                <ul>
                    @foreach($posts as $post)
                        <li>{{ $post->title }}</li>
                    @endforeach
                </ul>
                """);
    }

    public void testNestedBlocks() {
        check("""
                @section('content')
                <div>
                @foreach($xs as $x)
                @if($x)
                {{ $x }}
                @endif
                @endforeach
                </div>
                @endsection
                """, """
                @section('content')
                    <div>
                        @foreach($xs as $x)
                            @if($x)
                                {{ $x }}
                            @endif
                        @endforeach
                    </div>
                @endsection
                """);
    }

    public void testExpressionsAreLeftAlone() {
        check("""
                <p class="{{ $a  ?? 'b' }}" @class('x',{'y':$z})>{!!   $html !!}</p>
                """, """
                <p class="{{ $a  ?? 'b' }}" @class('x',{'y':$z})>{!!   $html !!}</p>
                """);
    }

    public void testEnterInBlockIndents() {
        myFixture.configureByText("t.looma.html", "@if($a)<caret>\n@endif\n");
        myFixture.type("\n");
        myFixture.checkResult("@if($a)\n    <caret>\n@endif\n");
    }

    public void testEnterBetweenTagsSplitsThem() {
        myFixture.configureByText("t.looma.html", "<div>\n    @foreach($a as $b)<caret>@endforeach\n</div>");
        myFixture.type("\n");
        myFixture.checkResult("<div>\n    @foreach($a as $b)\n        <caret>\n    @endforeach\n</div>");
    }

    public void testEnterAfterEndTagDoesNotIndent() {
        myFixture.configureByText("t.looma.html", "@if($a)\n    x\n@endif<caret>");
        myFixture.type("\n");
        myFixture.checkResult("@if($a)\n    x\n@endif\n<caret>");
    }
}
