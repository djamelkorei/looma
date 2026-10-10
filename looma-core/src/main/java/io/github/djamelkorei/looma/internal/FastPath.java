package io.github.djamelkorei.looma.internal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.util.ClassUtils;

/**
 * Evaluates the expression shapes templates use most ({@code user.name}, {@code !user.active},
 * {@code loop.even ? 'e' : 'o'}) without going through SpEL, which spends most of its time building type
 * descriptors for each property read.
 *
 * <p>A plan only returns a value when every step succeeds under exactly the rules SpEL and the {@link Sandbox}
 * would apply: map keys first, then public {@code getX()} / {@code isX()} getters, nothing on blocked types or
 * members. In every other case (null in the middle of a path, no getter, a getter that throws, a non-boolean
 * condition...) it returns {@link #MISS} and the caller evaluates the expression with SpEL, so results and error
 * messages are identical either way.
 */
final class FastPath {

    static final Object MISS = new Object();

    @FunctionalInterface
    interface Plan {
        /** The value, or {@link #MISS} if SpEL must evaluate this expression instead. */
        Object eval(Scope scope);
    }

    private static final String ID = "[A-Za-z_][A-Za-z0-9_]*";
    private static final String PATH = ID + "(?:\\." + ID + ")*";
    private static final String STR = "'[^'\\\\]*'|\"[^\"\\\\]*\"";
    private static final Pattern PATH_ONLY = Pattern.compile(PATH);
    private static final Pattern NOT = Pattern.compile("!\\s*(" + PATH + ")");
    private static final Pattern TERNARY = Pattern.compile("(" + PATH + ")\\s*\\?\\s*(" + STR + ")\\s*:\\s*(" + STR + ")");

    /** Words SpEL gives a meaning of their own (compared case-insensitively, as SpEL does). */
    private static final Set<String> KEYWORDS = Set.of("true", "false", "null", "and", "or", "not", "div", "mod",
            "lt", "gt", "le", "ge", "eq", "ne", "instanceof", "matches", "between", "new", "t");

    /** Getter handles per class and property; ClassValue lets classes (and their loaders) be unloaded. */
    private static final ClassValue<Map<String, Optional<MethodHandle>>> GETTERS = new ClassValue<>() {
        @Override protected Map<String, Optional<MethodHandle>> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };
    private static final MethodType OBJECT_TO_OBJECT = MethodType.methodType(Object.class, Object.class);

    private FastPath() {}

    /** A plan for {@code translated} (already in SpEL form), or null if it is not one of the supported shapes. */
    static Plan compile(String translated) {
        String src = translated.trim();
        if (PATH_ONLY.matcher(src).matches()) {
            String[] path = segments(src);
            return path == null ? null : scope -> path(path, scope);
        }
        Matcher m = NOT.matcher(src);
        if (m.matches()) {
            String[] path = segments(m.group(1));
            return path == null ? null : scope -> path(path, scope) instanceof Boolean b ? !b : MISS;
        }
        m = TERNARY.matcher(src);
        if (m.matches()) {
            String[] path = segments(m.group(1));
            String yes = unquote(m.group(2));
            String no = unquote(m.group(3));
            return path == null ? null : scope -> path(path, scope) instanceof Boolean b ? (b ? yes : no) : MISS;
        }
        return null;
    }

    private static String[] segments(String path) {
        String[] parts = path.split("\\.");
        for (String p : parts) {
            if (KEYWORDS.contains(p.toLowerCase(Locale.ROOT))) return null;
        }
        return parts;
    }

    private static String unquote(String literal) {
        return literal.substring(1, literal.length() - 1);
    }

    private static Object path(String[] path, Scope scope) {
        Object value = scope.get(path[0]); // the root of every expression is the Scope (see Accessors.ScopeAccessor)
        for (int i = 1; i < path.length; i++) {
            if (value == null) return MISS; // SpEL reports the null dereference
            value = read(value, path[i]);
            if (value == MISS) return MISS;
        }
        return value;
    }

    private static Object read(Object target, String name) {
        if (target instanceof Map<?, ?> map) { // SpEL tries the Map accessor first, and only for present keys
            return map.containsKey(name) ? map.get(name) : MISS;
        }
        if (Sandbox.deniedTarget(target) || Sandbox.deniedMember(name)) {
            return MISS; // SpEL raises the sandbox error
        }
        Class<?> type = target.getClass();
        Map<String, Optional<MethodHandle>> byName = GETTERS.get(type);
        Optional<MethodHandle> getter = byName.get(name);
        if (getter == null) { // looked up once per class and property, misses included
            getter = Optional.ofNullable(handle(find(type, name)));
            byName.putIfAbsent(name, getter);
        }
        if (getter.isEmpty()) return MISS;
        try {
            return (Object) getter.get().invokeExact(target);
        } catch (Throwable e) {
            if (e instanceof Error err && !(e instanceof LinkageError)) throw err; // never swallow OOM and friends
            return MISS; // let SpEL produce its usual error for a failing getter
        }
    }

    /** A publicly callable {@code getX()} or boolean {@code isX()} as SpEL's data-binding accessor would use. */
    private static Method find(Class<?> type, String name) {
        String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        Method m = publicNoArg(type, "get" + suffix);
        if (m == null) {
            m = publicNoArg(type, "is" + suffix);
            if (m != null && m.getReturnType() != boolean.class && m.getReturnType() != Boolean.class) m = null;
        }
        if (m == null || Modifier.isStatic(m.getModifiers()) || m.getDeclaringClass() == Object.class
                || m.getReturnType() == void.class) {
            return null;
        }
        if (callable(m)) return m;
        Method viaInterface = ClassUtils.getInterfaceMethodIfPossible(m, type);
        return viaInterface != m && callable(viaInterface) ? viaInterface : null;
    }

    private static Method publicNoArg(Class<?> type, String name) {
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static MethodHandle handle(Method m) {
        if (m == null) return null;
        try {
            return MethodHandles.publicLookup().unreflect(m).asType(OBJECT_TO_OBJECT);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    private static boolean callable(Method m) {
        try {
            MethodHandles.publicLookup().unreflect(m);
            return true;
        } catch (IllegalAccessException e) {
            return false;
        }
    }
}
