package io.github.djamelkorei.looma.intellij.lexer;

import static io.github.djamelkorei.looma.intellij.lexer.LoomaTokenTypes.*;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import io.github.djamelkorei.looma.intellij.lang.LoomaDirectives;
import io.github.djamelkorei.looma.intellij.lang.LoomaDirectives.Syntax;
import java.util.Arrays;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

/**
 * Splits a template into HTML ({@link LoomaTokenTypes#TEMPLATE_TEXT}) and Looma tokens, following the same
 * rules as the engine's lexer. Unlike the engine it never fails: an unclosed echo or argument list ends at
 * the end of its line so that one typo does not swallow the rest of the file.
 *
 * <p>The whole range is tokenized in {@link #start}. Tokens that the lexer can be restarted from report
 * state 0; tokens inside expressions, {@code @verbatim} and escapes report a non-zero state.
 */
public final class LoomaLexer extends LexerBase {

    private static final int IN_EXPRESSION = 1;
    private static final int IN_LITERAL = 2;

    private static final Set<String> KEYWORDS = Set.of("as", "true", "false", "null", "and", "or", "not",
            "instanceof", "matches", "between", "eq", "ne", "lt", "gt", "le", "ge", "div", "mod");
    private static final String[] OPERATORS = {"??", "?:", "==", "!=", "<=", ">=", "&&", "||", "++", "--", "+=",
            "-=", "!", "<", ">", "+", "-", "*", "/", "%", "^", "=", "?", "#", "@"};

    private final LoomaDirectives directives;
    /** Lex the whole range as one expression (component attributes like {@code :count="..."}). */
    private final boolean expressionOnly;

    private CharSequence buffer = "";
    private int bufferEnd;
    private int[] starts = new int[64];
    private int[] states = new int[64];
    private IElementType[] types = new IElementType[64];
    private int count;
    private int index;
    private int textStart = -1;

    public LoomaLexer() {
        this(LoomaDirectives.BUILTIN);
    }

    public LoomaLexer(LoomaDirectives directives) {
        this(directives, false);
    }

