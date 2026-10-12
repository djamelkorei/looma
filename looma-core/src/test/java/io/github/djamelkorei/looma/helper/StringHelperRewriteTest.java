package io.github.djamelkorei.looma.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** The regex-free string helpers must give exactly what the regex versions gave, on awkward input too. */
class StringHelperRewriteTest {

    private static final List<String> SAMPLES = samples();

    private static List<String> samples() {
        List<String> base = List.of("", " ", "a", "  a  ", "Ada Lovelace", "ada\tlovelace", "a\u000Bb", "a\fb\rc\nd",
                "a  b   c", "\u0001", "\u0001a\u0001", "a b", "a b", "Élodie Ñúñez", "çà va?", "Crème Brûlée!",
                "--hello--world--", "___", "Hello, World", "x".repeat(3), "😀 smile 😀", "İstanbul", "ǅemal", "ﬁne",
                "Launch <beta>", "Write docs & FAQ", "one two three four five six seven eight nine ten eleven twelve",
                "trailing \t", "\t leading", "\u001F x \u001F", "émoji🙂mix", "Ａｂｃ", "1.5 / 2");
        List<String> all = new ArrayList<>(base);
        for (String a : base) all.add(a + " " + a);
        return all;
    }

    // the implementations before the rewrite
    private static String oldTruncateWords(CharSequence value, int words) {
        if (value == null) return "";
        String[] parts = value.toString().trim().split("\\s+");
        if (parts.length <= words) return value.toString().trim();
        return String.join(" ", Arrays.copyOf(parts, words)) + "…";
    }

    private static int oldWordCount(StringHelper h, CharSequence value) {
        return h.isBlank(value) ? 0 : value.toString().trim().split("\\s+").length;
    }

    private static String oldInitials(StringHelper h, CharSequence value) {
        if (h.isBlank(value)) return "";
        StringBuilder sb = new StringBuilder();
        for (String w : value.toString().trim().split("\\s+")) sb.appendCodePoint(Character.toUpperCase(w.codePointAt(0)));
        return sb.toString();
    }

    private static String oldSlug(CharSequence value) {
        if (value == null) return "";
        String ascii = Normalizer.normalize(value.toString(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
    }

    private static Object outcome(java.util.function.Supplier<Object> s) {
        try {
            return s.get();
        } catch (RuntimeException e) {
            return e.getClass();
        }
    }

    @Test
    void sameAsTheRegexVersions() {
        StringHelper h = new StringHelper();
        for (String s : SAMPLES) {
            for (int n = 0; n <= 13; n++) {
                int words = n;
                assertEquals(outcome(() -> oldTruncateWords(s, words)), outcome(() -> h.truncateWords(s, words)),
                        "truncateWords('" + s + "', " + n + ")");
            }
            assertEquals(outcome(() -> oldTruncateWords(s, -1)), outcome(() -> h.truncateWords(s, -1)), "negative");
            assertEquals(outcome(() -> oldWordCount(h, s)), outcome(() -> h.wordCount(s)), "wordCount('" + s + "')");
            assertEquals(outcome(() -> oldInitials(h, s)), outcome(() -> h.initials(s)), "initials('" + s + "')");
            assertEquals(oldSlug(s), h.slug(s), "slug('" + s + "')");
        }
        assertEquals(oldTruncateWords(null, 2), h.truncateWords(null, 2));
        assertEquals(oldSlug(null), h.slug(null));
    }
}
