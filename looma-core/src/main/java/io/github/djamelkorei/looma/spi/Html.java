package io.github.djamelkorei.looma.spi;

/** HTML escaping for directive authors who write markup themselves. */
public final class Html {

    private Html() {}

    /** Returns {@code in} with {@code & < > " '} escaped. */
    public static String escape(CharSequence in) {
        StringBuilder out = new StringBuilder(in.length() + 16);
        escape(in, out);
        return out.toString();
    }

    public static void escape(CharSequence in, StringBuilder out) {
        // Copies untouched runs in bulk; most values contain nothing to escape.
        int start = 0;
        int length = in.length();
        for (int i = 0; i < length; i++) {
            String replacement;
            switch (in.charAt(i)) {
                case '&' -> replacement = "&amp;";
                case '<' -> replacement = "&lt;";
                case '>' -> replacement = "&gt;";
                case '"' -> replacement = "&quot;";
                case '\'' -> replacement = "&#39;";
                default -> replacement = null;
            }
            if (replacement != null) {
                out.append(in, start, i).append(replacement);
                start = i + 1;
            }
        }
        out.append(in, start, length);
    }
}
