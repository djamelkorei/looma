package io.github.djamelkorei.looma.intellij.annotator;

import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.util.XmlTagUtil;
import io.github.djamelkorei.looma.intellij.component.LoomaComponentDescriptorProvider;
import io.github.djamelkorei.looma.intellij.component.LoomaComponents;
import org.jetbrains.annotations.NotNull;

/** Reports {@code <x-name>} tags without a {@code components/name} template, as the engine's startup check does. */
public final class LoomaComponentAnnotator implements Annotator {

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!(element instanceof XmlTag tag) || !LoomaComponentDescriptorProvider.isComponentTag(tag)) return;
        String name = LoomaComponents.componentName(tag);
        if (LoomaComponents.isSlot(name) || LoomaComponents.template(tag) != null) return;
        PsiElement start = XmlTagUtil.getStartTagNameElement(tag);
        holder.newAnnotation(HighlightSeverity.ERROR, "Component template 'components." + name + "' not found")
                .range(start != null ? start : tag).create();
    }
}
