package io.github.djamelkorei.looma.intellij.properties;

import com.intellij.lang.properties.IProperty;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import java.util.List;

public class LoomaMessageReferenceTest extends BasePlatformTestCase {

    public void testLangKeyResolvesToProperty() {
        myFixture.addFileToProject("messages.properties", "greeting=Hello {0}\nfarewell=Bye");
        PsiFile file = myFixture.addFileToProject("templates/home.looma.html", "@lang('gree<caret>ting', $user)");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
        assertNotNull(ref);
        PsiElement target = ref instanceof PsiPolyVariantReference poly && poly.multiResolve(false).length > 0
                ? poly.multiResolve(false)[0].getElement() : ref.resolve();
        assertTrue(String.valueOf(target), target instanceof IProperty);
    }

    public void testLangKeyCompletion() {
        myFixture.addFileToProject("messages.properties", "greeting=Hello\nfarewell=Bye");
        PsiFile file = myFixture.addFileToProject("templates/home.looma.html", "@lang('<caret>')");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        myFixture.completeBasic();
        List<String> items = myFixture.getLookupElementStrings();
        assertNotNull(items);
        assertTrue(items.toString(), items.containsAll(List.of("greeting", "farewell")));
    }
}
