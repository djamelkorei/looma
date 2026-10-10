package io.github.djamelkorei.looma.intellij.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.annotations.NotNull;

/** Edits string literals, variables and members, which each wrap a single token. */
public final class LoomaTokenManipulator extends AbstractElementManipulator<PsiElement> {

    @Override
    public PsiElement handleContentChange(@NotNull PsiElement element, @NotNull TextRange range, String newContent) {
        if (element.getFirstChild() instanceof LeafPsiElement leaf) {
            leaf.replaceWithText(range.replace(element.getText(), newContent));
        }
        return element;
    }

    @Override
    public @NotNull TextRange getRangeInElement(@NotNull PsiElement element) {
        if (element instanceof LoomaStringLiteral literal) return literal.getValueRange();
        if (element instanceof LoomaVariable variable) return variable.nameRange();
        if (element instanceof LoomaBean bean) return bean.nameRange();
        return super.getRangeInElement(element);
    }
}
