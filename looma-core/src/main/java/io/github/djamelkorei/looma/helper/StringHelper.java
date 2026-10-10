package io.github.djamelkorei.looma.helper;

import io.github.djamelkorei.looma.spi.TemplateHelper;
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
}
