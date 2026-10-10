package io.github.djamelkorei.looma.spring;

import io.github.djamelkorei.looma.spi.Authorizer;
import jakarta.servlet.http.HttpServletRequest;

/** {@code @can} without Spring Security: authorities are servlet roles; per-object permissions are always denied. */
final class RequestAuthorizer implements Authorizer {

    private final HttpServletRequest request;

    RequestAuthorizer(HttpServletRequest request) {
        this.request = request;
    }

    @Override
    public boolean can(String authority) {
        return request.getUserPrincipal() != null && request.isUserInRole(authority);
    }

    @Override
    public boolean can(String permission, Object target) {
        return false;
    }
}
