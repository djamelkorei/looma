package io.github.djamelkorei.looma.intellij.highlight;

import static io.github.djamelkorei.looma.intellij.lexer.LoomaTokenTypes.*;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.tree.IElementType;
import io.github.djamelkorei.looma.intellij.lang.LoomaDirectives;
import io.github.djamelkorei.looma.intellij.lexer.LoomaLexer;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/** Highlights the Looma tokens; the HTML between them is highlighted by the HTML layer. */
public final class LoomaSyntaxHighlighter extends SyntaxHighlighterBase {

    private static final Map<IElementType, TextAttributesKey> KEYS = new HashMap<>();

    static {
        KEYS.put(DIRECTIVE, LoomaColors.DIRECTIVE);
        KEYS.put(ECHO_OPEN, LoomaColors.ECHO_BRACES);
        KEYS.put(ECHO_CLOSE, LoomaColors.ECHO_BRACES);
        KEYS.put(RAW_OPEN, LoomaColors.RAW_BRACES);
        KEYS.put(RAW_CLOSE, LoomaColors.RAW_BRACES);
        KEYS.put(COMMENT, LoomaColors.COMMENT);
        KEYS.put(ESCAPE, LoomaColors.ESCAPE);
        KEYS.put(VARIABLE, LoomaColors.VARIABLE);
        KEYS.put(IDENTIFIER, LoomaColors.IDENTIFIER);
        KEYS.put(KEYWORD, LoomaColors.KEYWORD);
        KEYS.put(STRING, LoomaColors.STRING);
        KEYS.put(NUMBER, LoomaColors.NUMBER);
        KEYS.put(OPERATOR, LoomaColors.OPERATOR);
        KEYS.put(ARROW, LoomaColors.OPERATOR);
        KEYS.put(SAFE_DOT, LoomaColors.OPERATOR);
        KEYS.put(COLON, LoomaColors.OPERATOR);
        KEYS.put(SEMICOLON, LoomaColors.COMMA);
        KEYS.put(LPAREN, LoomaColors.PARENTHESES);
        KEYS.put(RPAREN, LoomaColors.PARENTHESES);
        KEYS.put(LBRACKET, LoomaColors.BRACKETS);
        KEYS.put(RBRACKET, LoomaColors.BRACKETS);
        KEYS.put(LBRACE, LoomaColors.BRACES);
        KEYS.put(RBRACE, LoomaColors.BRACES);
        KEYS.put(COMMA, LoomaColors.COMMA);
        KEYS.put(DOT, LoomaColors.DOT);
        KEYS.put(BAD_CHARACTER, LoomaColors.BAD_CHARACTER);
    }

    private final LoomaDirectives directives;

    public LoomaSyntaxHighlighter(LoomaDirectives directives) {
        this.directives = directives;
    }

    @Override
    public @NotNull Lexer getHighlightingLexer() {
        return new LoomaLexer(directives);
    }

    @Override
    public TextAttributesKey @NotNull [] getTokenHighlights(IElementType tokenType) {
        return pack(KEYS.get(tokenType));
    }
}
