package io.github.djamelkorei.looma.intellij.component;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlAttributeDescriptor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;
import com.intellij.xml.XmlNSDescriptor;
import com.intellij.xml.util.XmlUtil;
import io.github.djamelkorei.looma.intellij.psi.LoomaPsiFile;
import io.github.djamelkorei.looma.intellij.psi.LoomaVariable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A {@code <x-name>} tag: declared by its template (so Ctrl+Click opens it), with the template's
 * variables as attributes. Any other attribute is allowed too: it ends up in {@code $attributes}.
 */
public final class LoomaComponentDescriptor implements XmlElementDescriptor {

    private final XmlTag tag;
    private final String name;

    public LoomaComponentDescriptor(@NotNull XmlTag tag) {
        this.tag = tag;
        this.name = tag.getName();
    }

    @Override
    public @Nullable PsiElement getDeclaration() {
        // Missing templates resolve to the tag itself: LoomaComponentAnnotator reports them more clearly.
        String component = name.substring(LoomaComponents.TAG_PREFIX.length());
        PsiElement target = LoomaComponents.isSlot(component) ? LoomaComponents.slotTarget(tag) : LoomaComponents.template(tag);
        return target != null ? target : tag;
    }

    @Override public String getName(PsiElement context) { return name; }
    @Override public String getName() { return name; }
    @Override public String getQualifiedName() { return name; }
    @Override public String getDefaultName() { return name; }
    @Override public void init(PsiElement element) {}
    @Override public XmlElementsGroup getTopGroup() { return null; }
    @Override public int getContentType() { return CONTENT_TYPE_ANY; }
    @Override public String getDefaultValue() { return null; }

    @Override
    public XmlElementDescriptor @NotNull [] getElementsDescriptors(XmlTag context) {
        XmlNSDescriptor html = getNSDescriptor();
        XmlDocument document = PsiTreeUtil.getParentOfType(context, XmlDocument.class);
        return html == null || document == null ? EMPTY_ARRAY : html.getRootElementsDescriptors(document);
    }

    @Override
    public @Nullable XmlElementDescriptor getElementDescriptor(XmlTag childTag, XmlTag contextTag) {
        if (LoomaComponentDescriptorProvider.isComponentTag(childTag)) return new LoomaComponentDescriptor(childTag);
        XmlNSDescriptor html = getNSDescriptor();
        return html == null ? null : html.getElementDescriptor(childTag);
    }

    @Override
    public XmlAttributeDescriptor @NotNull [] getAttributesDescriptors(@Nullable XmlTag context) {
        LoomaPsiFile template = LoomaComponents.template(tag);
        if (template == null) return XmlAttributeDescriptor.EMPTY;
        List<XmlAttributeDescriptor> out = new ArrayList<>();
        for (Map.Entry<String, LoomaVariable> param : LoomaComponents.parameters(template).entrySet()) {
            out.add(new LoomaComponentAttributeDescriptor(param.getKey(), param.getValue()));
            out.add(new LoomaComponentAttributeDescriptor(":" + param.getKey(), param.getValue()));
        }
        return out.toArray(XmlAttributeDescriptor.EMPTY);
    }

    @Override
    public @Nullable XmlAttributeDescriptor getAttributeDescriptor(String attributeName, @Nullable XmlTag context) {
        String param = attributeName.startsWith(":") ? attributeName.substring(1) : attributeName;
        LoomaPsiFile template = LoomaComponents.template(tag);
        LoomaVariable use = template == null ? null : LoomaComponents.parameters(template).get(param);
        return new LoomaComponentAttributeDescriptor(attributeName, use);
    }

    @Override
    public @Nullable XmlAttributeDescriptor getAttributeDescriptor(XmlAttribute attribute) {
        return getAttributeDescriptor(attribute.getName(), attribute.getParent());
    }

    @Override
    public @Nullable XmlNSDescriptor getNSDescriptor() {
        XmlDocument document = PsiTreeUtil.getParentOfType(tag, XmlDocument.class);
        return document == null ? null : document.getDefaultNSDescriptor(XmlUtil.HTML_URI, true);
    }
}
