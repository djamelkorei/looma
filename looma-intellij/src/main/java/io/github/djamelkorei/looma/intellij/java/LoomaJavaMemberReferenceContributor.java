package io.github.djamelkorei.looma.intellij.java;

import static com.intellij.patterns.PlatformPatterns.psiElement;

import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import io.github.djamelkorei.looma.intellij.psi.LoomaMember;
import org.jetbrains.annotations.NotNull;

/** Members of expressions whose Java type is known get a reference to the getter, field or method. */
public final class LoomaJavaMemberReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(psiElement(LoomaMember.class), new PsiReferenceProvider() {
            @Override
            public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                    @NotNull ProcessingContext context) {
                LoomaMember member = (LoomaMember) element;
                PsiType type = LoomaJavaTypes.typeOf(member.getQualifier());
                return type == null ? PsiReference.EMPTY_ARRAY
                        : new PsiReference[] {new LoomaJavaMemberReference(member, type)};
            }
        });
    }
}
