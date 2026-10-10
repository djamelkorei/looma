package io.github.djamelkorei.looma.directive;

import io.github.djamelkorei.looma.LoomaException;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Map;
import java.util.Optional;

/**
 * A small JSON writer for {@code @json}. Output is safe to place inside a {@code <script>} element:
 * {@code < > & '} and the line separators U+2028/U+2029 are written as \\u escapes.
 */
final class Json {

    private static final int MAX_DEPTH = 64;

    private Json() {}

    static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb, 0);
        return sb.toString();
    }

    private static void write(Object v, StringBuilder sb, int depth) {
        if (depth > MAX_DEPTH) {
            throw new LoomaException("@json: data is nested deeper than " + MAX_DEPTH + " levels (cycle?)");
        }
        if (v == null) {
            sb.append("null");
        } else if (v instanceof Optional<?> o) {
            write(o.orElse(null), sb, depth);
        } else if (v instanceof Boolean b) {
            sb.append(b);
        } else if (v instanceof Number n) {
            double d = n.doubleValue();
            sb.append(Double.isNaN(d) || Double.isInfinite(d) ? "null" : n.toString());
        } else if (v instanceof CharSequence || v instanceof Character || v instanceof Enum<?>
                || v instanceof java.time.temporal.TemporalAccessor || v instanceof java.util.Date
                || v instanceof java.util.UUID) {
            quote(v instanceof Enum<?> e ? e.name() : v.toString(), sb);
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                quote(String.valueOf(e.getKey()), sb);
                sb.append(':');
                write(e.getValue(), sb, depth + 1);
            }
            sb.append('}');
        } else if (v instanceof Iterable<?> it) {
            sb.append('[');
            boolean first = true;
            for (Object o : it) {
                if (!first) sb.append(',');
                first = false;
                write(o, sb, depth + 1);
            }
            sb.append(']');
        } else if (v.getClass().isArray()) {
            sb.append('[');
            int len = java.lang.reflect.Array.getLength(v);
            for (int i = 0; i < len; i++) {
                if (i > 0) sb.append(',');
                write(java.lang.reflect.Array.get(v, i), sb, depth + 1);
            }
            sb.append(']');
        } else if (v instanceof Class<?> || v instanceof ClassLoader || v instanceof AccessibleObject) {
            quote(v.toString(), sb); // never walk reflective objects
        } else {
            bean(v, sb, depth);
        }
    }

    private static void bean(Object v, StringBuilder sb, int depth) {
        sb.append('{');
        boolean first = true;
        try {
            if (v.getClass().isRecord()) {
                for (RecordComponent c : v.getClass().getRecordComponents()) {
                    first = member(sb, first, c.getName(), c.getAccessor(), v, depth);
                }
            } else {
                BeanInfo info = Introspector.getBeanInfo(v.getClass(), Object.class);
                for (PropertyDescriptor p : info.getPropertyDescriptors()) {
                    if (p.getReadMethod() != null) {
                        first = member(sb, first, p.getName(), p.getReadMethod(), v, depth);
                    }
                }
            }
        } catch (IntrospectionException e) {
            throw new LoomaException("@json: cannot read " + v.getClass().getName() + ": " + e.getMessage(), 0, e);
        }
        if (first) { // no properties at all: fall back to the text form rather than an empty object
            sb.setLength(sb.length() - 1);
            quote(v.toString(), sb);
            return;
        }
        sb.append('}');
    }

    private static boolean member(StringBuilder sb, boolean first, String name, Method getter, Object owner, int depth) {
        try {
            getter.setAccessible(true);
            Object value = getter.invoke(owner);
            if (!first) sb.append(',');
            quote(name, sb);
            sb.append(':');
            write(value, sb, depth + 1);
            return false;
        } catch (InvocationTargetException | IllegalAccessException | RuntimeException e) {
            throw new LoomaException("@json: cannot read '" + name + "' of " + owner.getClass().getName(), 0, e);
        }
    }

    private static void quote(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '<', '>', '&', '\'', ' ', ' ' -> sb.append(String.format("\\u%04x", (int) c));
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }
}
