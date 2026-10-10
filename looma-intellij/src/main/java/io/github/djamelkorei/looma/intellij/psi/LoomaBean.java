package io.github.djamelkorei.looma.intellij.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ContributedReferenceHost;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import org.jetbrains.annotations.NotNull;

/** {@code @priceService} in an expression: a Spring bean templates may call. */
public final class LoomaBean extends LoomaPsiElement implements ContributedReferenceHost {

    public LoomaBean(@NotNull ASTNode node) {
        super(node);
    }

    /** The bean name, without '@'. */
    @Override
    public @NotNull String getName() {
        return nameRange().substring(getText());
    }

    public @NotNull TextRange nameRange() {
        return TextRange.create(Math.min(1, getTextLength()), getTextLength());
    }

    @Override
    public PsiReference @NotNull [] getReferences() {
        return ReferenceProvidersRegistry.getReferencesFromProviders(this);
    }
}
