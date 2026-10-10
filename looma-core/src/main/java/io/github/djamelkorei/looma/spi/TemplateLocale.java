package io.github.djamelkorei.looma.spi;

import java.util.Locale;

/**
 * The locale of the render in progress, for helpers and directives that format text (numbers, dates...).
 * The Spring starter uses the request's locale; plain {@code LoomaEngine.render(name, model)} uses the JVM default.
 */
public final class TemplateLocale {

    private static final ThreadLocal<Locale> CURRENT = new ThreadLocal<>();

    private TemplateLocale() {}

    /** The current render's locale, or the JVM default outside a render. */
    public static Locale current() {
        Locale locale = CURRENT.get();
        return locale != null ? locale : Locale.getDefault();
    }

    /** Engine-internal: sets the locale for the current thread and returns the previous one (to restore). */
    public static Locale enter(Locale locale) {
        Locale previous = CURRENT.get();
        CURRENT.set(locale);
        return previous;
    }

    /** Engine-internal: restores what {@link #enter} returned. */
    public static void exit(Locale previous) {
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }
}
