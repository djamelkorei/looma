package io.github.djamelkorei.looma.helper;

import io.github.djamelkorei.looma.spi.TemplateHelper;
import io.github.djamelkorei.looma.spi.TemplateLocale;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalAccessor;

/**
 * {@code Date} in templates: formatting and comparisons for {@code java.time} values, {@code java.util.Date}
 * and epoch milliseconds. Month and day names follow the visitor's locale.
 *
 * <pre>
 * {{ Date.format($order.createdAt, 'd MMMM yyyy') }}   10 October 2026  (fr: 10 octobre 2026)
 * {{ Date.medium($order.createdAt) }}                  localized date (and time, if it has one)
 * {{ Date.relative($comment.postedAt) }}               3 hours ago / in 2 days
 * @if(Date.isPast($invoice.dueDate)) overdue @endif
 * </pre>
 */
public final class DateHelper implements TemplateHelper {

    private final ZoneId zone;

    public DateHelper() {
        this(ZoneId.systemDefault());
    }

    /** @param zone used for instants, {@code java.util.Date} and "now" */
    public DateHelper(ZoneId zone) {
        this.zone = zone;
    }

    @Override
    public String name() {
        return "Date";
    }

    /** {@link DateTimeFormatter} pattern such as {@code 'yyyy-MM-dd HH:mm'}, in the current locale. */
    public String format(Object value, String pattern) {
        TemporalAccessor t = temporal(value);
        return t == null ? "" : DateTimeFormatter.ofPattern(pattern, TemplateLocale.current()).format(t);
    }

    public String shortFormat(Object value) {
        return styled(value, FormatStyle.SHORT);
    }

    public String medium(Object value) {
        return styled(value, FormatStyle.MEDIUM);
    }

    public String longFormat(Object value) {
        return styled(value, FormatStyle.LONG);
    }

    /** ISO-8601, e.g. for {@code <time datetime="...">}. */
    public String iso(Object value) {
        TemporalAccessor t = temporal(value);
        if (t == null) return "";
        if (t instanceof LocalDate d) return d.toString();
        if (t instanceof LocalDateTime d) return d.toString();
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(t);
    }

    /** "just now", "5 minutes ago", "in 2 days"... (English). */
    public String relative(Object value) {
        TemporalAccessor t = temporal(value);
        if (t == null) return "";
        ZonedDateTime then = t instanceof LocalDate d ? d.atStartOfDay(zone) : (ZonedDateTime) t;
        long seconds = Duration.between(ZonedDateTime.now(zone), then).getSeconds();
        boolean future = seconds > 0;
        long s = Math.abs(seconds);
        if (s < 45) return "just now";
        String amount;
        if (s < 3600) amount = unit(Math.max(1, Math.round(s / 60.0)), "minute");
        else if (s < 86_400) amount = unit(Math.round(s / 3600.0), "hour");
        else if (s < 30 * 86_400L) amount = unit(Math.round(s / 86_400.0), "day");
        else if (s < 365 * 86_400L) amount = unit(Math.round(s / (30 * 86_400.0)), "month");
        else amount = unit(Math.round(s / (365 * 86_400.0)), "year");
        return future ? "in " + amount : amount + " ago";
    }

    public ZonedDateTime now() {
        return ZonedDateTime.now(zone);
    }

    public LocalDate today() {
        return LocalDate.now(zone);
    }

    /** Whole days from {@code a} to {@code b} (negative if b is earlier). */
    public long daysBetween(Object a, Object b) {
        return ChronoUnit.DAYS.between(date(a), date(b));
    }

    public boolean isToday(Object value) {
        return date(value).equals(today());
    }

    public boolean isPast(Object value) {
        return compareToNow(value) < 0;
    }

    public boolean isFuture(Object value) {
        return compareToNow(value) > 0;
    }

    /** Localized day name: "Saturday" (fr: "samedi"). */
    public String dayName(Object value) {
        return date(value).getDayOfWeek().getDisplayName(TextStyle.FULL, TemplateLocale.current());
    }

    /** Localized month name: "October" (fr: "octobre"). */
    public String monthName(Object value) {
        return date(value).getMonth().getDisplayName(TextStyle.FULL, TemplateLocale.current());
    }

    private String styled(Object value, FormatStyle style) {
        TemporalAccessor t = temporal(value);
        if (t == null) return "";
        DateTimeFormatter f = t instanceof LocalDate ? DateTimeFormatter.ofLocalizedDate(style)
                : t instanceof LocalTime ? DateTimeFormatter.ofLocalizedTime(style)
                : DateTimeFormatter.ofLocalizedDateTime(style == FormatStyle.LONG ? FormatStyle.LONG : style, FormatStyle.SHORT);
        return f.withLocale(TemplateLocale.current()).format(t);
    }

    private int compareToNow(Object value) {
        TemporalAccessor t = temporal(value);
        if (t instanceof LocalDate d) return d.compareTo(today());
        return ((ZonedDateTime) t).compareTo(now());
    }

    private LocalDate date(Object value) {
        TemporalAccessor t = temporal(value);
        if (t == null) throw new IllegalArgumentException("Date: value is null");
        return t instanceof LocalDate d ? d : ((ZonedDateTime) t).toLocalDate();
    }

    /** Normalizes to LocalDate, LocalTime or ZonedDateTime. */
    private TemporalAccessor temporal(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate || value instanceof LocalTime || value instanceof ZonedDateTime) return (TemporalAccessor) value;
        if (value instanceof LocalDateTime d) return d.atZone(zone);
        if (value instanceof Instant i) return i.atZone(zone);
        if (value instanceof java.time.OffsetDateTime o) return o.toZonedDateTime();
        if (value instanceof java.sql.Date d) return d.toLocalDate();
        if (value instanceof java.util.Date d) return d.toInstant().atZone(zone);
        if (value instanceof Number n) return Instant.ofEpochMilli(n.longValue()).atZone(zone);
        if (value instanceof Temporal t) return t;
        throw new IllegalArgumentException("Date: unsupported value of type " + value.getClass().getSimpleName());
    }

    private static String unit(long n, String unit) {
        return n + " " + unit + (n == 1 ? "" : "s");
    }
}
