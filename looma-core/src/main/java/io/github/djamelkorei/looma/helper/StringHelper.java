package io.github.djamelkorei.looma.helper;

import io.github.djamelkorei.looma.Htmlable;
import io.github.djamelkorei.looma.spi.Html;
import io.github.djamelkorei.looma.spi.TemplateHelper;
import java.text.Normalizer;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * {@code String} in templates: the static methods of {@link java.lang.String} that are useful in a view, plus
 * null-safe checks. Methods on a string value ({@code $name.toUpperCase()}) work without any helper.
 *
 * <pre>
 * {{ String.format('%.2f €', $price) }}      {{ String.join(', ', $tags) }}      {{ String.valueOf($n) }}
 * </pre>
 */
public final class StringHelper implements TemplateHelper {

    @Override
    public String name() {
        return "String";
    }

    /** {@link String#format(String, Object...)}; uses the JVM's default locale, like Java. */
    public String format(String format, Object... args) {
        return String.format(format, args);
    }

    /** Like {@link #format(String, Object...)} with an explicit locale tag, e.g. {@code 'fr'} or {@code 'de-CH'}. */
    public String formatLocale(String languageTag, String format, Object... args) {
        return String.format(Locale.forLanguageTag(languageTag), format, args);
    }

    /** Joins the items' text with a delimiter; {@code null} items become {@code "null"}, as in Java. */
    public String join(CharSequence delimiter, Iterable<?> items) {
        StringJoiner joiner = new StringJoiner(delimiter);
        if (items != null) items.forEach(item -> joiner.add(String.valueOf(item)));
        return joiner.toString();
    }

    public String join(CharSequence delimiter, Object... items) {
        StringJoiner joiner = new StringJoiner(delimiter);
        for (Object item : items) joiner.add(String.valueOf(item));
        return joiner.toString();
    }

    public String valueOf(Object value) {
        return String.valueOf(value);
    }

    /** True for {@code null} or {@code ""}. */
    public boolean isEmpty(CharSequence value) {
        return value == null || value.isEmpty();
    }

    /** True for {@code null}, {@code ""} or whitespace only. */
    public boolean isBlank(CharSequence value) {
        return value == null || value.toString().isBlank();
    }

    /** {@code null}-safe default: {@code d} when {@code value} is null or empty. */
    public String defaultIfEmpty(CharSequence value, String d) {
        return isEmpty(value) ? d : value.toString();
    }

    /** {@code null}-safe default: {@code d} when {@code value} is null, empty or whitespace. */
    public String defaultIfBlank(CharSequence value, String d) {
        return isBlank(value) ? d : value.toString();
    }

    /** Shortens to at most {@code max} characters, ending with "…" when cut. */
    public String abbreviate(CharSequence value, int max) {
        if (value == null) return "";
        String s = value.toString();
        if (s.length() <= max) return s;
        return max <= 1 ? "\u2026" : s.substring(0, max - 1).stripTrailing() + "\u2026";
    }

    /** Keeps the first {@code words} words, ending with "…" when cut. */
    public String truncateWords(CharSequence value, int words) {
        if (value == null) return "";
        String[] parts = value.toString().trim().split("\\s+");
        if (parts.length <= words) return value.toString().trim();
        return String.join(" ", java.util.Arrays.copyOf(parts, words)) + "\u2026";
    }

    public int wordCount(CharSequence value) {
        return isBlank(value) ? 0 : value.toString().trim().split("\\s+").length;
    }

    public String capitalize(CharSequence value) {
        if (isEmpty(value)) return "";
        String s = value.toString();
        return s.substring(0, 1).toUpperCase(io.github.djamelkorei.looma.spi.TemplateLocale.current()) + s.substring(1);
    }

    public String uncapitalize(CharSequence value) {
        if (isEmpty(value)) return "";
        String s = value.toString();
        return s.substring(0, 1).toLowerCase(io.github.djamelkorei.looma.spi.TemplateLocale.current()) + s.substring(1);
    }

    /** "Ada Lovelace" -> "AL". */
    public String initials(CharSequence value) {
        if (isBlank(value)) return "";
        StringBuilder sb = new StringBuilder();
        for (String w : value.toString().trim().split("\\s+")) sb.appendCodePoint(Character.toUpperCase(w.codePointAt(0)));
        return sb.toString();
    }

    /** Text before the first {@code separator}, or all of it if absent. */
    public String substringBefore(CharSequence value, String separator) {
        if (value == null) return "";
        String s = value.toString();
        int i = s.indexOf(separator);
        return i < 0 ? s : s.substring(0, i);
    }

    /** Text after the first {@code separator}, or "" if absent. */
    public String substringAfter(CharSequence value, String separator) {
        if (value == null) return "";
        String s = value.toString();
        int i = s.indexOf(separator);
        return i < 0 ? "" : s.substring(i + separator.length());
    }

    /** URL-friendly slug: "Héllo, World!" -> "hello-world". */
    public String slug(CharSequence value) {
        if (value == null) return "";
        String ascii = Normalizer.normalize(value.toString(), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return ascii.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+|-+$)", "");
    }

    /** "3 items" / "1 item": picks the word for the count and puts the count in front. */
    public String plural(Number count, String singular, String plural) {
        long n = count == null ? 0 : count.longValue();
        return n + " " + (n == 1 ? singular : plural);
    }

    /** Shows only the last {@code visible} characters: mask('4242424242424242', 4) -> "************4242". */
    public String mask(CharSequence value, int visible) {
        if (value == null) return "";
        String s = value.toString();
        int keep = Math.max(0, Math.min(visible, s.length()));
        return "*".repeat(s.length() - keep) + s.substring(s.length() - keep);
    }

    /** Escapes the text, then turns line breaks into {@code <br>}; safe to output with {@code {{ }}}. */
    public Htmlable nl2br(CharSequence value) {
        String escaped = value == null ? "" : Html.escape(value);
        String html = escaped.replace("\r\n", "\n").replace("\n", "<br>\n");
        return () -> html;
    }
}
