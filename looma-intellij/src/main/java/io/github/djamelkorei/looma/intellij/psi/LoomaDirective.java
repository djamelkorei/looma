package io.github.djamelkorei.looma.intellij.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import io.github.djamelkorei.looma.intellij.lexer.LoomaTokenTypes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** {@code @name} or {@code @name(args)}. */
public final class LoomaDirective extends LoomaPsiElement {

    public LoomaDirective(@NotNull ASTNode node) {
        super(node);
    }

    /** The name without '@'. */
    @Override
    public @NotNull String getName() {
        return getNameElement().getText().substring(1);
    }

    /** The {@code @name} token. */
    public @NotNull PsiElement getNameElement() {
        return getNode().findChildByType(LoomaTokenTypes.DIRECTIVE).getPsi();
    }

    public @Nullable LoomaArguments getArguments() {
        return PsiTreeUtil.getChildOfType(this, LoomaArguments.class);
    }

    /** The block this directive opens, continues or ends; null for an inline directive. */
    public @Nullable LoomaBlock getBlock() {
        return getParent() instanceof LoomaBlock block ? block : null;
    }
}
