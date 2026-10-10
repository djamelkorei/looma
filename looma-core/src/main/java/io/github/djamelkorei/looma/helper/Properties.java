package io.github.djamelkorei.looma.helper;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;

/** Reads a named property for helpers such as {@code List.sortBy}: map keys, then public getters; never reflection types. */
final class Properties {

    private Properties() {}

    static Object read(Object target, String name) {
        if (target == null) return null;
        if (target instanceof Map<?, ?> map) return map.get(name);
        if (name.equals("class") || target instanceof Class<?> || target instanceof ClassLoader
                || target instanceof AccessibleObject) {
            throw new IllegalArgumentException("Property '" + name + "' is not allowed in templates");
        }
        String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String prefix : new String[] {"get", "is"}) {
            try {
                Method m = target.getClass().getMethod(prefix + suffix);
                if (!Modifier.isStatic(m.getModifiers()) && m.getDeclaringClass() != Object.class) {
                    return m.invoke(target);
                }
            } catch (NoSuchMethodException ignored) {
                // try the next prefix
            } catch (ReflectiveOperationException e) {
                throw new IllegalArgumentException("Cannot read '" + name + "' of " + target.getClass().getSimpleName(), e);
            }
        }
        if (target.getClass().isRecord()) {
            try {
                return target.getClass().getMethod(name).invoke(target);
            } catch (ReflectiveOperationException ignored) {
                // fall through
            }
        }
        throw new IllegalArgumentException("No property '" + name + "' on " + target.getClass().getSimpleName());
    }
}
