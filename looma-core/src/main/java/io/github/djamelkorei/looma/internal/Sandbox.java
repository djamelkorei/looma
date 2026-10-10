package io.github.djamelkorei.looma.internal;

import java.lang.reflect.AccessibleObject;
import java.util.List;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.MethodExecutor;
import org.springframework.expression.MethodResolver;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.support.DataBindingMethodResolver;
import org.springframework.expression.spel.support.DataBindingPropertyAccessor;

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

    static final class Methods implements MethodResolver {
        private final MethodResolver delegate = DataBindingMethodResolver.forInstanceMethodInvocation();

        @Override
        public MethodExecutor resolve(EvaluationContext c, Object target, String name, List<TypeDescriptor> argTypes)
                throws AccessException {
            // Scope is the template's own variable table (#root / #this): calling put() on it would let a
            // template assign variables, which the language deliberately does not allow.
            if (deniedTarget(target) || deniedMember(name) || target instanceof Scope) {
                throw new AccessException("Method '" + name + "' is not allowed in templates");
            }
            return delegate.resolve(c, target, name, argTypes);
        }
    }
}
