package io.github.djamelkorei.looma.intellij.component;

import com.intellij.psi.PsiElement;
import com.intellij.xml.impl.BasicXmlAttributeDescriptor;
import org.jetbrains.annotations.Nullable;

/** An attribute of a component tag; declared by the variable's first use in the component template. */
public final class LoomaComponentAttributeDescriptor extends BasicXmlAttributeDescriptor {

    private final String name;
    private final PsiElement declaration;

    public LoomaComponentAttributeDescriptor(String name, @Nullable PsiElement declaration) {
        this.name = name;
        this.declaration = declaration;
    }

    @Override public PsiElement getDeclaration() { return declaration; }
    @Override public String getName() { return name; }
    @Override public void init(PsiElement element) {}
    @Override public boolean isRequired() { return false; }
    @Override public boolean isFixed() { return false; }
    @Override public boolean hasIdType() { return false; }
    @Override public boolean hasIdRefType() { return false; }
    @Override public String getDefaultValue() { return null; }
    @Override public boolean isEnumerated() { return false; }
    @Override public String[] getEnumeratedValues() { return null; }
}
