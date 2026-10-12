package io.github.djamelkorei.looma.internal;

/**
 * The shortcut {@link Expressions} tries before SpEL. Plans come from {@link DirectEvaluator}, which evaluates the
 * expression shapes templates are made of directly; a plan answers {@link #MISS} whenever SpEL has to decide, and
 * the caller then evaluates the expression with SpEL, so results and error messages are identical either way.
 */
final class FastPath {

    static final Object MISS = new Object();

    @FunctionalInterface
    interface Plan {
        /** The value, or {@link #MISS} if SpEL must evaluate this expression instead. */
        Object eval(Scope scope);
    }

    private FastPath() {}

    /** A plan for {@code translated} (already in SpEL form), or null if it is not one of the supported shapes. */
    static Plan compile(String translated) {
        return DirectEvaluator.compile(translated);
    }
}
