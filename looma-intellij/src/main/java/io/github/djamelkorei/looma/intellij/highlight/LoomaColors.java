package io.github.djamelkorei.looma.intellij.highlight;

import static com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey;

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;

public interface LoomaColors {

    TextAttributesKey DIRECTIVE = createTextAttributesKey("LOOMA_DIRECTIVE", DefaultLanguageHighlighterColors.KEYWORD);
    TextAttributesKey ECHO_BRACES =
            createTextAttributesKey("LOOMA_ECHO_BRACES", DefaultLanguageHighlighterColors.TEMPLATE_LANGUAGE_COLOR);
    TextAttributesKey RAW_BRACES =
            createTextAttributesKey("LOOMA_RAW_BRACES", DefaultLanguageHighlighterColors.METADATA);
    TextAttributesKey COMMENT = createTextAttributesKey("LOOMA_COMMENT", DefaultLanguageHighlighterColors.BLOCK_COMMENT);
    TextAttributesKey ESCAPE =
            createTextAttributesKey("LOOMA_ESCAPE", DefaultLanguageHighlighterColors.VALID_STRING_ESCAPE);
    TextAttributesKey VARIABLE =
            createTextAttributesKey("LOOMA_VARIABLE", DefaultLanguageHighlighterColors.LOCAL_VARIABLE);
    TextAttributesKey IDENTIFIER =
            createTextAttributesKey("LOOMA_IDENTIFIER", DefaultLanguageHighlighterColors.IDENTIFIER);
    TextAttributesKey PROPERTY =
            createTextAttributesKey("LOOMA_PROPERTY", DefaultLanguageHighlighterColors.INSTANCE_FIELD);
    TextAttributesKey METHOD =
            createTextAttributesKey("LOOMA_METHOD", DefaultLanguageHighlighterColors.INSTANCE_METHOD);
    TextAttributesKey KEYWORD = createTextAttributesKey("LOOMA_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD);
    TextAttributesKey STRING = createTextAttributesKey("LOOMA_STRING", DefaultLanguageHighlighterColors.STRING);
    TextAttributesKey NUMBER = createTextAttributesKey("LOOMA_NUMBER", DefaultLanguageHighlighterColors.NUMBER);
    TextAttributesKey OPERATOR =
            createTextAttributesKey("LOOMA_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN);
    TextAttributesKey PARENTHESES =
            createTextAttributesKey("LOOMA_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES);
    TextAttributesKey BRACKETS = createTextAttributesKey("LOOMA_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS);
    TextAttributesKey BRACES = createTextAttributesKey("LOOMA_BRACES", DefaultLanguageHighlighterColors.BRACES);
    TextAttributesKey COMMA = createTextAttributesKey("LOOMA_COMMA", DefaultLanguageHighlighterColors.COMMA);
    TextAttributesKey DOT = createTextAttributesKey("LOOMA_DOT", DefaultLanguageHighlighterColors.DOT);
    TextAttributesKey BAD_CHARACTER = createTextAttributesKey("LOOMA_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER);
}
