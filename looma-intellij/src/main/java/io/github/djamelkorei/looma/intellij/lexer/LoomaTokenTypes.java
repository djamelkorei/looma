package io.github.djamelkorei.looma.intellij.lexer;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.OuterLanguageElementType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import io.github.djamelkorei.looma.intellij.lang.LoomaLanguage;

public interface LoomaTokenTypes {

    /** HTML between Looma constructs. */
    IElementType TEMPLATE_TEXT = new LoomaTokenType("TEMPLATE_TEXT");
    /** A Looma construct as seen from the HTML tree. */
    IElementType OUTER_ELEMENT = new OuterLanguageElementType("LOOMA_FRAGMENT", LoomaLanguage.INSTANCE);

    IElementType COMMENT = new LoomaTokenType("COMMENT");
    /** The '@' of {@code @{{ ... }}} and {@code @@}. */
    IElementType ESCAPE = new LoomaTokenType("ESCAPE");
    IElementType ECHO_OPEN = new LoomaTokenType("{{");
    IElementType ECHO_CLOSE = new LoomaTokenType("}}");
    IElementType RAW_OPEN = new LoomaTokenType("{!!");
    IElementType RAW_CLOSE = new LoomaTokenType("!!}");
    /** {@code @name}. */
    IElementType DIRECTIVE = new LoomaTokenType("DIRECTIVE");

    // expressions
    IElementType WHITE_SPACE = TokenType.WHITE_SPACE;
    IElementType BAD_CHARACTER = TokenType.BAD_CHARACTER;
    /** {@code $name}. */
    IElementType VARIABLE = new LoomaTokenType("VARIABLE");
    IElementType IDENTIFIER = new LoomaTokenType("IDENTIFIER");
    /** {@code @priceService}: a Spring bean marked {@code @TemplateAccessible}. */
    IElementType BEAN = new LoomaTokenType("BEAN");
    IElementType KEYWORD = new LoomaTokenType("KEYWORD");
    IElementType STRING = new LoomaTokenType("STRING");
    IElementType NUMBER = new LoomaTokenType("NUMBER");
    IElementType LPAREN = new LoomaTokenType("(");
    IElementType RPAREN = new LoomaTokenType(")");
    IElementType LBRACKET = new LoomaTokenType("[");
    IElementType RBRACKET = new LoomaTokenType("]");
    IElementType LBRACE = new LoomaTokenType("{");
    IElementType RBRACE = new LoomaTokenType("}");
    IElementType COMMA = new LoomaTokenType(",");
    IElementType SEMICOLON = new LoomaTokenType(";");
    IElementType COLON = new LoomaTokenType(":");
    IElementType DOT = new LoomaTokenType(".");
    /** {@code ->}, Blade's member access. */
    IElementType ARROW = new LoomaTokenType("->");
    IElementType SAFE_DOT = new LoomaTokenType("?.");
    IElementType OPERATOR = new LoomaTokenType("OPERATOR");

    TokenSet COMMENTS = TokenSet.create(COMMENT);
    TokenSet STRINGS = TokenSet.create(STRING);
    TokenSet WHITESPACES = TokenSet.create(WHITE_SPACE);
    TokenSet MEMBER_ACCESS = TokenSet.create(DOT, ARROW, SAFE_DOT);
}
