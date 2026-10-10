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

/** References from directive arguments: template names (includes, layouts), section names and stack names. */
public final class LoomaReferenceContributor extends PsiReferenceContributor {

    @Override
    public void registerReferenceProviders(@NotNull PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(psiElement(LoomaStringLiteral.class), new PsiReferenceProvider() {
            @Override
            public PsiReference @NotNull [] getReferencesByElement(@NotNull PsiElement element,
                    @NotNull ProcessingContext context) {
                LoomaStringLiteral literal = (LoomaStringLiteral) element;
                LoomaDirective directive = literal.getDirective();
                if (directive == null) return PsiReference.EMPTY_ARRAY;
                int index = literal.argumentIndex();
                PsiReference ref = switch (directive.getName()) {
                    case "include", "extends" -> index == 0 ? template(literal, false) : null;
                    // a missing template is the expected case for these
                    case "includeIf" -> index == 0 ? template(literal, true) : null;
                    case "includeFirst" -> isInFirstArgument(literal, directive) ? template(literal, true) : null;
                    case "includeWhen", "includeUnless" -> index == 1 ? template(literal, false) : null;
                    case "each" -> index == 0 || index == 3 ? template(literal, false) : null;
                    case "section" -> index == 0 ? new LoomaNameReference(literal, LoomaNameReference.Kind.SECTION) : null;
                    case "yield" -> index == 0 ? new LoomaNameReference(literal, LoomaNameReference.Kind.YIELD) : null;
                    case "push", "prepend" -> index == 0 ? new LoomaNameReference(literal, LoomaNameReference.Kind.PUSH) : null;
                    case "stack" -> index == 0 ? new LoomaNameReference(literal, LoomaNameReference.Kind.STACK) : null;
                    default -> null;
                };
                return ref == null ? PsiReference.EMPTY_ARRAY : new PsiReference[] {ref};
            }
        });
    }

    private static PsiReference template(LoomaStringLiteral literal, boolean soft) {
        TextRange range = literal.getValueRange();
        return new LoomaTemplateReference(literal, range, soft, "");
    }

    /** {@code 'nav'} in {@code @includeFirst({'themes.dark.nav', 'nav'})}. */
    private static boolean isInFirstArgument(LoomaStringLiteral literal, LoomaDirective directive) {
        var args = directive.getArguments() == null ? null : directive.getArguments().split();
        return args != null && !args.isEmpty() && args.get(0).stream().anyMatch(e -> e == literal);
    }
}
