package io.github.djamelkorei.looma.intellij.editor;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.ide.util.treeView.smartTree.TreeElement;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.lang.documentation.DocumentationProvider;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.util.Arrays;
import java.util.List;

public class LoomaEditorTest extends BasePlatformTestCase {

    public void testDirectiveCompletionInHtml() {
        myFixture.configureByText("t.looma.html", "<div>@fore<caret></div>");
        myFixture.completeBasic();
        List<String> items = myFixture.getLookupElementStrings();
        assertNotNull(items);
        assertEquals(List.of("foreach", "forelse").stream().sorted().toList(), items.stream().sorted().toList());
    }

    public void testCompletingBlockDirectiveAddsEndTag() {
        myFixture.configureByText("t.looma.html", "  @unl<caret>");
        var items = myFixture.completeBasic(); // also offers includeUnless
        myFixture.getLookup().setCurrentItem(java.util.Arrays.stream(items)
                .filter(i -> i.getLookupString().equals("unless")).findFirst().orElseThrow());
        myFixture.finishLookup(com.intellij.codeInsight.lookup.Lookup.NORMAL_SELECT_CHAR);
        myFixture.checkResult("  @unless(<caret>)\n  @endunless");
    }

    public void testCompletingNoArgumentBlockAddsBody() {
        myFixture.configureByText("t.looma.html", "@onc<caret>");
        myFixture.completeBasic();
        myFixture.checkResult("@once\n    <caret>\n@endonce");
    }

    public void testEndTagOfOpenBlockOffered() {
        myFixture.configureByText("t.looma.html", "@foreach($a as $b) x @end<caret>");
        myFixture.completeBasic();
        List<String> items = myFixture.getLookupElementStrings();
        assertNotNull(items);
        assertEquals("endforeach", items.getFirst());
    }

    public void testNoCompletionInEmail() {
        myFixture.configureByText("t.looma.html", "mail me@fo<caret>");
        assertEmpty(myFixture.completeBasic());
    }

    public void testNoDirectivesForBeansInExpressions() {
        myFixture.configureByText("t.looma.html", "{{ @fo<caret> }}");
        var items = myFixture.completeBasic();
        assertTrue(items == null || java.util.Arrays.stream(items).noneMatch(i -> i.getLookupString().equals("foreach")));
    }

    public void testHelperMembers() {
        myFixture.configureByText("t.looma.html", "{{ Number.perc<caret> }}");
        myFixture.completeBasic();
        myFixture.checkResult("{{ Number.percent<caret> }}");
    }

    public void testLoopMembers() {
        myFixture.configureByText("t.looma.html", "@foreach($a as $b) {{ $loop->fi<caret> }} @endforeach");
        myFixture.completeBasic();
        myFixture.checkResult("@foreach($a as $b) {{ $loop->first<caret> }} @endforeach");
    }

    public void testTypingEchoBracesClosesThem() {
        myFixture.configureByText("t.looma.html", "<p>{<caret></p>");
        myFixture.type("{");
        myFixture.checkResult("<p>{{ <caret> }}</p>");
        myFixture.type("-");
        myFixture.checkResult("<p>{{-- <caret> --}}</p>");
    }

    public void testTypingRawEcho() {
        myFixture.configureByText("t.looma.html", "{!<caret>");
        myFixture.type("!");
        myFixture.checkResult("{!! <caret> !!}");
    }

    public void testCommentLine() {
        myFixture.configureByText("t.looma.html", "<p>hi<caret></p>");
        myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE);
        myFixture.checkResult("{{--<p>hi</p>--}}");
    }

    public void testFolding() {
        myFixture.configureByText("t.looma.html", "@if($a)\n  x\n@else\n  y\n@endif\n{{-- a\n b --}}");
        myFixture.doHighlighting();
        com.intellij.codeInsight.folding.CodeFoldingManager.getInstance(getProject()).buildInitialFoldings(myFixture.getEditor());
        FoldRegion[] regions = myFixture.getEditor().getFoldingModel().getAllFoldRegions();
        assertEquals(Arrays.toString(regions), 3, regions.length);
    }

    public void testDirectiveDocumentation() {
        myFixture.configureByText("t.looma.html", "@fore<caret>ach($a as $b) @endforeach");
        PsiElement element = DocumentationManager.getInstance(getProject())
                .findTargetElement(myFixture.getEditor(), myFixture.getFile());
        DocumentationProvider provider = DocumentationManager.getProviderFromElement(element);
        String doc = provider.generateDoc(element, myFixture.getFile().findElementAt(myFixture.getCaretOffset()));
        assertNotNull(doc);
        assertTrue(doc, doc.contains("@foreach") && doc.contains("$loop"));
    }

    public void testStructureView() {
        myFixture.configureByText("t.looma.html", "@extends('app')\n@section('content') @if($a) x @endif @endsection");
        StructureViewBuilder builder = LanguageStructureViewBuilder.getInstance().getStructureViewBuilder(myFixture.getFile());
        assertNotNull(builder);
        var model = ((TreeBasedStructureViewBuilder) builder).createStructureViewModel(myFixture.getEditor());
        try {
            TreeElement[] roots = model.getRoot().getChildren();
            String all = Arrays.toString(Arrays.stream(roots).map(r -> r.getPresentation().getPresentableText()).toArray());
            assertTrue(all, all.contains("Looma"));
        } finally {
            model.dispose();
        }
    }
}
