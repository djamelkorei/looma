package io.github.djamelkorei.looma.intellij.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.ContributedReferenceHost;
import io.github.djamelkorei.looma.intellij.lexer.LoomaTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A property or method after {@code .}, {@code ->} or {@code ?.}: {@code title} in {@code $post->title}. */
public final class LoomaMember extends LoomaPsiElement implements ContributedReferenceHost {

    public LoomaMember(@NotNull ASTNode node) {
        super(node);
    }

    @Override
    public @NotNull String getName() {
        return getText();
    }

    /** True if followed by '(': a method call. */
    public boolean isCall() {
        PsiElement next = skipWhitespace(getNextSibling(), true);
        return next != null && next.getNode().getElementType() == LoomaTokenTypes.LPAREN;
    }

    /** What the member is accessed on, if it is a variable or another member ({@code $a->b->c}). */
    public @Nullable PsiElement getQualifier() {
        PsiElement access = skipWhitespace(getPrevSibling(), false);
        if (access == null || !LoomaTokenTypes.MEMBER_ACCESS.contains(access.getNode().getElementType())) return null;
        PsiElement qualifier = skipWhitespace(access.getPrevSibling(), false);
        return qualifier instanceof LoomaVariable || qualifier instanceof LoomaMember
                || qualifier instanceof LoomaVariableDeclaration ? qualifier : null;
    }

    @Override
    public PsiReference @NotNull [] getReferences() {
        return ReferenceProvidersRegistry.getReferencesFromProviders(this);
    }

    private static PsiElement skipWhitespace(PsiElement e, boolean forward) {
        while (e instanceof PsiWhiteSpace) e = forward ? e.getNextSibling() : e.getPrevSibling();
        return e;
    }
}
