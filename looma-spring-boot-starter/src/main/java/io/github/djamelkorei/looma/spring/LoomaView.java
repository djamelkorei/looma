package io.github.djamelkorei.looma.spring;

import io.github.djamelkorei.looma.CsrfToken;
import io.github.djamelkorei.looma.LoomaEngine;
import io.github.djamelkorei.looma.MessageResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.context.MessageSource;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.support.RequestContextUtils;

public class LoomaView implements View {

    private final LoomaEngine engine;
    private final String name;
    private final String contentType;
    private final MessageSource messages;

    public LoomaView(LoomaEngine engine, String name, String contentType, MessageSource messages) {
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
            MessageSource messages) {
        Map<String, Object> all = new HashMap<>();
        if (messages != null) {
            Locale locale = RequestContextUtils.getLocale(request);
            all.put("_messages", (MessageResolver) (key, args) -> messages.getMessage(key, args, key, locale));
        }
        all.put("request", request);
        all.put("principal", request.getUserPrincipal());
        Object csrf = request.getAttribute("_csrf");
        if (csrf != null) {
            BeanWrapper bean = new BeanWrapperImpl(csrf);
            all.put("_csrf", new CsrfToken(
                    String.valueOf(bean.getPropertyValue("parameterName")),
                    String.valueOf(bean.getPropertyValue("token"))));
        }
        all.putAll(model);
        return all;
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
        String html = engine.render(name, withRequestData(model, request, messages));
        response.getWriter().write(html);
    }
}
