package io.github.djamelkorei.looma.helper;

import io.github.djamelkorei.looma.spi.TemplateHelper;

/**
 * {@code Math} in templates: the {@link java.lang.Math} functions views need. Whole numbers stay whole
 * ({@code Math.max(2, 5)} is {@code 5}, not {@code 5.0}).
 *
 * <pre>
 * {{ Math.max($a, $b) }}   {{ Math.round($rating) }}   {{ Math.ceil($items.size() / 10.0) }} pages
 * </pre>
 */
public final class MathHelper implements TemplateHelper {

    @Override
    public String name() {
        return "Math";
    }

    public Number max(Number a, Number b) {
        return whole(a) && whole(b) ? (Number) Math.max(a.longValue(), b.longValue()) : Math.max(a.doubleValue(), b.doubleValue());
    }

    public Number min(Number a, Number b) {
        return whole(a) && whole(b) ? (Number) Math.min(a.longValue(), b.longValue()) : Math.min(a.doubleValue(), b.doubleValue());
    }

    public Number abs(Number a) {
        return whole(a) ? (Number) Math.abs(a.longValue()) : Math.abs(a.doubleValue());
    }

    public long round(Number a) {
        return Math.round(a.doubleValue());
    }

    public long ceil(Number a) {
        return (long) Math.ceil(a.doubleValue());
    }

    public long floor(Number a) {
        return (long) Math.floor(a.doubleValue());
    }

    public double pow(Number base, Number exponent) {
        return Math.pow(base.doubleValue(), exponent.doubleValue());
    }

    public double sqrt(Number a) {
        return Math.sqrt(a.doubleValue());
    }

    private static boolean whole(Number n) {
        return n instanceof Integer || n instanceof Long || n instanceof Short || n instanceof Byte;
    }
}
