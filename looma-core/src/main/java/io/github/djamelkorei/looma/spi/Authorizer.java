package io.github.djamelkorei.looma.spi;

/**
 * Answers {@code @can} / {@code @cannot} / {@code @canany}. Templates see it as the {@code _authorizer}
 * variable; the Spring starter provides one per request (Spring Security authorities and PermissionEvaluator).
 * Without one, every check is denied.
 */
public interface Authorizer {

    /** Whether the current user has this authority, e.g. {@code "posts:edit"} or {@code "ROLE_ADMIN"}. */
    boolean can(String authority);

    /** Whether the current user has {@code permission} on {@code target}, e.g. {@code can("edit", post)}. */
    boolean can(String permission, Object target);

    /** Whether the current user has at least one of these authorities. */
    default boolean canAny(String... authorities) {
        for (String a : authorities) {
            if (can(a)) return true;
        }
        return false;
    }
}
