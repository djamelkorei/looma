package io.github.djamelkorei.looma.intellij.lang;

import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.MultiplePsiFilesPerDocumentFileViewProvider;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.templateLanguages.TemplateDataElementType;
import com.intellij.psi.templateLanguages.TemplateLanguageFileViewProvider;
import io.github.djamelkorei.looma.intellij.lexer.LoomaTokenTypes;
import java.util.Set;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A template has two trees: Looma (the base language) and HTML (everything between Looma constructs). */
public final class LoomaFileViewProvider extends MultiplePsiFilesPerDocumentFileViewProvider
        implements TemplateLanguageFileViewProvider {

    public static final TemplateDataElementType HTML_TEMPLATE_DATA = new TemplateDataElementType(
            "LOOMA_HTML", LoomaLanguage.INSTANCE, LoomaTokenTypes.TEMPLATE_TEXT, LoomaTokenTypes.OUTER_ELEMENT) {
        /**
         * An echo or {@code @json} produces a value, so the HTML (and the JavaScript inside {@code <script>}) is
         * parsed with a placeholder in its place: {@code window.x = @json($x);} is then valid JavaScript. The
         * placeholder text comes from {@link LoomaOuterLanguagePatcher}.
         */
        @Override
        protected boolean isInsertionToken(@Nullable IElementType tokenType, @NotNull CharSequence tokenSequence) {
            return tokenType == LoomaTokenTypes.ECHO_OPEN || tokenType == LoomaTokenTypes.RAW_OPEN
                    || (tokenType == LoomaTokenTypes.DIRECTIVE && "@json".contentEquals(tokenSequence));
        }
    };

    public LoomaFileViewProvider(@NotNull PsiManager manager, @NotNull VirtualFile file, boolean eventSystemEnabled) {
        super(manager, file, eventSystemEnabled);
    }

    @Override public @NotNull Language getBaseLanguage() { return LoomaLanguage.INSTANCE; }
    @Override public @NotNull Language getTemplateDataLanguage() { return HTMLLanguage.INSTANCE; }
    @Override public @NotNull Set<Language> getLanguages() { return Set.of(LoomaLanguage.INSTANCE, HTMLLanguage.INSTANCE); }

    @Override
    protected @NotNull MultiplePsiFilesPerDocumentFileViewProvider cloneInner(@NotNull VirtualFile copy) {
        return new LoomaFileViewProvider(getManager(), copy, false);
    }

    @Override
    protected PsiFile createFile(@NotNull Language lang) {
        ParserDefinition definition = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
        if (definition == null) return null;
        if (lang.is(HTMLLanguage.INSTANCE)) {
            PsiFileImpl file = (PsiFileImpl) definition.createFile(this);
            file.setContentElementType(HTML_TEMPLATE_DATA);
            return file;
        }
        return lang.is(LoomaLanguage.INSTANCE) ? definition.createFile(this) : null;
    }
}
