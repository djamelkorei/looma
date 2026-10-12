package io.github.djamelkorei.looma.internal;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.MethodExecutor;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.support.DataBindingPropertyAccessor;
import org.springframework.expression.spel.support.ReflectiveMethodResolver;

/**
 * Blocks the reflection escape hatches that remain in {@code SimpleEvaluationContext}:
 * {@code obj.class}, {@code obj.getClass()} and any access on reflective or process types.
 */
final class Sandbox {

    private Sandbox() {}

    static boolean deniedTarget(Object target) {
        return target instanceof Class<?>
                || target instanceof ClassLoader
                || target instanceof AccessibleObject
                || target instanceof Runtime
                || target instanceof Process
                || target instanceof ProcessBuilder
                || target instanceof Thread
                || target instanceof Module
                || target instanceof java.lang.invoke.MethodHandle;
    }

    static boolean deniedMember(String name) {
        return name.equals("class") || name.equals("getClass");
    }

    static final class Properties implements PropertyAccessor {
        private final PropertyAccessor delegate = DataBindingPropertyAccessor.forReadOnlyAccess();

        @Override public Class<?>[] getSpecificTargetClasses() { return delegate.getSpecificTargetClasses(); }
        @Override public boolean canRead(EvaluationContext c, Object target, String name) throws AccessException {
            return !deniedTarget(target) && !deniedMember(name) && delegate.canRead(c, target, name);
        }
        @Override public TypedValue read(EvaluationContext c, Object target, String name) throws AccessException {
            // SpEL caches the accessor that served the first read of an expression node and later calls read()
            // directly, without canRead(). A loop can therefore warm the cache with a harmless object and then
            // hand a blocked one to the same node, so the check must be repeated here.
            if (deniedTarget(target) || deniedMember(name)) {
                throw new AccessException("Property '" + name + "' is not allowed in templates");
            }
            return delegate.read(c, target, name);
        }
        @Override public boolean canWrite(EvaluationContext c, Object target, String name) { return false; }
        @Override public void write(EvaluationContext c, Object target, String name, Object value) {
            throw new UnsupportedOperationException("Templates cannot modify objects");
        }
    }

    /**
     * The rules of {@code DataBindingMethodResolver} (instance methods only, nothing from Object or Class, nothing on
     * a class loader) plus the sandbox checks. It extends {@link ReflectiveMethodResolver} rather than wrapping one
     * because SpEL only reuses a resolved method executor across evaluations when its single resolver is a
     * {@code ReflectiveMethodResolver}; with a wrapper every {@code $p.status.name()} in a loop re-resolved the
     * method by reflection, which dominated the profile of a page with many calls.
     *
     * <p>A cached executor is reused only for the same target type and argument types, so a node warmed up with a
     * harmless object never serves a denied type ({@link #deniedTarget}), and denied names never reach the cache.
     */
    static final class Methods extends ReflectiveMethodResolver {

        /** A resolved method per target class, name and argument types; see {@link #resolve}. */
        private record Key(Class<?> target, String name, List<TypeDescriptor> argTypes) {}

        private static final int MAX_CACHED = 10_000;
        private final Map<Key, MethodExecutor> resolved = new ConcurrentHashMap<>();

        /**
         * SpEL remembers one resolved method per expression node and drops it as soon as the argument types differ
         * from the previous call. Real data flips between types all the time ({@code List.of} gives different
         * classes for different sizes, a value is sometimes null), and each flip meant a full reflective lookup
         * of the class's methods. The executors here are reusable, so they are kept per class, name and argument
         * types instead.
         */
        @Override
        public MethodExecutor resolve(EvaluationContext c, Object target, String name, List<TypeDescriptor> argTypes)
                throws AccessException {
            // Scope is the template's own variable table (#root / #this): calling put() on it would let a
            // template assign variables, which the language deliberately does not allow.
            if (deniedTarget(target) || deniedMember(name) || target instanceof Scope) {
                throw new AccessException("Method '" + name + "' is not allowed in templates");
            }
            Key key = new Key(target.getClass(), name, new ArrayList<>(argTypes)); // argument types can be null
            MethodExecutor executor = resolved.get(key);
            if (executor == null) {
                executor = super.resolve(c, target, name, argTypes);
                if (executor != null) {
                    if (resolved.size() >= MAX_CACHED) resolved.clear();
                    resolved.put(key, executor);
                }
            }
            return executor;
        }

        @Override
        protected boolean isCandidateForInvocation(Method method, Class<?> targetClass) {
            Class<?> declaring = method.getDeclaringClass();
            return !Modifier.isStatic(method.getModifiers()) && declaring != Object.class && declaring != Class.class
                    && !ClassLoader.class.isAssignableFrom(targetClass);
        }
    }
}
