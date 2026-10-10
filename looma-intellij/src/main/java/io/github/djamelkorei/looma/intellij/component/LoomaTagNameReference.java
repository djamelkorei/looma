package io.github.djamelkorei.looma.intellij.component;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.xml.TagNameReference;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import io.github.djamelkorei.looma.intellij.lang.LoomaFileType;
import io.github.djamelkorei.looma.intellij.psi.LoomaTemplates;
import org.jetbrains.annotations.NotNull;

/** The name of {@code <x-alert>}: renaming or moving {@code components/alert} renames the tag. */
final class LoomaTagNameReference extends TagNameReference {

    LoomaTagNameReference(ASTNode nameElement, boolean startTagFlag) {
        super(nameElement, startTagFlag);
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        XmlTag tag = getTagElement();
        if (tag != null && LoomaComponentDescriptorProvider.isComponentTag(tag)
                && newElementName.endsWith(LoomaFileType.SUFFIX)) {
            String base = newElementName.substring(0, newElementName.length() - LoomaFileType.SUFFIX.length());
            String component = LoomaComponents.componentName(tag);
            int dot = component.lastIndexOf('.');
            return super.handleElementRename(
                    LoomaComponents.TAG_PREFIX + (dot < 0 ? base : component.substring(0, dot + 1) + base));
        }
        return super.handleElementRename(newElementName);
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        XmlTag tag = getTagElement();
        String components = LoomaTemplates.COMPONENTS + ".";
        if (tag != null && element instanceof PsiFile file && LoomaComponentDescriptorProvider.isComponentTag(tag)) {
            String view = LoomaTemplates.nameOf(file);
            if (view != null && view.startsWith(components)) {
                return super.handleElementRename(LoomaComponents.TAG_PREFIX + view.substring(components.length()));
            }
        }
        return super.bindToElement(element);
    }
}
