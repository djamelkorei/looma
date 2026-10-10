package io.github.djamelkorei.looma.intellij.parser;

import com.intellij.lang.html.HTMLLanguage;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.djamelkorei.looma.intellij.lang.LoomaLanguage;
import io.github.djamelkorei.looma.intellij.psi.*;
import java.util.List;

public class LoomaParserTest extends BasePlatformTestCase {

    private PsiFile looma(String text) {
        return myFixture.configureByText("t.looma.html", text).getViewProvider().getPsi(LoomaLanguage.INSTANCE);
    }

    public void testBlocksAndInlineDirectives() {
        PsiFile file = looma("""
                @foreach($posts as $post) {{ $post->title }} @endforeach
                @if($x) a @elseif($y) b @else c @endif
                @section('t', 'x')
                @section('content') z @endsection
                """);
        List<LoomaBlock> blocks = List.copyOf(PsiTreeUtil.findChildrenOfType(file, LoomaBlock.class));
        assertEquals(List.of("foreach", "if", "section"), blocks.stream().map(LoomaBlock::getName).toList());
        assertEquals(List.of("if", "elseif", "else", "endif"),
                blocks.get(1).getTags().stream().map(LoomaDirective::getName).toList());
    }

    public void testNestedBlockClosedByOuterEnd() {
        PsiFile file = looma("@foreach($a as $b) @if($b) x @endforeach");
        LoomaBlock outer = PsiTreeUtil.findChildOfType(file, LoomaBlock.class);
        assertEquals("foreach", outer.getName());
        assertEquals("endforeach", outer.getTags().getLast().getName());
        LoomaBlock inner = PsiTreeUtil.findChildOfType(outer, LoomaBlock.class);
        assertEquals(List.of("if"), inner.getTags().stream().map(LoomaDirective::getName).toList());
    }

    public void testVariables() {
        PsiFile file = looma("@foreach($posts as $post) {{ $post->title }} {{ items.size() }} {{ {key: v} }} @endforeach"
                + "@for($i = 0; $i < 3; $i++) @endfor");
        assertEquals(List.of("post", "i"), PsiTreeUtil.findChildrenOfType(file, LoomaVariableDeclaration.class)
                .stream().map(LoomaVariableDeclaration::getName).toList());
        assertEquals(List.of("posts", "post", "items", "v", "i", "i"),
                PsiTreeUtil.findChildrenOfType(file, LoomaVariable.class).stream().map(LoomaVariable::getName).toList());
        assertEquals(List.of("title", "size"),
                PsiTreeUtil.findChildrenOfType(file, LoomaMember.class).stream().map(LoomaMember::getName).toList());
    }

    public void testHtmlTreeSeesLoomaAsFragments() {
        PsiFile file = myFixture.configureByText("t.looma.html",
                "<ul class=\"{{ $c }}\">@foreach($a as $b)<li @class('x')>{{ $b }}</li>@endforeach</ul>");
        PsiFile html = file.getViewProvider().getPsi(HTMLLanguage.INSTANCE);
        List<String> tags = PsiTreeUtil.findChildrenOfType(html, XmlTag.class).stream().map(XmlTag::getName).toList();
        assertEquals(List.of("ul", "li"), tags);
    }
}
