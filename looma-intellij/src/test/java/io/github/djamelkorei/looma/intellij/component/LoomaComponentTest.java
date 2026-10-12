package io.github.djamelkorei.looma.intellij.component;

import com.intellij.codeInspection.htmlInspections.HtmlUnknownAttributeInspection;
import com.intellij.codeInspection.htmlInspections.HtmlUnknownTagInspection;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.djamelkorei.looma.intellij.psi.LoomaVariable;
import java.util.List;

public class LoomaComponentTest extends BasePlatformTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.enableInspections(new HtmlUnknownTagInspection(), new HtmlUnknownAttributeInspection());
        myFixture.addFileToProject("templates/components/alert.looma.html",
                "<div {{ $attributes }}>{{ $title }} {{ $type }} {{ $slot }}</div>");
    }

    private PsiFile html() {
        PsiFile top = com.intellij.lang.injection.InjectedLanguageManager.getInstance(getProject())
                .getTopLevelFile(myFixture.getFile());
        return top.getViewProvider().getPsi(com.intellij.lang.html.HTMLLanguage.INSTANCE);
    }

    private void configure(String text) {
        PsiFile file = myFixture.addFileToProject("templates/page.looma.html", text);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
    }

    /** Named slots and self-closing components are Looma syntax, not XML namespaces or broken HTML. */
    public void testSlotsAndSelfClosingComponentsRaiseNoHtmlWarnings() {
        myFixture.enableInspections(new com.intellij.codeInsight.daemon.impl.analysis.XmlUnboundNsPrefixInspection(),
                new com.intellij.xml.util.CheckEmptyTagInspection());
        myFixture.addFileToProject("templates/components/card.looma.html", "<div>{{ $header }} {{ $slot }}</div>");
        myFixture.addFileToProject("templates/components/badge.looma.html", "<b>{{ $status }}</b>");
        configure("""
                <x-card>
                  <x-slot:header>Title <x-badge :status="$s" /></x-slot:header>
                  body
                </x-card>
                <x-alert type="info" />
                <div />
                """);
        List<String> problems = myFixture.doHighlighting(com.intellij.lang.annotation.HighlightSeverity.WEAK_WARNING).stream()
                .map(h -> h.getDescription() + " @" + h.getText()).toList();
        assertEquals(problems.toString(), List.of("Empty tag doesn't work in some browsers @<div />"), problems);
    }

    public void testComponentTagResolvesToTemplate() {
        configure("<x-al<caret>ert type=\"info\">hi</x-alert>");
        PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
        assertNotNull(ref);
        PsiElement target = ref.resolve();
        assertNotNull(target);
        assertEquals("alert.looma.html", target.getContainingFile().getName());
    }

    public void testNoUnknownTagOrAttributeWarnings() {
        configure("<x-alert type=\"info\" :count=\"items.size()\" class=\"mb-4\"><div>body</div>"
                + "<x-slot:title>T</x-slot:title></x-alert>");
        myFixture.checkHighlighting(true, false, true);
    }

    public void testUnknownComponent() {
        configure("<<error descr=\"Component template 'components.nope' not found\">x-nope</error>></x-nope>");
        myFixture.checkHighlighting(false, false, false);
    }

    public void testAttributeCompletionFromComponentVariables() {
        configure("<x-alert <caret>></x-alert>");
        myFixture.completeBasic();
        List<String> items = myFixture.getLookupElementStrings();
        assertNotNull(items);
        assertTrue(items.toString(), items.containsAll(List.of("title", "type", ":title")));
        assertFalse(items.contains("slot"));
    }

    public void testTagCompletion() {
        configure("<x-<caret>");
        myFixture.completeBasic();
        List<String> items = myFixture.getLookupElementStrings();
        assertNotNull(items);
        assertTrue(items.toString(), items.contains("x-alert"));
    }

    public void testSlotResolvesToVariableInComponent() {
        configure("<x-alert><x-slot:ti<caret>tle>T</x-slot:title></x-alert>");
        PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
        assertNotNull(ref);
        assertTrue(String.valueOf(ref.resolve()), ref.resolve() instanceof LoomaVariable);
    }

    public void testRenamingComponentTemplateRenamesTags() {
        configure("<x-alert type=\"info\">hi</x-alert>");
        PsiFile alert = myFixture.findFileInTempDir("templates/components/alert.looma.html") == null ? null
                : getPsiManager().findFile(myFixture.findFileInTempDir("templates/components/alert.looma.html"));
        assertNotNull(alert);
        myFixture.renameElement(alert, "callout.looma.html");
        myFixture.checkResult("<x-callout type=\"info\">hi</x-callout>");
    }

    public void testAttributeExpressionIsInjected() {
        configure("@foreach($posts as $post) <x-alert :title=\"$po<caret>st.title\"></x-alert> @endforeach");
        // The caret is inside the injection, so the fixture works on the injected expression.
        assertTrue(myFixture.getFile() instanceof io.github.djamelkorei.looma.intellij.injection.LoomaExpressionFile);
        PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
        assertNotNull(ref);
        assertTrue(String.valueOf(ref.resolve()),
                ref.resolve() instanceof io.github.djamelkorei.looma.intellij.psi.LoomaVariableDeclaration);
    }

    public void testPlainAttributeIsNotInjected() {
        configure("<x-alert title=\"$po<caret>st\"></x-alert>");
        assertNull(com.intellij.lang.injection.InjectedLanguageManager.getInstance(getProject())
                .findInjectedElementAt(html(), myFixture.getCaretOffset()));
    }
}
