package io.github.djamelkorei.looma;

/** CSRF token exposed to templates as the {@code _csrf} variable; rendered by {@code @csrf}. */
public record CsrfToken(String parameterName, String token) {
}
