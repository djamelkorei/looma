package io.github.djamelkorei.looma.internal;

import io.github.djamelkorei.looma.LoomaException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Splits template source into text, echo and directive tokens. */
final class Lexer {

    enum Kind { TEXT, ECHO, RAW, DIRECTIVE }

    record Token(Kind kind, String text, String args, int line) {}

    private static final Set<String> TAKES_ARGS =
            Set.of("if", "elseif", "unless", "isset", "switch", "case", "foreach", "forelse", "include",
                    "extends", "section", "yield", "push", "prepend", "stack");
    private static final Set<String> NO_ARGS =
            Set.of("else", "endif", "endunless", "endisset", "endswitch", "break", "default", "auth", "endauth",
                    "guest", "endguest", "parent", "endpush", "endprepend", "endforeach", "empty", "endforelse", "endsection", "show");

    private Lexer() {}

    static List<Token> lex(String s) {
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
                int e = find(s, "!!}", i + 3, line);
                flush(out, text, textLine);
                out.add(new Token(Kind.RAW, s.substring(i + 3, e).trim(), null, line));
                line += newlines(s, i, e);
                i = e + 3;
            } else if (s.startsWith("{{", i)) {
                int e = find(s, "}}", i + 2, line);
                flush(out, text, textLine);
                out.add(new Token(Kind.ECHO, s.substring(i + 2, e).trim(), null, line));
                line += newlines(s, i, e);
                i = e + 2;
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
                } else if (NO_ARGS.contains(name)) {
                    flush(out, text, textLine);
                    out.add(new Token(Kind.DIRECTIVE, name, null, line));
                    i = j;
                } else if (TAKES_ARGS.contains(name)) {
                    int k = j;
                    while (k < n && (s.charAt(k) == ' ' || s.charAt(k) == '\t')) k++;
                    if (k < n && s.charAt(k) == '(') {
                        int close = matchParen(s, k, line);
                        flush(out, text, textLine);
                        out.add(new Token(Kind.DIRECTIVE, name, s.substring(k + 1, close).trim(), line));
                        line += newlines(s, i, close);
                        i = close + 1;
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

    private static void flush(List<Token> out, StringBuilder text, int line) {
        if (text.length() > 0) {
            out.add(new Token(Kind.TEXT, text.toString(), null, line));
            text.setLength(0);
        }
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
