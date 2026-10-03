package io.github.djamelkorei.looma.spring;

import io.github.djamelkorei.looma.LoomaEngine;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.web.servlet.View;

public class LoomaView implements View {

    private final LoomaEngine engine;
    private final String name;
    private final String contentType;

    public LoomaView(LoomaEngine engine, String name, String contentType) {
        this.engine = engine;
        this.name = name;
        this.contentType = contentType;
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
        String html = engine.render(name, model);
        response.getWriter().write(html);
    }
}
