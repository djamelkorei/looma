package io.github.djamelkorei.looma.helper;

import io.github.djamelkorei.looma.spi.TemplateHelper;
import io.github.djamelkorei.looma.spi.TemplateLocale;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * {@code Number} in templates: locale-aware formatting (uses the visitor's locale in a Spring app).
 *
 * <pre>
 * {{ Number.decimal($price, 2) }}       1,234.50   (fr: 1 234,50)
 * {{ Number.currency($price, 'EUR') }}  €1,234.50  (fr: 1 234,50 €)
 * {{ Number.percent(0.256, 1) }}        25.6%
 * {{ Number.compact(1234567) }}         1.2M
 * {{ Number.bytes(1536) }}              1.5 KB
 * &#64;foreach(Number.sequence(1, $pages) as $p) ... @endforeach
 * </pre>
 */
public final class NumberHelper implements TemplateHelper {

    @Override
    public String name() {
        return "Number";
    }

    /** {@link DecimalFormat} pattern, e.g. {@code '#,##0.00'}, with the current locale's symbols. */
    public String format(Number value, String pattern) {
        if (value == null) return "";
        Locale locale = TemplateLocale.current();
        return formatter("pattern", locale, pattern,
                () -> new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(locale))).format(value);
    }

    /** Grouped number with exactly {@code digits} decimals. */
    public String decimal(Number value, int digits) {
        if (value == null) return "";
        Locale locale = TemplateLocale.current();
        return formatter("decimal", locale, digits, () -> {
            NumberFormat f = NumberFormat.getNumberInstance(locale);
            f.setMinimumFractionDigits(digits);
            f.setMaximumFractionDigits(digits);
            return f;
        }).format(value);
    }

    /** Grouped whole number: 1234567 -> 1,234,567. */
    public String integer(Number value) {
        if (value == null) return "";
        Locale locale = TemplateLocale.current();
        return formatter("integer", locale, null, () -> NumberFormat.getIntegerInstance(locale)).format(value);
    }

    /** 0.256 -> 26% (or 25.6% with digits = 1). */
    public String percent(Number value, int digits) {
        if (value == null) return "";
        Locale locale = TemplateLocale.current();
        return formatter("percent", locale, digits, () -> {
            NumberFormat f = NumberFormat.getPercentInstance(locale);
            f.setMinimumFractionDigits(digits);
            f.setMaximumFractionDigits(digits);
            return f;
        }).format(value);
    }

    private record FormatKey(String kind, Locale locale, Object option) {}

    /** Configured formats per kind, locale and option; each use gets a copy, as formats are not thread-safe. */
    private static final Map<FormatKey, NumberFormat> FORMATS = new ConcurrentHashMap<>();

    /**
     * A fresh copy of the cached format: building one from the locale data costs far more than copying it, and a page
     * formats every amount and date with the same few formats.
     */
    private static NumberFormat formatter(String kind, Locale locale, Object option, Supplier<NumberFormat> factory) {
        FormatKey key = new FormatKey(kind, locale, option);
        NumberFormat prototype = FORMATS.get(key);
        if (prototype == null) {
            prototype = factory.get();
            if (FORMATS.size() > 1_000) FORMATS.clear(); // patterns written in templates are few; never grow unbounded
            FORMATS.put(key, prototype);
        }
        return (NumberFormat) prototype.clone();
    }

    public String percent(Number value) {
        return percent(value, 0);
    }

    /** Money in the given ISO currency, formatted for the current locale. */
    public String currency(Number value, String currencyCode) {
        if (value == null) return "";
        Locale locale = TemplateLocale.current();
        NumberFormat f = formatter("currency", locale, currencyCode, () -> {
            NumberFormat format = NumberFormat.getCurrencyInstance(locale);
            format.setCurrency(Currency.getInstance(currencyCode));
            return format;
        });
        return f.format(value instanceof BigDecimal ? value : value.doubleValue());
    }

    /** Short form: 1234 -> 1.2K, 1234567 -> 1.2M (localized). */
    public String compact(Number value) {
        if (value == null) return "";
        Locale locale = TemplateLocale.current();
        NumberFormat f = formatter("compact", locale, null, () -> {
            NumberFormat format = NumberFormat.getCompactNumberInstance(locale, NumberFormat.Style.SHORT);
            format.setMaximumFractionDigits(1);
            return format;
        });
        // CompactNumberFormat ignores the fraction digits for BigDecimal/BigInteger (1260500 -> "1M"); at this
        // scale a double is exact enough, and money is usually BigDecimal.
        boolean big = value instanceof BigDecimal || value instanceof java.math.BigInteger;
        return f.format(big ? value.doubleValue() : value);
    }

    /** File sizes in binary units: 1536 -> 1.5 KB. */
    public String bytes(Number value) {
        if (value == null) return "";
        double v = value.doubleValue();
        String[] units = {"B", "KB", "MB", "GB", "TB", "PB"};
        int u = 0;
        while (Math.abs(v) >= 1024 && u < units.length - 1) {
            v /= 1024;
            u++;
        }
        return (u == 0 ? integer((long) v) : decimal(v, 1)) + " " + units[u];
    }

    /** 1st, 2nd, 3rd, 4th... (English). */
    public String ordinal(Number value) {
        if (value == null) return "";
        long n = value.longValue();
        long mod100 = Math.abs(n) % 100;
        String suffix = (mod100 >= 11 && mod100 <= 13) ? "th" : switch ((int) (Math.abs(n) % 10)) {
            case 1 -> "st";
            case 2 -> "nd";
            case 3 -> "rd";
            default -> "th";
        };
        return n + suffix;
    }

    /** Whole numbers from {@code from} to {@code to}, inclusive (counts down if from > to). At most 10,000 items. */
    public List<Integer> sequence(int from, int to) {
        int size = Math.abs(to - from) + 1;
        if (size > 10_000) throw new IllegalArgumentException("Number.sequence: more than 10,000 items");
        List<Integer> list = new ArrayList<>(size);
        int step = from <= to ? 1 : -1;
        for (int i = from; ; i += step) {
            list.add(i);
            if (i == to) break;
        }
        return list;
    }
}
