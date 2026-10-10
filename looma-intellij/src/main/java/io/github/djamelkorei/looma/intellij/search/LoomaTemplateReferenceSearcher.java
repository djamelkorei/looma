package io.github.djamelkorei.looma.intellij.search;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import io.github.djamelkorei.looma.intellij.lang.LoomaFileType;
import org.jetbrains.annotations.NotNull;

/**
 * Finds view names pointing to a template. The platform looks for the file name ({@code nav.looma}),
 * which view names ({@code 'partials.nav'}) never contain, so search for the last segment instead.
 */
public final class LoomaTemplateReferenceSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {

    public LoomaTemplateReferenceSearcher() {
        super(true);
    }

    @Override
    public void processQuery(@NotNull ReferencesSearch.SearchParameters params,
            @NotNull Processor<? super PsiReference> consumer) {
        if (!(params.getElementToSearch() instanceof PsiFile file)) return;
        String name = file.getName();
        if (!name.endsWith(LoomaFileType.SUFFIX)) return;
        String word = name.substring(0, name.length() - LoomaFileType.SUFFIX.length());
        if (word.isEmpty()) return;
        params.getOptimizer().searchWord(word, params.getEffectiveSearchScope(),
                (short) (UsageSearchContext.IN_STRINGS | UsageSearchContext.IN_CODE | UsageSearchContext.IN_FOREIGN_LANGUAGES),
                true, file);
    }
}
