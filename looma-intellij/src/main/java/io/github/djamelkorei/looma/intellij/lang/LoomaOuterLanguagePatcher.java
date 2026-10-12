package io.github.djamelkorei.looma.intellij.lang;

import com.intellij.psi.templateLanguages.DefaultOuterLanguagePatcher;
import com.intellij.psi.templateLanguages.TemplateDataElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The text that stands in for an echo or {@code @json} while the HTML and JavaScript of a template are parsed
 * (see {@link LoomaFileViewProvider#HTML_TEMPLATE_DATA}). It is registered for the data languages, so it answers
 * only for Looma's own template data type and leaves other template languages alone.
 */
public final class LoomaOuterLanguagePatcher implements TemplateDataElementType.OuterLanguageRangePatcher {

    @Override
    public @Nullable String getTextForOuterLanguageInsertionRange(@NotNull TemplateDataElementType type,
            @NotNull CharSequence outerElementText) {
        return type == LoomaFileViewProvider.HTML_TEMPLATE_DATA ? DefaultOuterLanguagePatcher.OUTER_EXPRESSION_PLACEHOLDER : null;
    }
}
