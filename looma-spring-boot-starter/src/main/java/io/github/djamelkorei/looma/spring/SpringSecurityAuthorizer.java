package io.github.djamelkorei.looma.spring;

import io.github.djamelkorei.looma.spi.Authorizer;
import jakarta.servlet.http.HttpServletRequest;
import java.util.function.Function;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.DenyAllPermissionEvaluator;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * {@code @can} with Spring Security: {@code @can('posts:edit')} checks the user's granted authorities (exact match;
 * roles are {@code ROLE_ADMIN}), {@code @can('edit', $post)} asks the application's {@link PermissionEvaluator}
 * bean, or denies like Spring Security's default when there is none. Anonymous users have no authorities.
 *
 * <p>Only loaded when Spring Security is on the classpath.
 */
final class SpringSecurityAuthorizer implements Authorizer {

    private final Authentication authentication;
    private final PermissionEvaluator permissions;

    private SpringSecurityAuthorizer(Authentication authentication, PermissionEvaluator permissions) {
        this.authentication = authentication;
        this.permissions = permissions;
    }

    static Function<HttpServletRequest, Authorizer> factory(ObjectProvider<PermissionEvaluator> evaluators) {
        return request -> new SpringSecurityAuthorizer(SecurityContextHolder.getContext().getAuthentication(),
                evaluators.getIfAvailable(DenyAllPermissionEvaluator::new));
    }

    private boolean signedIn() {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    @Override
    public boolean can(String authority) {
        if (!signedIn()) return false;
        for (GrantedAuthority granted : authentication.getAuthorities()) {
            if (authority.equals(granted.getAuthority())) return true;
        }
        return false;
    }

    @Override
    public boolean can(String permission, Object target) {
        return signedIn() && target != null && permissions.hasPermission(authentication, target, permission);
    }
}
