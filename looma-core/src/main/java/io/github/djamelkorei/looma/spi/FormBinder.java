package io.github.djamelkorei.looma.spi;

/**
 * Supplies field values for {@code @field} / {@code @checkbox} / ... inside {@code @bind('form')}. Templates see it
 * as the {@code _formBinder} variable. The Spring starter reads Spring's {@code BindingResult}, so values are
 * formatted by your converters and a rejected input is shown back to the user; without one, Looma reads the
 * property from the object itself.
 */
@FunctionalInterface
public interface FormBinder {

    /**
     * @param objectName the name given to {@code @bind}, e.g. {@code "form"}
     * @param target     the model object with that name (may be null)
     * @param path       the field path, e.g. {@code "email"} or {@code "address.city"}
     */
    Object value(String objectName, Object target, String path);
}
