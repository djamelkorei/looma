package io.github.djamelkorei.looma.intellij.reference;

import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.IncorrectOperationException;
import io.github.djamelkorei.looma.intellij.LoomaIcons;
import io.github.djamelkorei.looma.intellij.psi.LoomaScopes;
import io.github.djamelkorei.looma.intellij.psi.LoomaVariable;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A variable read resolves to the directive variable it names ({@code @foreach ... as $post}), to the loop
 * for {@code $loop}, or to where the application puts it into the model (see {@link LoomaModelVariables}).
 * Unresolved variables are fine: the model is only known at run time.
 */
public final class LoomaVariableReference extends PsiReferenceBase<LoomaVariable> {

    public LoomaVariableReference(@NotNull LoomaVariable element) {
        super(element, element.nameRange(), true);
    }

    @Override
    public @Nullable PsiElement resolve() {
        String name = getElement().getName();
        PsiElement local = LoomaScopes.resolve(getElement(), name);
        if (local != null) return local;
        for (LoomaModelVariables provider : LoomaModelVariables.EP.getExtensionList()) {
            PsiElement target = provider.resolve(getElement().getContainingFile(), name);
            if (target != null) return target;
        }
        return null;
    }

    @Override
    public Object @NotNull [] getVariants() {
        List<Object> variants = new ArrayList<>();
        LoomaScopes.visible(getElement()).forEach((name, description) -> variants.add(
                LookupElementBuilder.create(name).withIcon(LoomaIcons.FILE).withTypeText(description, true)));
        for (LoomaModelVariables provider : LoomaModelVariables.EP.getExtensionList()) {
            variants.addAll(provider.variants(getElement().getContainingFile()));
        }
        return variants.toArray();
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        return super.handleElementRename(newElementName.startsWith("$") ? newElementName.substring(1) : newElementName);
    }
}
