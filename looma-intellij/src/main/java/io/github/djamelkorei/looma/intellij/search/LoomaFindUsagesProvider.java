package io.github.djamelkorei.looma.intellij.search;

import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.FindUsagesProvider;
import com.intellij.psi.PsiElement;
import io.github.djamelkorei.looma.intellij.psi.LoomaVariableDeclaration;
import org.jetbrains.annotations.NotNull;

public final class LoomaFindUsagesProvider implements FindUsagesProvider {

    @Override
    public WordsScanner getWordsScanner() {
        return new LoomaWordsScanner();
    }

    @Override
    public boolean canFindUsagesFor(@NotNull PsiElement element) {
        return element instanceof LoomaVariableDeclaration;
    }

    @Override public String getHelpId(@NotNull PsiElement element) { return null; }
    @Override public @NotNull String getType(@NotNull PsiElement element) { return "variable"; }

    @Override
    public @NotNull String getDescriptiveName(@NotNull PsiElement element) {
        return element instanceof LoomaVariableDeclaration d ? "$" + d.getName() : element.getText();
    }

    @Override
    public @NotNull String getNodeText(@NotNull PsiElement element, boolean useFullName) {
        return getDescriptiveName(element);
    }
}
