package io.github.djamelkorei.looma.intellij.reference;

import static com.intellij.patterns.PlatformPatterns.psiElement;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceContributor;
import com.intellij.psi.PsiReferenceProvider;
import com.intellij.psi.PsiReferenceRegistrar;
import com.intellij.util.ProcessingContext;
import io.github.djamelkorei.looma.intellij.psi.LoomaDirective;
import io.github.djamelkorei.looma.intellij.psi.LoomaStringLiteral;
import org.jetbrains.annotations.NotNull;

/** References from directive arguments: template names, section names and stack names. */
public final class LoomaReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(psiElement(LoomaStringLiteral.class), new PsiReferenceProvider() {
            @Override
            public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                    @NotNull ProcessingContext context) {
                LoomaStringLiteral literal = (LoomaStringLiteral) element;
                if (!literal.isArgument(0)) return PsiReference.EMPTY_ARRAY;
                LoomaDirective directive = literal.getDirective();
                PsiReference ref = switch (directive.getName()) {
                    case "include", "extends" -> template(literal);
                    case "section" -> new LoomaNameReference(literal, LoomaNameReference.Kind.SECTION);
                    case "yield" -> new LoomaNameReference(literal, LoomaNameReference.Kind.YIELD);
                    case "push", "prepend" -> new LoomaNameReference(literal, LoomaNameReference.Kind.PUSH);
                    case "stack" -> new LoomaNameReference(literal, LoomaNameReference.Kind.STACK);
                    default -> null;
                };
                return ref == null ? PsiReference.EMPTY_ARRAY : new PsiReference[] {ref};
            }
        });
    }

    private static PsiReference template(LoomaStringLiteral literal) {
        TextRange range = literal.getValueRange();
        return new LoomaTemplateReference(literal, range, false, "");
    }
}
