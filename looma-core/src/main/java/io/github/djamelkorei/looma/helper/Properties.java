package io.github.djamelkorei.looma.helper;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Reads a named property for helpers such as {@code List.sortBy}: map keys, then public getters; never reflection types. */
final class Properties {

    private Properties() {}

    /** A getter ({@code getX} / {@code isX}) or a record component accessor. */
    private record Accessor(Method method, boolean recordComponent) {}

    private static final Object NONE = new Object();

    /** Accessor per class and property, misses included: sorting a list reads the same property of every element. */
    private static final ClassValue<Map<String, Object>> ACCESSORS = new ClassValue<>() {
        @Override protected Map<String, Object> computeValue(Class<?> type) { return new ConcurrentHashMap<>(); }
    };

    static Object read(Object target, String name) {
        if (target == null) return null;
        if (target instanceof Map<?, ?> map) return map.get(name);
        if (name.equals("class") || target instanceof Class<?> || target instanceof ClassLoader
                || target instanceof AccessibleObject) {
            throw new IllegalArgumentException("Property '" + name + "' is not allowed in templates");
        }
        Class<?> type = target.getClass();
        Map<String, Object> byName = ACCESSORS.get(type);
        Object found = byName.get(name);
        if (found == null) {
            found = find(type, name);
            byName.putIfAbsent(name, found);
        }
        if (found instanceof Accessor accessor) {
            try {
                return accessor.method().invoke(target);
            } catch (ReflectiveOperationException e) {
                if (!accessor.recordComponent()) {
                    throw new IllegalArgumentException("Cannot read '" + name + "' of " + type.getSimpleName(), e);
                }
                // a failing record accessor reads as a missing property, as before
            }
        }
        throw new IllegalArgumentException("No property '" + name + "' on " + type.getSimpleName());
    }

    private static Object find(Class<?> type, String name) {
        String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String prefix : new String[] {"get", "is"}) {
            try {
                Method m = type.getMethod(prefix + suffix);
                if (!Modifier.isStatic(m.getModifiers()) && m.getDeclaringClass() != Object.class) {
                    return new Accessor(m, false);
                }
            } catch (NoSuchMethodException ignored) {
                // try the next prefix
            }
        }
        if (type.isRecord()) {
            try {
                return new Accessor(type.getMethod(name), true);
            } catch (NoSuchMethodException ignored) {
                // fall through
            }
        }
        return NONE;
    }
}
