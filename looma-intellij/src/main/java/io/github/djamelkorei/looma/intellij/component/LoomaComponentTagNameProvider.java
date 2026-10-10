package io.github.djamelkorei.looma.intellij.component;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlTagNameProvider;
import io.github.djamelkorei.looma.intellij.LoomaIcons;
import io.github.djamelkorei.looma.intellij.psi.LoomaTemplates;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** Offers {@code <x-...>} for every template in the components folder, and {@code <x-slot>}. */
public final class LoomaComponentTagNameProvider implements XmlTagNameProvider {

    @Override
    public void addTagNameVariants(List<LookupElement> elements, @NotNull XmlTag tag, String prefix) {
        if (!LoomaComponents.isTemplate(tag.getContainingFile())) return;
        String components = LoomaTemplates.COMPONENTS + ".";
        for (String view : LoomaTemplates.all(tag).keySet()) {
            if (view.startsWith(components)) {
                elements.add(LookupElementBuilder.create(LoomaComponents.TAG_PREFIX + view.substring(components.length()))
                        .withIcon(LoomaIcons.FILE).withTypeText("component", true));
            }
        }
        elements.add(LookupElementBuilder.create(LoomaComponents.TAG_PREFIX + LoomaComponents.SLOT)
                .withIcon(LoomaIcons.FILE).withTypeText("named slot", true));
    }
}
