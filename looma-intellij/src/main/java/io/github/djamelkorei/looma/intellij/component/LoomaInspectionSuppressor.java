package io.github.djamelkorei.looma.intellij.component;

import com.intellij.codeInspection.InspectionSuppressor;
import com.intellij.codeInspection.SuppressQuickFix;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import io.github.djamelkorei.looma.intellij.lang.LoomaFileViewProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** {@code <x-slot:header>} names a slot; the colon is not an XML namespace prefix, so that inspection stays quiet. */
public final class LoomaInspectionSuppressor implements InspectionSuppressor {

    private static final String UNBOUND_NAMESPACE = "XmlUnboundNsPrefix";

    @Override
    public boolean isSuppressedFor(@NotNull PsiElement element, @NotNull String toolId) {
        if (!toolId.equals(UNBOUND_NAMESPACE)) return false;
        if (!(element.getContainingFile().getViewProvider() instanceof LoomaFileViewProvider)) return false;
        XmlTag tag = element instanceof XmlTag t ? t : PsiTreeUtil.getParentOfType(element, XmlTag.class);
        return tag != null && LoomaComponentDescriptorProvider.isComponentTag(tag)
                && LoomaComponents.isSlot(LoomaComponents.componentName(tag));
    }

    @Override
    public SuppressQuickFix @NotNull [] getSuppressActions(@Nullable PsiElement element, @NotNull String toolId) {
        return SuppressQuickFix.EMPTY_ARRAY;
    }
}
