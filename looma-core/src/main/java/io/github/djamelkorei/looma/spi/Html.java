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
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
    }
}
