package io.github.djamelkorei.looma.intellij.component;

import com.intellij.psi.impl.source.xml.XmlElementDescriptorProvider;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import io.github.djamelkorei.looma.intellij.lang.LoomaFileViewProvider;
import org.jetbrains.annotations.Nullable;

/** Describes {@code <x-name>} tags in templates as Looma components (and {@code <x-slot>} as slots). */
public final class LoomaComponentDescriptorProvider implements XmlElementDescriptorProvider {

    @Override
    public @Nullable XmlElementDescriptor getDescriptor(XmlTag tag) {
        return isComponentTag(tag) ? new LoomaComponentDescriptor(tag) : null;
    }

    public static boolean isComponentTag(XmlTag tag) {
        return tag.getName().startsWith(LoomaComponents.TAG_PREFIX) && tag.getName().length() > 2
                && tag.getContainingFile().getViewProvider() instanceof LoomaFileViewProvider;
    }
}
