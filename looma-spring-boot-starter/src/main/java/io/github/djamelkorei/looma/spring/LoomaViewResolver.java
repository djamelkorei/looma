package io.github.djamelkorei.looma.spring;

import io.github.djamelkorei.looma.LoomaEngine;
import java.util.Locale;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;

/** Resolves a view name to a Looma template, or returns null so other resolvers can try. */
public class LoomaViewResolver implements ViewResolver, Ordered {

    private final LoomaEngine engine;
    private final String contentType;
    private int order = Ordered.LOWEST_PRECEDENCE - 10;

    public LoomaViewResolver(LoomaEngine engine, String contentType) {
        this.engine = engine;
        this.contentType = contentType;
    }

    @Override
    public View resolveViewName(String viewName, Locale locale) {
        return engine.exists(viewName) ? new LoomaView(engine, viewName, contentType) : null;
    }

    @Override
    public int getOrder() {
        return order;
    }

    public void setOrder(int order) {
        this.order = order;
    }
}
