package io.github.djamelkorei.looma.spring;

import io.github.djamelkorei.looma.CsrfToken;
import io.github.djamelkorei.looma.LoomaEngine;
import io.github.djamelkorei.looma.spi.Authorizer;
import java.util.function.Function;
import io.github.djamelkorei.looma.MessageResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.support.RequestContextUtils;

public class LoomaView implements View {

    private final LoomaEngine engine;
    private final String name;
    private final String contentType;
    private final MessageSource messages;
    private final Function<HttpServletRequest, Authorizer> authorizers;

    public LoomaView(LoomaEngine engine, String name, String contentType, MessageSource messages) {
        this(engine, name, contentType, messages, RequestAuthorizer::new);
    }

    public LoomaView(LoomaEngine engine, String name, String contentType, MessageSource messages,
            Function<HttpServletRequest, Authorizer> authorizers) {
        this.authorizers = authorizers;
        this.engine = engine;
        this.name = name;
        this.contentType = contentType;
        this.messages = messages;
    }

    /**
     * Adds {@code request}, {@code principal}, {@code _csrf} and {@code _messages} (for {@code @lang}) to the model unless the controller
     * already set them. The CSRF token is read from the {@code _csrf} request attribute that Spring
     * Security's CsrfFilter publishes, without a compile-time dependency on Spring Security.
     */
    static Map<String, Object> withRequestData(Map<String, ?> model, HttpServletRequest request,
            MessageSource messages, Function<HttpServletRequest, Authorizer> authorizers) {
        Map<String, Object> all = new HashMap<>();
        if (messages != null) {
            Locale locale = RequestContextUtils.getLocale(request);
            all.put("_messages", (MessageResolver) (key, args) -> messages.getMessage(key, args, key, locale));
        }
        all.put("request", request);
        all.put("principal", request.getUserPrincipal());
        all.put("_authorizer", authorizers.apply(request)); // answers @can / @cannot / @canany
        all.put("_formBinder", new SpringFormBinder(model));  // values for @bind / @field (BindingResult-aware)
        Object csrf = request.getAttribute("_csrf");
        if (csrf != null) {
            BeanWrapper bean = new BeanWrapperImpl(csrf);
            all.put("_csrf", new CsrfToken(
                    String.valueOf(bean.getPropertyValue("parameterName")),
                    String.valueOf(bean.getPropertyValue("token"))));
        }
        all.putAll(model);
        all.putIfAbsent("errors", validationErrors(model, messages, RequestContextUtils.getLocale(request)));
        return all;
    }

    /**
     * Field name to messages, collected from every {@link BindingResult} in the model (what {@code @Valid}
     * puts there), resolved through the {@link MessageSource} for the request locale. Global errors are
     * listed under {@code _global}. Feeds {@code @error('field')}.
     */
    static Map<String, List<String>> validationErrors(Map<String, ?> model, MessageSource messages, Locale locale) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        for (Object value : model.values()) {
            if (value instanceof BindingResult result && result.hasErrors()) {
                for (FieldError e : result.getFieldErrors()) {
                    errors.computeIfAbsent(e.getField(), k -> new ArrayList<>()).add(resolve(e, messages, locale));
                }
                for (ObjectError e : result.getGlobalErrors()) {
                    errors.computeIfAbsent("_global", k -> new ArrayList<>()).add(resolve(e, messages, locale));
                }
            }
        }
        return errors;
    }

    private static String resolve(ObjectError error, MessageSource messages, Locale locale) {
        if (messages != null) {
            try {
                return messages.getMessage(error, locale);
            } catch (NoSuchMessageException ignored) {
                // fall through to the default message
            }
        }
        return error.getDefaultMessage() != null ? error.getDefaultMessage() : error.getCode();
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        response.setContentType(contentType);
        // Render fully before writing so a template error does not leave a half-sent page.
        // The request's locale drives @lang and the Number/Date helpers.
        String html = engine.render(name, withRequestData(model, request, messages, authorizers), RequestContextUtils.getLocale(request));
        response.getWriter().write(html);
    }
}
