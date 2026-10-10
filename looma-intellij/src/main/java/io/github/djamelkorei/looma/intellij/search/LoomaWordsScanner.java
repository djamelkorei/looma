package io.github.djamelkorei.looma.intellij.search;

import com.intellij.lang.cacheBuilder.WordOccurrence;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lang.findUsages.LanguageFindUsages;
import com.intellij.lang.html.HTMLLanguage;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Processor;
import io.github.djamelkorei.looma.intellij.lexer.LoomaLexer;
import io.github.djamelkorei.looma.intellij.lexer.LoomaTokenTypes;
import org.jetbrains.annotations.NotNull;

/**
 * Indexes the words of Looma tokens ({@code post} for {@code $post}, {@code if} for {@code @if}), and of
 * the HTML between them with HTML's own scanner plus split at '-' (for component names).
 */
public final class LoomaWordsScanner implements WordsScanner {

    @Override
    public void processWords(@NotNull CharSequence fileText, @NotNull Processor<? super WordOccurrence> processor) {
        LoomaLexer lexer = new LoomaLexer();
        lexer.start(fileText);
        StringBuilder html = new StringBuilder(fileText.length());
        for (; lexer.getTokenType() != null; lexer.advance()) {
            IElementType type = lexer.getTokenType();
            int start = lexer.getTokenStart();
            int end = lexer.getTokenEnd();
            if (type == LoomaTokenTypes.TEMPLATE_TEXT) {
                html.append(fileText, start, end);
                // HTML sees <x-alert> as one word; component searches look for "alert".
                if (!words(fileText, start, end, WordOccurrence.Kind.CODE, processor)) return;
                continue;
            }
            html.append(" ".repeat(end - start));
            WordOccurrence.Kind kind;
            if (type == LoomaTokenTypes.STRING) kind = WordOccurrence.Kind.LITERALS;
            else if (type == LoomaTokenTypes.COMMENT) kind = WordOccurrence.Kind.COMMENTS;
            else if (type == LoomaTokenTypes.VARIABLE || type == LoomaTokenTypes.IDENTIFIER
                    || type == LoomaTokenTypes.DIRECTIVE || type == LoomaTokenTypes.KEYWORD) kind = WordOccurrence.Kind.CODE;
            else continue;
            if (!words(fileText, start, end, kind, processor)) return;
        }
        WordsScanner htmlScanner = LanguageFindUsages.getWordsScanner(HTMLLanguage.INSTANCE);
        if (htmlScanner != null) htmlScanner.processWords(html, processor);
    }

    /** Emits every run of letters, digits and '_' in the range. */
    private static boolean words(CharSequence text, int start, int end, WordOccurrence.Kind kind,
            Processor<? super WordOccurrence> processor) {
        int i = start;
        while (i < end) {
            while (i < end && !isWordChar(text.charAt(i))) i++;
            int wordStart = i;
            while (i < end && isWordChar(text.charAt(i))) i++;
            if (i > wordStart && !processor.process(new WordOccurrence(text, wordStart, i, kind))) return false;
        }
        return true;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
