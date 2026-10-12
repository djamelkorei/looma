package io.github.djamelkorei.looma.internal;

/**
 * The shortcut {@link Expressions} uses instead of SpEL when an expression could be compiled. Plans come from
 * {@link DirectEvaluator}, which evaluates the expression shapes templates are made of directly and lets SpEL evaluate
 * single steps it does not cover, so results and errors are the same as with SpEL alone and every step runs once.
 */
final class FastPath {

    @FunctionalInterface
    interface Plan {
        /** The value, or the exception SpEL would throw. */
        Object eval(Scope scope);
    }

    private FastPath() {}

    /** A plan for {@code translated} (already in SpEL form), or null if SpEL must evaluate it from the start. */
    static Plan compile(String translated) {
        return DirectEvaluator.compile(translated);
    }
}
