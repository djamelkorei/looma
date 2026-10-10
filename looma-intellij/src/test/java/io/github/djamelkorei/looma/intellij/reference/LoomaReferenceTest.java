package io.github.djamelkorei.looma.intellij.reference;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import io.github.djamelkorei.looma.intellij.psi.LoomaDirective;
import io.github.djamelkorei.looma.intellij.psi.LoomaStringLiteral;
import io.github.djamelkorei.looma.intellij.psi.LoomaVariableDeclaration;
import java.util.Arrays;
import java.util.List;

public class LoomaReferenceTest extends BasePlatformTestCase {

    private PsiReference referenceAtCaret() {
        PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
        assertNotNull("no reference at caret", ref);
        return ref;
    }

    public void testIncludeResolvesToTemplate() {
        PsiFile nav = myFixture.addFileToProject("templates/partials/nav.looma.html", "<nav></nav>");
        PsiFile home = myFixture.addFileToProject("templates/home.looma.html", "@include('partials.n<caret>av')");
        myFixture.configureFromExistingVirtualFile(home.getVirtualFile());
        assertEquals(nav, referenceAtCaret().resolve());
    }

    public void testTemplateNameCompletion() {
        myFixture.addFileToProject("templates/layouts/app.looma.html", "");
        myFixture.addFileToProject("templates/layouts/admin.looma.html", "");
        PsiFile home = myFixture.addFileToProject("templates/home.looma.html", "@extends('layouts.<caret>')");
        myFixture.configureFromExistingVirtualFile(home.getVirtualFile());
        myFixture.completeBasic();
        List<String> items = myFixture.getLookupElementStrings();
        assertNotNull(items);
        assertTrue(items.toString(), items.containsAll(List.of("layouts.app", "layouts.admin")));
    }

    public void testRenamingTemplateUpdatesInclude() {
        PsiFile nav = myFixture.addFileToProject("templates/partials/nav.looma.html", "<nav></nav>");
        PsiFile home = myFixture.addFileToProject("templates/home.looma.html", "@include('partials.nav')");
        myFixture.configureFromExistingVirtualFile(home.getVirtualFile());
        myFixture.renameElement(nav, "menu.looma.html");
        myFixture.checkResult("@include('partials.menu')");
    }

    public void testSectionResolvesToLayoutYield() {
        myFixture.addFileToProject("templates/layouts/app.looma.html", "<main>@yield('content')</main>");
        PsiFile home = myFixture.addFileToProject("templates/home.looma.html",
                "@extends('layouts.app') @section('con<caret>tent') x @endsection");
        myFixture.configureFromExistingVirtualFile(home.getVirtualFile());
        PsiElement target = referenceAtCaret().resolve();
        assertTrue(target instanceof LoomaStringLiteral);
        assertEquals("yield", ((LoomaStringLiteral) target).getDirective().getName());
    }

    public void testYieldResolvesToChildSections() {
        myFixture.addFileToProject("templates/home.looma.html", "@extends('layouts.app') @section('content') x @endsection");
        myFixture.addFileToProject("templates/about.looma.html", "@extends('layouts.app') @section('content', 'y')");
        PsiFile layout = myFixture.addFileToProject("templates/layouts/app.looma.html", "@yield('con<caret>tent')");
        myFixture.configureFromExistingVirtualFile(layout.getVirtualFile());
        PsiPolyVariantReference ref = (PsiPolyVariantReference) referenceAtCaret();
        assertEquals(2, ref.multiResolve(false).length);
    }

    public void testPushResolvesToStack() {
        myFixture.addFileToProject("templates/layouts/app.looma.html", "@stack('scripts')");
        PsiFile home = myFixture.addFileToProject("templates/home.looma.html", "@push('scr<caret>ipts') x @endpush");
        myFixture.configureFromExistingVirtualFile(home.getVirtualFile());
        PsiElement target = referenceAtCaret().resolve();
        assertEquals("stack", ((LoomaStringLiteral) target).getDirective().getName());
    }

    public void testLoopVariableResolvesAndRenames() {
        myFixture.configureByText("t.looma.html", "@foreach($posts as $post) {{ $po<caret>st->title }} @endforeach");
        PsiElement target = referenceAtCaret().resolve();
        assertTrue(target instanceof LoomaVariableDeclaration);
        myFixture.renameElementAtCaret("item");
        myFixture.checkResult("@foreach($posts as $item) {{ $item->title }} @endforeach");
    }

    public void testLoopVariableAndMetadata() {
        myFixture.configureByText("t.looma.html", "@foreach($posts as $post) {{ $lo<caret>op->index }} @endforeach");
        PsiElement target = referenceAtCaret().resolve();
        assertTrue(target instanceof LoomaDirective);
        assertEquals("foreach", ((LoomaDirective) target).getName());
    }

    public void testListVariableIsNotTheLoopVariable() {
        myFixture.configureByText("t.looma.html", "@foreach($po<caret>st as $post) @endforeach");
        assertNull(referenceAtCaret().resolve());
    }

    public void testVariableCompletion() {
        myFixture.configureByText("t.looma.html", "@foreach($posts as $post) {{ $<caret> }} @endforeach");
        myFixture.completeBasic();
        List<String> items = myFixture.getLookupElementStrings();
        assertNotNull(items);
        assertTrue(items.toString(), items.containsAll(Arrays.asList("post", "loop", "errors")));
    }
}
