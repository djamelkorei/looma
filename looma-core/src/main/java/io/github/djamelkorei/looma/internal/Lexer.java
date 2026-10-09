package io.github.djamelkorei.looma.internal;

import io.github.djamelkorei.looma.LoomaException;
import java.util.ArrayList;
import java.util.List;
import io.github.djamelkorei.looma.spi.Syntax;

/** Splits template source into text, echo and directive tokens. */
final class Lexer {

    enum Kind { TEXT, ECHO, RAW, DIRECTIVE, COMPONENT_OPEN, COMPONENT_SELF, COMPONENT_CLOSE }

    record Token(Kind kind, String text, String args, int line) {}

    private Lexer() {}

    static List<Token> lex(String s, DirectiveRegistry registry) {
        List<Token> out = new ArrayList<>();
        StringBuilder text = new StringBuilder();
        int line = 1;
        int textLine = 1;
        int i = 0;
        int n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (s.startsWith("{{--", i)) {
                int e = find(s, "--}}", i + 4, line);
                line += newlines(s, i, e);
                i = e + 4;
            } else if (s.startsWith("@{{", i)) {
                int e = s.indexOf("}}", i + 3);
                if (e < 0) {
                    text.append(c);
                    i++;
                } else {
                    text.append(s, i + 1, e + 2);
                    line += newlines(s, i, e);
                    i = e + 2;
                }
            } else if (s.startsWith("{!!", i)) {
                int e = findClose(s, "!!}", i + 3, line);
                flush(out, text, textLine);
                out.add(new Token(Kind.RAW, s.substring(i + 3, e).trim(), null, line));
                line += newlines(s, i, e);
                i = e + 3;
            } else if (s.startsWith("{{", i)) {
                int e = findClose(s, "}}", i + 2, line);
                flush(out, text, textLine);
                out.add(new Token(Kind.ECHO, s.substring(i + 2, e).trim(), null, line));
                line += newlines(s, i, e);
                i = e + 2;
            } else if (c == '<' && (s.startsWith("<x-", i) || s.startsWith("</x-", i)) && componentTag(s, i) > 0) {
                int end = componentTag(s, i);
                boolean closing = s.startsWith("</", i);
                int nameStart = i + (closing ? 4 : 3);
                int nameEnd = nameStart;
                while (nameEnd < end && isNameChar(s.charAt(nameEnd))) nameEnd++;
                String name = s.substring(nameStart, nameEnd);
                String attrs = s.substring(nameEnd, end).trim();
                Kind kind = closing ? Kind.COMPONENT_CLOSE : Kind.COMPONENT_OPEN;
                if (!closing && attrs.endsWith("/")) {
                    kind = Kind.COMPONENT_SELF;
                    attrs = attrs.substring(0, attrs.length() - 1).trim();
                }
                flush(out, text, textLine);
                out.add(new Token(kind, name, attrs, line));
                line += newlines(s, i, end);
                i = end + 1;
            } else if (c == '@' && s.startsWith("@@", i)) {
                text.append('@');
                i += 2;
            } else if (c == '@') {
                int j = i + 1;
                while (j < n && Character.isLetter(s.charAt(j))) j++;
                String name = s.substring(i + 1, j);
                if (looksLikeEmailOrIdentifier(s, j)) {
                    text.append(c);
                    i++;
                } else if (name.equals("verbatim")) {
                    int e = s.indexOf("@endverbatim", j);
                    if (e < 0) throw new LoomaException("Unclosed @verbatim", line, null);
                    text.append(s, j, e);
                    line += newlines(s, i, e);
                    i = e + "@endverbatim".length();
                } else if (registry.syntax(name) == Syntax.NONE) {
                    flush(out, text, textLine);
                    out.add(new Token(Kind.DIRECTIVE, name, null, line));
                    i = j;
                } else if (registry.syntax(name) != null) {
                    // OPTIONAL: '(' must follow directly; REQUIRED: blanks may precede it.
                    boolean optional = registry.syntax(name) == Syntax.OPTIONAL;
                    int k = j;
                    while (!optional && k < n && (s.charAt(k) == ' ' || s.charAt(k) == '\t')) k++;
                    if (k < n && s.charAt(k) == '(') {
                        int close = matchParen(s, k, line);
                        flush(out, text, textLine);
                        out.add(new Token(Kind.DIRECTIVE, name, s.substring(k + 1, close).trim(), line));
                        line += newlines(s, i, close);
                        i = close + 1;
                    } else if (optional) {
                        flush(out, text, textLine);
                        out.add(new Token(Kind.DIRECTIVE, name, null, line));
                        i = j;
                    } else {
                        text.append(c);
                        i++;
                    }
                } else {
                    text.append(c);
                    i++;
                }
            } else {
                text.append(c);
                if (c == '\n') line++;
                i++;
            }
            if (text.length() == 0) textLine = line;
        }
        flush(out, text, textLine);
        return out;
    }

    /** True if the directive-like name is continued by {@code .word}, a digit or '_' (e.g. a@empty.com). */
    private static boolean looksLikeEmailOrIdentifier(String s, int end) {
        if (end >= s.length()) return false;
        char c = s.charAt(end);
        return Character.isDigit(c) || c == '_'
                || (c == '.' && end + 1 < s.length() && Character.isLetterOrDigit(s.charAt(end + 1)));
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-' || c == ':';
    }

    /** Index of the '>' ending a {@code <x-...>} tag starting at {@code start}, or -1 if it is not a tag. */
    private static int componentTag(String s, int start) {
        int i = start + (s.startsWith("</", start) ? 4 : 3);
        int nameStart = i;
        while (i < s.length() && isNameChar(s.charAt(i))) i++;
        if (i == nameStart || i >= s.length()) return -1;
        char c = s.charAt(i);
        if (!(Character.isWhitespace(c) || c == '>' || c == '/')) return -1;
        char quote = 0;
        for (; i < s.length(); i++) {
            c = s.charAt(i);
            if (quote != 0) {
                if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '>') {
                return i;
            }
        }
        return -1;
    }

    private static void flush(List<Token> out, StringBuilder text, int line) {
        if (text.length() > 0) {
            out.add(new Token(Kind.TEXT, text.toString(), null, line));
            text.setLength(0);
        }
    }

    /** Like {@link #find} but ignores terminators inside quoted strings, so {@code {{ '}}' }} works. */
    private static int findClose(String s, String end, int from, int line) {
        char quote = 0;
        for (int i = from; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == '\\') i++;
                else if (c == quote) quote = 0;
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (s.startsWith(end, i)) {
                return i;
            }
        }
        // Fall back to the first terminator so an unbalanced quote yields a normal expression error.
        return find(s, end, from, line);
    }

    private static int find(String s, String end, int from, int line) {
        int e = s.indexOf(end, from);
        if (e < 0) throw new LoomaException("Unclosed tag, expected '" + end + "'", line, null);
        return e;
    }

    private static int newlines(String s, int from, int to) {
        int count = 0;
        for (int i = from; i < to && i < s.length(); i++) if (s.charAt(i) == '\n') count++;
        return count;
    }

    /** Index of the ')' matching the '(' at {@code open}, skipping quoted strings. */
    private static int matchParen(String s, int open, int line) {
        int depth = 0;
        char quote = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
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
        throw new LoomaException("Unbalanced parentheses in directive", line, null);
    }
}
