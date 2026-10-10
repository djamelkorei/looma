package io.github.djamelkorei.looma.intellij.injection;

import com.intellij.lang.injection.MultiHostInjector;
import com.intellij.lang.injection.MultiHostRegistrar;
import com.intellij.psi.ElementManipulators;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import io.github.djamelkorei.looma.intellij.component.LoomaComponentDescriptorProvider;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** {@code <x-alert :count="items.size()">}: the value of a {@code :}-attribute is a Looma expression. */
public final class LoomaAttributeInjector implements MultiHostInjector {

    @Override
    public void getLanguagesToInject(@NotNull MultiHostRegistrar registrar, @NotNull PsiElement context) {
        if (!(context instanceof XmlAttributeValue value) || !(value instanceof PsiLanguageInjectionHost host)) return;
        if (!(value.getParent() instanceof XmlAttribute attribute) || !attribute.getName().startsWith(":")) return;
        if (attribute.getParent() == null || !LoomaComponentDescriptorProvider.isComponentTag(attribute.getParent())) return;
        var range = ElementManipulators.getValueTextRange(value);
        if (range.isEmpty()) return;
        registrar.startInjecting(LoomaExpressionLanguage.INSTANCE).addPlace(null, null, host, range).doneInjecting();
    }

    @Override
    public @NotNull List<? extends Class<? extends PsiElement>> elementsToInjectIn() {
        return List.of(XmlAttributeValue.class);
    }
}
