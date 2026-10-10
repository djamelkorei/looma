package io.github.djamelkorei.looma.intellij.java;

import static com.intellij.patterns.PsiJavaPatterns.psiLiteral;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.ProcessingContext;
import io.github.djamelkorei.looma.intellij.reference.LoomaTemplateReference;
import org.jetbrains.annotations.NotNull;

/** {@code return "home";} in a controller points to {@code templates/home.looma.html}. */
public final class LoomaJavaViewReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(psiLiteral(), new PsiReferenceProvider() {
            @Override
            public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                    @NotNull ProcessingContext context) {
                PsiLiteralExpression literal = (PsiLiteralExpression) element;
                if (LoomaJavaViews.viewName(literal) == null) return PsiReference.EMPTY_ARRAY;
                TextRange range = ElementManipulators.getValueTextRange(literal);
                // Soft: the view may well belong to another view resolver.
                return new PsiReference[] {new LoomaTemplateReference(literal, range, true, "")};
            }
        }, PsiReferenceRegistrar.LOWER_PRIORITY);
    }
}
