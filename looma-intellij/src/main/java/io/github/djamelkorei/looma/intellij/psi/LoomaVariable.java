package io.github.djamelkorei.looma.intellij.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiReference;
import io.github.djamelkorei.looma.intellij.reference.LoomaVariableReference;
import org.jetbrains.annotations.NotNull;

/** A variable read: {@code $user} or, since the '$' is optional, {@code user}. */
public final class LoomaVariable extends LoomaPsiElement {

    public LoomaVariable(@NotNull ASTNode node) {
        super(node);
    }

    /** The name without '$'. */
    @Override
    public @NotNull String getName() {
        return nameRange().substring(getText());
    }

    /** The range of the name (without '$') inside this element. */
    public @NotNull TextRange nameRange() {
        return TextRange.create(getText().startsWith("$") ? 1 : 0, getTextLength());
    }

    @Override
    public PsiReference getReference() {
        return new LoomaVariableReference(this);
    }
}
