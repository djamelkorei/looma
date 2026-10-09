package io.github.djamelkorei.looma.internal;

import io.github.djamelkorei.looma.LoomaException;
import java.util.ArrayList;
import java.util.List;

/** Helpers for directive argument lists. */
final class Args {

    private Args() {}

    /** Splits on top-level commas, ignoring commas inside quotes, parentheses, brackets and braces. */
    static List<String> split(String args) {
        return split(args, ',');
    }

    /** Like {@link #split(String)} with a custom delimiter, e.g. ';' for {@code @for}. */
    static List<String> split(String args, char delimiter) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        char quote = 0;
        int start = 0;
        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);
            if (quote != 0) {
                if (c == '\\') i++;
                else if (c == quote) quote = 0;
            } else if (c == '\'' || c == '"') {
                quote = c;
            } else if (c == '(' || c == '[' || c == '{') {
                depth++;
            } else if (c == ')' || c == ']' || c == '}') {
                depth--;
            } else if (c == delimiter && depth == 0) {
                parts.add(args.substring(start, i).trim());
                start = i + 1;
            }
        }
        parts.add(args.substring(start).trim());
        return parts;
    }

    /** Unquotes a string literal such as {@code 'content'}. */
    static String literal(String arg, int line) {
        if (arg.length() >= 2) {
            char q = arg.charAt(0);
            if ((q == '\'' || q == '"') && arg.charAt(arg.length() - 1) == q) {
                return arg.substring(1, arg.length() - 1);
            }
        }
        throw new LoomaException("Expected a quoted string but found: " + arg, line, null);
    }
}
