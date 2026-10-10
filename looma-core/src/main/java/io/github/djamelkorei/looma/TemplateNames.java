package io.github.djamelkorei.looma;

import java.util.regex.Pattern;

/** Turns logical template names into relative paths, rejecting anything that could leave the template folder. */
final class TemplateNames {

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_\\-./]+");

    private TemplateNames() {}

    /** {@code layouts.app} or {@code layouts/app} -> {@code layouts/app}; null if the name is not acceptable. */
    static String toPath(String name) {
        if (name == null || !VALID.matcher(name).matches() || name.startsWith("/") || name.contains("..")) {
            return null;
        }
        return name.replace('.', '/');
    }
}
