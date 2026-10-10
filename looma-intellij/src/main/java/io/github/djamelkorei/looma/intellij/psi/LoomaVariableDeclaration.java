package io.github.djamelkorei.looma.intellij.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/** A variable introduced by a directive: {@code $post} in {@code @foreach($posts as $post)}. */
public final class LoomaVariableDeclaration extends LoomaPsiElement implements PsiNameIdentifierOwner {

    public LoomaVariableDeclaration(@NotNull ASTNode node) {
        super(node);
    }

    /** The name without '$'. */
    @Override
    public @NotNull String getName() {
        String text = getText();
        return text.startsWith("$") ? text.substring(1) : text;
    }

    @Override
    public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
        if (getFirstChild() instanceof LeafPsiElement leaf) {
            leaf.replaceWithText(getText().startsWith("$") ? "$" + name : name);
        }
        return this;
    }

    @Override
    public PsiElement getNameIdentifier() {
        return getFirstChild();
    }

    @Override
    public int getTextOffset() {
        return getTextRange().getStartOffset() + (getText().startsWith("$") ? 1 : 0);
    }

    /** The directive that declares this variable. */
    public LoomaDirective getDirective() {
        return (LoomaDirective) getParent().getParent();
    }
}
