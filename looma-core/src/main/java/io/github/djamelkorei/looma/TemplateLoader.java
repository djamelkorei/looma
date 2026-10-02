package io.github.djamelkorei.looma;

/** Locates template source by logical name, e.g. {@code layouts.app} or {@code layouts/app}. */
public interface TemplateLoader {

    boolean exists(String name);

    /** @throws LoomaException if the template cannot be found or read */
    String load(String name);
}
