package io.github.djamelkorei.looma.directive;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Turns whatever a loop is given into a list. */
final class Iterables {

    private Iterables() {}

    @SuppressWarnings("unchecked")
    static List<Object> toList(Object value) {
        if (value instanceof List<?> l && l instanceof java.util.RandomAccess) {
            return (List<Object>) l; // already indexable: no copy (templates cannot modify it)
        }
        List<Object> list = new ArrayList<>();
        if (value == null) {
            return list;
        } else if (value instanceof Iterable<?> it) {
            it.forEach(list::add);
        } else if (value instanceof Map<?, ?> m) {
            m.forEach((k, v) -> list.add(new MapEntry(k, v)));
        } else if (value.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < len; i++) list.add(java.lang.reflect.Array.get(value, i));
        } else {
            list.add(value);
        }
        return list;
    }
}
