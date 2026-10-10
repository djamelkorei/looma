package io.github.djamelkorei.looma.internal;

import java.util.Arrays;
import java.util.Map;

/**
 * Variable scope with parent lookup. Used as the SpEL root object.
 *
 * <p>Built for the render loop: local variables live in two small arrays (a loop scope holds two), and the root
 * scope reads the model map directly instead of copying it on every render. Never modified by templates.
 */
public final class Scope {

    private static final String[] NO_NAMES = new String[0];
    private static final Object[] NO_VALUES = new Object[0];

    private final Scope parent;
    private final Map<String, ?> base;
    private String[] names = NO_NAMES;
    private Object[] values = NO_VALUES;
    private int size;

    public Scope(Scope parent) {
        this(parent, null);
    }

    /** @param base read-only variables looked up after the local ones (the controller's model) */
    public Scope(Scope parent, Map<String, ?> base) {
        this.parent = parent;
        this.base = base;
    }

    public Object get(String name) {
        for (Scope s = this; s != null; s = s.parent) {
            for (int i = 0; i < s.size; i++) {
                if (name.equals(s.names[i])) return s.values[i];
            }
            if (s.base != null) {
                Object value = s.base.get(name);
                if (value != null || s.base.containsKey(name)) return value;
            }
        }
        return null;
    }

    public void put(String name, Object value) {
        for (int i = 0; i < size; i++) {
            if (name.equals(names[i])) {
                values[i] = value;
                return;
            }
        }
        if (size == names.length) {
            int capacity = Math.max(4, size * 2);
            names = Arrays.copyOf(names, capacity);
            values = Arrays.copyOf(values, capacity);
        }
        names[size] = name;
        values[size++] = value;
    }

    public void putAll(Map<?, ?> values) {
        values.forEach((k, v) -> put(String.valueOf(k), v));
    }
}
