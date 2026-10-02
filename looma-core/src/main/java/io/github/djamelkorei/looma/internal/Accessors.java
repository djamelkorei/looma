package io.github.djamelkorei.looma.internal;

import java.util.Map;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;

/** SpEL property accessors for {@link Scope} (root) and {@link Map}. */
final class Accessors {

    private Accessors() {}

    static final class ScopeAccessor implements PropertyAccessor {
        @Override public Class<?>[] getSpecificTargetClasses() { return new Class<?>[] {Scope.class}; }
        @Override public boolean canRead(EvaluationContext c, Object target, String name) { return true; }
        @Override public TypedValue read(EvaluationContext c, Object target, String name) throws AccessException {
            return new TypedValue(((Scope) target).get(name));
        }
        @Override public boolean canWrite(EvaluationContext c, Object target, String name) { return false; }
        @Override public void write(EvaluationContext c, Object target, String name, Object value) {
            throw new UnsupportedOperationException("Templates cannot assign variables");
        }
    }

    static final class MapAccessor implements PropertyAccessor {
        @Override public Class<?>[] getSpecificTargetClasses() { return new Class<?>[] {Map.class}; }
        @Override public boolean canRead(EvaluationContext c, Object target, String name) {
            return ((Map<?, ?>) target).containsKey(name);
        }
        @Override public TypedValue read(EvaluationContext c, Object target, String name) {
            return new TypedValue(((Map<?, ?>) target).get(name));
        }
        @Override public boolean canWrite(EvaluationContext c, Object target, String name) { return false; }
        @Override public void write(EvaluationContext c, Object target, String name, Object value) {
            throw new UnsupportedOperationException("Templates cannot modify maps");
        }
    }
}
