package io.github.djamelkorei.looma.spring;

import io.github.djamelkorei.looma.LoomaEngine;
import io.github.djamelkorei.looma.spi.Authorizer;
import jakarta.servlet.http.HttpServletRequest;
import java.util.function.Function;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;

/** Resolves a view name to a Looma template, or returns null so other resolvers can try. */
public class LoomaViewResolver implements ViewResolver, Ordered {

    private final LoomaEngine engine;
    private final String contentType;
    private final MessageSource messages;
    private final Function<HttpServletRequest, Authorizer> authorizers;
    private int order = Ordered.LOWEST_PRECEDENCE - 10;

    public LoomaViewResolver(LoomaEngine engine, String contentType, MessageSource messages) {
        this(engine, contentType, messages, RequestAuthorizer::new);
    }

    public LoomaViewResolver(LoomaEngine engine, String contentType, MessageSource messages,
            Function<HttpServletRequest, Authorizer> authorizers) {
        this.authorizers = authorizers;
        this.engine = engine;
        this.contentType = contentType;
        this.messages = messages;
    }

    @Override
    public View resolveViewName(String viewName, Locale locale) {
        // "contacts :: rows" renders only @fragment('rows') of contacts (same syntax as Thymeleaf), e.g. for htmx
        String template = viewName;
        String fragment = null;
        int sep = viewName.indexOf("::");
        if (sep > 0) {
            template = viewName.substring(0, sep).trim();
            fragment = viewName.substring(sep + 2).trim();
        }
        return engine.exists(template)
                ? new LoomaView(engine, template, fragment, contentType, messages, authorizers)
                : null;
    }

    @Override
    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }
}