    public LoomaLexer(LoomaDirectives directives, boolean expressionOnly) {
        this.directives = directives;
        this.expressionOnly = expressionOnly;
    }

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
        this.buffer = buffer;
        this.bufferEnd = endOffset;
        this.count = 0;
        this.index = 0;
        this.textStart = -1;
        if (expressionOnly) expression(startOffset, endOffset);
        else tokenize(startOffset, endOffset);
        add(null, endOffset, 0); // sentinel holding the end of the last token
        count--;
    }

    @Override public int getState() { return index < count ? states[index] : 0; }
    @Override public IElementType getTokenType() { return index < count ? types[index] : null; }
    @Override public int getTokenStart() { return starts[index]; }
    @Override public int getTokenEnd() { return starts[index + 1]; }
    @Override public void advance() { if (index < count) index++; }
    @Override public @NotNull CharSequence getBufferSequence() { return buffer; }
    @Override public int getBufferEnd() { return bufferEnd; }

    // ---- tokenizing

    private void tokenize(int from, int end) {
        int i = from;
        while (i < end) {
            char c = buffer.charAt(i);
            if (startsWith("{{--", i, end)) {
                int e = indexOf("--}}", i + 4, end);
                int stop = e < 0 ? end : e + 4;
                emit(COMMENT, i, 0);
                i = stop;
            } else if (startsWith("@{{", i, end)) {
                int e = indexOf("}}", i + 3, end);
                if (e < 0) {
                    text(i);
                    i++;
                } else {
                    emit(ESCAPE, i, 0);
                    emit(TEMPLATE_TEXT, i + 1, IN_LITERAL);
                    i = e + 2;
                }
            } else if (startsWith("{!!", i, end)) {
                i = echo(i, end, RAW_OPEN, 3, "!!}", RAW_CLOSE);
            } else if (startsWith("{{", i, end)) {
                i = echo(i, end, ECHO_OPEN, 2, "}}", ECHO_CLOSE);
            } else if (c == '@' && startsWith("@@", i, end)) {
                emit(ESCAPE, i, 0);
                emit(TEMPLATE_TEXT, i + 1, IN_LITERAL);
                i += 2;
            } else if (c == '@') {
                i = directive(i, end);
            } else {
                text(i);
                i++;
            }
        }
        flushText();
    }

    private int echo(int i, int end, IElementType open, int openLength, String close, IElementType closeType) {
        int contentStart = i + openLength;
        int e = findClose(close, contentStart, end);
        emit(open, i, 0);
        if (e < 0) {
            int stop = lineEnd(contentStart, end);
            expression(contentStart, stop);
            return stop;
        }
        expression(contentStart, e);
        emit(closeType, e, IN_EXPRESSION);
        return e + close.length();
    }

    private int directive(int i, int end) {
        int j = i + 1;
        while (j < end && Character.isLetter(buffer.charAt(j))) j++;
        String name = buffer.subSequence(i + 1, j).toString();
        Syntax syntax = name.isEmpty() || looksLikeEmailOrIdentifier(j, end) ? null : directives.syntax(name);
        if (syntax == null) {
            text(i);
            return i + 1;
        }
        if (name.equals(LoomaDirectives.VERBATIM)) {
            emit(DIRECTIVE, i, 0);
            int e = indexOf("@" + LoomaDirectives.END_VERBATIM, j, end);
            if (e < 0) {
                if (j < end) emit(TEMPLATE_TEXT, j, IN_LITERAL);
                return end;
            }
            if (e > j) emit(TEMPLATE_TEXT, j, IN_LITERAL);
            emit(DIRECTIVE, e, IN_LITERAL);
            return e + 1 + LoomaDirectives.END_VERBATIM.length();
        }
        if (syntax == Syntax.NONE) {
            emit(DIRECTIVE, i, 0);
            return j;
        }
        int k = j;
        if (syntax == Syntax.REQUIRED) {
            while (k < end && (buffer.charAt(k) == ' ' || buffer.charAt(k) == '\t')) k++;
        }
        if (k < end && buffer.charAt(k) == '(') {
            emit(DIRECTIVE, i, 0);
            if (k > j) emit(WHITE_SPACE, j, IN_EXPRESSION);
            int close = matchParen(k, end);
            emit(LPAREN, k, IN_EXPRESSION);
            if (close < 0) {
                int stop = lineEnd(k + 1, end);
                expression(k + 1, stop);
                return stop;
            }
            expression(k + 1, close);
            emit(RPAREN, close, IN_EXPRESSION);
            return close + 1;
        }
        if (syntax == Syntax.OPTIONAL) {
            emit(DIRECTIVE, i, 0);
            return j;
        }
        text(i);
        return i + 1;
    }

    /** Tokens of a SpEL/Blade expression between {@code from} and {@code to}. */
    private void expression(int from, int to) {
        int i = from;
        while (i < to) {
            char c = buffer.charAt(i);
            int start = i;
            IElementType type;
            if (Character.isWhitespace(c)) {
                while (i < to && Character.isWhitespace(buffer.charAt(i))) i++;
                type = WHITE_SPACE;
            } else if (c == '$' && i + 1 < to && isIdentifierStart(buffer.charAt(i + 1))) {
                i = identifierEnd(i + 1, to);
                type = VARIABLE;
            } else if (isIdentifierStart(c)) {
                i = identifierEnd(i, to);
                type = KEYWORDS.contains(buffer.subSequence(start, i).toString()) ? KEYWORD : IDENTIFIER;
            } else if (Character.isDigit(c)) {
                i = numberEnd(i, to);
                type = NUMBER;
            } else if (c == '\'' || c == '"') {
                i = stringEnd(i, to);
                type = STRING;
            } else if (startsWith("->", i, to)) {
                i += 2;
                type = ARROW;
            } else if (startsWith("?.", i, to)) {
                i += 2;
                type = SAFE_DOT;
            } else {
                i++;
                type = switch (c) {
                    case '(' -> LPAREN;
                    case ')' -> RPAREN;
                    case '[' -> LBRACKET;
                    case ']' -> RBRACKET;
                    case '{' -> LBRACE;
                    case '}' -> RBRACE;
                    case ',' -> COMMA;
                    case ';' -> SEMICOLON;
                    case ':' -> COLON;
                    case '.' -> DOT;
                    default -> null;
                };
                if (type == null) {
                    i = operatorEnd(start, to);
                    type = i > start ? OPERATOR : BAD_CHARACTER;
                    if (i == start) i++;
                }
            }
            emit(type, start, IN_EXPRESSION);
        }
    }

    private int operatorEnd(int i, int to) {
        for (String op : OPERATORS) {
            if (startsWith(op, i, to)) return i + op.length();
        }
        return i;
    }

    private static boolean isIdentifierStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private int identifierEnd(int i, int to) {
        while (i < to && (Character.isLetterOrDigit(buffer.charAt(i)) || buffer.charAt(i) == '_')) i++;
        return i;
    }

    private int numberEnd(int i, int to) {
        while (i < to && Character.isDigit(buffer.charAt(i))) i++;
        if (i + 1 < to && buffer.charAt(i) == '.' && Character.isDigit(buffer.charAt(i + 1))) {
            i++;
            while (i < to && Character.isDigit(buffer.charAt(i))) i++;
        }
        if (i < to && (buffer.charAt(i) == 'e' || buffer.charAt(i) == 'E')) {
            int k = i + 1;
            if (k < to && (buffer.charAt(k) == '+' || buffer.charAt(k) == '-')) k++;
            if (k < to && Character.isDigit(buffer.charAt(k))) {
                i = k;
                while (i < to && Character.isDigit(buffer.charAt(i))) i++;
            }
        }
        if (i < to && "lLdDfF".indexOf(buffer.charAt(i)) >= 0) i++;
        return i;
    }

    /** End of a quoted string; SpEL escapes a quote by doubling it. Unterminated strings run to {@code to}. */
    private int stringEnd(int i, int to) {
        char quote = buffer.charAt(i++);
        while (i < to) {
            char c = buffer.charAt(i);
            if (c == '\\' && i + 1 < to) {
                i += 2;
            } else if (c == quote) {
                if (i + 1 < to && buffer.charAt(i + 1) == quote) i += 2;
                else return i + 1;
            } else {
                i++;
            }
        }
        return to;
    }

    // ---- engine rules

    /** True if the directive-like name is continued by {@code .word}, a digit or '_' (e.g. a@empty.com). */
    private boolean looksLikeEmailOrIdentifier(int end, int limit) {
        if (end >= limit) return false;
        char c = buffer.charAt(end);
        return Character.isDigit(c) || c == '_'
                || (c == '.' && end + 1 < limit && Character.isLetterOrDigit(buffer.charAt(end + 1)));
    }

    /** Index of {@code end} after {@code from}, ignoring it inside quoted strings; -1 if missing. */
    private int findClose(String end, int from, int limit) {
        char quote = 0;
        for (int i = from; i < limit; i++) {
            char c = buffer.charAt(i);
            if (quote != 0) {
                if (c == '\\') i++;
                else if (c == quote) quote = 0;
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (startsWith(end, i, limit)) {
                return i;
            }
        }
        // Like the engine: an unbalanced quote falls back to the first terminator.
        return indexOf(end, from, limit);
    }

    /** Index of the ')' matching the '(' at {@code open}, skipping quoted strings; -1 if unbalanced. */
    private int matchParen(int open, int limit) {
        int depth = 0;
        char quote = 0;
        for (int i = open; i < limit; i++) {
            char c = buffer.charAt(i);
            if (quote != 0) {
                if (c == '\\') i++;
                else if (c == quote) quote = 0;
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i;
            }
        }
        return -1;
    }

    // ---- helpers

    private int lineEnd(int from, int limit) {
        for (int i = from; i < limit; i++) {
            if (buffer.charAt(i) == '\n') return i;
        }
        return limit;
    }

    private boolean startsWith(String s, int at, int limit) {
        if (at + s.length() > limit) return false;
        for (int k = 0; k < s.length(); k++) {
            if (buffer.charAt(at + k) != s.charAt(k)) return false;
        }
        return true;
    }

    private int indexOf(String s, int from, int limit) {
        for (int i = from; i + s.length() <= limit; i++) {
            if (startsWith(s, i, limit)) return i;
        }
        return -1;
    }

    private void text(int at) {
        if (textStart < 0) textStart = at;
    }

    private void flushText() {
        if (textStart >= 0) {
            add(TEMPLATE_TEXT, textStart, 0);
            textStart = -1;
        }
    }

    private void emit(IElementType type, int start, int state) {
        flushText();
        add(type, start, state);
    }

    private void add(IElementType type, int start, int state) {
        if (count == types.length) {
            int size = count * 2;
            types = Arrays.copyOf(types, size);
            starts = Arrays.copyOf(starts, size);
            states = Arrays.copyOf(states, size);
        }
        types[count] = type;
        starts[count] = start;
        states[count] = state;
        count++;
    }
}
