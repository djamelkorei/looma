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
        return new DecimalFormat(pattern, DecimalFormatSymbols.getInstance(TemplateLocale.current())).format(value);
    }

    /** Grouped number with exactly {@code digits} decimals. */
    public String decimal(Number value, int digits) {
        if (value == null) return "";
        NumberFormat f = NumberFormat.getNumberInstance(TemplateLocale.current());
        f.setMinimumFractionDigits(digits);
        f.setMaximumFractionDigits(digits);
        return f.format(value);
    }

    /** Grouped whole number: 1234567 -> 1,234,567. */
    public String integer(Number value) {
        if (value == null) return "";
        return NumberFormat.getIntegerInstance(TemplateLocale.current()).format(value);
    }

    /** 0.256 -> 26% (or 25.6% with digits = 1). */
    public String percent(Number value, int digits) {
        if (value == null) return "";
        NumberFormat f = NumberFormat.getPercentInstance(TemplateLocale.current());
        f.setMinimumFractionDigits(digits);
        f.setMaximumFractionDigits(digits);
        return f.format(value);
    }

    public String percent(Number value) {
        return percent(value, 0);
    }

    /** Money in the given ISO currency, formatted for the current locale. */
    public String currency(Number value, String currencyCode) {
        if (value == null) return "";
        NumberFormat f = NumberFormat.getCurrencyInstance(TemplateLocale.current());
        f.setCurrency(Currency.getInstance(currencyCode));
        return f.format(value instanceof BigDecimal ? value : value.doubleValue());
    }

    /** Short form: 1234 -> 1.2K, 1234567 -> 1.2M (localized). */
    public String compact(Number value) {
        if (value == null) return "";
        NumberFormat f = NumberFormat.getCompactNumberInstance(TemplateLocale.current(), NumberFormat.Style.SHORT);
        f.setMaximumFractionDigits(1);
        return f.format(value);
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
