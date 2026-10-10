package io.github.djamelkorei.looma.intellij.search;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import io.github.djamelkorei.looma.intellij.psi.LoomaBlock;
import io.github.djamelkorei.looma.intellij.psi.LoomaVariable;
import io.github.djamelkorei.looma.intellij.psi.LoomaVariableDeclaration;
import org.jetbrains.annotations.NotNull;

/** Uses of a loop variable are inside its block: look there instead of searching words ({@code $} is a word char). */
public final class LoomaVariableReferenceSearcher
        extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {

    public LoomaVariableReferenceSearcher() {
        super(true);
    }

    @Override
    public void processQuery(@NotNull ReferencesSearch.SearchParameters params,
            @NotNull Processor<? super PsiReference> consumer) {
        if (!(params.getElementToSearch() instanceof LoomaVariableDeclaration declaration)) return;
        LoomaBlock block = declaration.getDirective().getBlock();
        PsiElement scope = block != null ? block : declaration.getContainingFile();
        for (LoomaVariable variable : PsiTreeUtil.findChildrenOfType(scope, LoomaVariable.class)) {
            PsiReference ref = variable.getReference();
            if (ref != null && ref.isReferenceTo(declaration) && !consumer.process(ref)) return;
        }
    }
}
