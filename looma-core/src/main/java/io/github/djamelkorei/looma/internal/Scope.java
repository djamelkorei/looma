package io.github.djamelkorei.looma.internal;

import java.util.HashMap;
import java.util.Map;

/** Variable scope with parent lookup. Used as the SpEL root object. */
public final class Scope {

    private final Map<String, Object> vars = new HashMap<>();
    private final Scope parent;

    public Scope(Scope parent) {
        this.parent = parent;
    }

    public Object get(String name) {
        for (Scope s = this; s != null; s = s.parent) {
            if (s.vars.containsKey(name)) {
                return s.vars.get(name);
            }
        }
        return null;
    }

    public void put(String name, Object value) {
        vars.put(name, value);
    }

    public void putAll(Map<?, ?> values) {
        values.forEach((k, v) -> vars.put(String.valueOf(k), v));
    }
}
