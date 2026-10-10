package io.github.djamelkorei.looma.spring;

import io.github.djamelkorei.looma.spi.FormBinder;
import java.util.Map;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.validation.BindingResult;

/**
 * Field values for {@code @bind} forms, like Thymeleaf's {@code th:field}: from the model's {@link BindingResult}
 * when there is one (formatted by your converters, and the rejected input after a failed submit), otherwise from
 * the object's properties.
 */
final class SpringFormBinder implements FormBinder {

    private final Map<String, ?> model;

    SpringFormBinder(Map<String, ?> model) {
        this.model = model;
    }

    @Override
    public Object value(String objectName, Object target, String path) {
        if (model.get(BindingResult.MODEL_KEY_PREFIX + objectName) instanceof BindingResult result) {
            // Multi-value fields: getFieldValue() formats a List as one string ("web,mobile"), which would untick
            // every @checkbox / @option after a failed submit; the raw collection keeps each value checkable.
            Object raw = result.getRawFieldValue(path);
            if (raw instanceof java.util.Collection<?> || (raw != null && raw.getClass().isArray())) {
                return raw;
            }
            return result.getFieldValue(path);
        }
        if (target == null) return null;
        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(target);
        wrapper.setAutoGrowNestedPaths(false);
        try {
            return wrapper.isReadableProperty(path) ? wrapper.getPropertyValue(path) : null;
        } catch (org.springframework.beans.NullValueInNestedPathException e) {
            return null; // e.g. address is null: show an empty field
        }
    }
}
