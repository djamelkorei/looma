package io.github.djamelkorei.looma.intellij.psi;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.ContributedReferenceHost;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** {@code 'text'} or {@code "text"}. References (templates, sections, message keys...) are contributed. */
public final class LoomaStringLiteral extends LoomaPsiElement implements ContributedReferenceHost {

    public LoomaStringLiteral(@NotNull ASTNode node) {
        super(node);
    }

    /** The text without quotes, SpEL doubled quotes undone. */
    public @NotNull String getValue() {
        String text = getText();
        if (text.isEmpty()) return "";
        char quote = text.charAt(0);
        return getValueRange().substring(text).replace("" + quote + quote, "" + quote);
    }

    /** The range of the value inside this element. */
    public @NotNull TextRange getValueRange() {
        String text = getText();
        if (text.length() < 2) return TextRange.create(Math.min(1, text.length()), text.length());
        boolean closed = text.charAt(text.length() - 1) == text.charAt(0);
        return TextRange.create(1, closed ? text.length() - 1 : text.length());
    }

    /** The directive this literal is an argument of, or null (in an echo). */
    public @Nullable LoomaDirective getDirective() {
        return PsiTreeUtil.getParentOfType(this, LoomaDirective.class, true, LoomaEcho.class);
    }

    /** Which argument of its directive this literal is (and nothing else), or -1. */
    public int argumentIndex() {
        LoomaDirective directive = getDirective();
        if (directive == null || directive.getArguments() == null) return -1;
        var args = directive.getArguments().split();
        for (int i = 0; i < args.size(); i++) {
            if (args.get(i).size() == 1 && args.get(i).get(0) == this) return i;
        }
        return -1;
    }

    /** True if this is the {@code index}-th argument of its directive and nothing else. */
    public boolean isArgument(int index) {
        LoomaDirective directive = getDirective();
        return directive != null && directive.getArguments() != null
                && directive.getArguments().literal(index) == this;
    }

    @Override
    public PsiReference @NotNull [] getReferences() {
        return ReferenceProvidersRegistry.getReferencesFromProviders(this);
    }
}
