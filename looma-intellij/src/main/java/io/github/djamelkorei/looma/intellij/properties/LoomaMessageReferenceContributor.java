package io.github.djamelkorei.looma.intellij.properties;

import static com.intellij.patterns.PlatformPatterns.psiElement;

import com.intellij.lang.properties.references.PropertyReference;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import io.github.djamelkorei.looma.intellij.psi.LoomaStringLiteral;
import org.jetbrains.annotations.NotNull;

/** {@code @lang('greeting')} points to {@code greeting=...} in the project's message bundles. */
public final class LoomaMessageReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(psiElement(LoomaStringLiteral.class), new PsiReferenceProvider() {
            @Override
            public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                    @NotNull ProcessingContext context) {
                LoomaStringLiteral literal = (LoomaStringLiteral) element;
                if (!literal.isArgument(0) || !literal.getDirective().getName().equals("lang")) {
                    return PsiReference.EMPTY_ARRAY;
                }
                // Soft: messages may also come from a MessageSource that is not a properties file.
                return new PsiReference[] {
                        new PropertyReference(literal.getValue(), literal, null, true, literal.getValueRange())};
            }
        });
    }
}
