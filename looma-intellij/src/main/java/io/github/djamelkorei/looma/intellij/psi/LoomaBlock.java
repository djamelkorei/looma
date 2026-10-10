package io.github.djamelkorei.looma.intellij.psi;

import com.intellij.lang.ASTNode;
import com.intellij.psi.util.PsiTreeUtil;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** {@code @if(...) ... @else ... @endif}: the opening directive, the body and the secondary and end tags. */
public final class LoomaBlock extends LoomaPsiElement {

    public LoomaBlock(@NotNull ASTNode node) {
        super(node);
    }

    public @NotNull LoomaDirective getOpener() {
        return PsiTreeUtil.getChildOfType(this, LoomaDirective.class);
    }

    /** The opener, the secondary tags and the end tag, in order. */
    public @NotNull List<LoomaDirective> getTags() {
        return PsiTreeUtil.getChildrenOfTypeAsList(this, LoomaDirective.class);
    }

    public @NotNull String getName() {
        return getOpener().getName();
    }
}
