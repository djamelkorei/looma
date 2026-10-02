package io.github.djamelkorei.looma;

import io.github.djamelkorei.looma.internal.Parser;
import io.github.djamelkorei.looma.internal.RenderSession;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Entry point: loads, parses (and optionally caches) templates and renders them against a model. */
public final class LoomaEngine {

    private final TemplateLoader loader;
    private final boolean cache;
    private final Map<String, Template> cached = new ConcurrentHashMap<>();

    public LoomaEngine(TemplateLoader loader, boolean cache) {
        this.loader = loader;
        this.cache = cache;
    }

    public boolean exists(String name) {
        return loader.exists(name);
    }

    public Template template(String name) {
        return cache ? cached.computeIfAbsent(name, this::compile) : compile(name);
    }

    public String render(String name, Map<String, ?> model) {
        return new RenderSession(this::template).render(template(name), model);
    }

    public void render(String name, Map<String, ?> model, Writer writer) {
        String html = render(name, model);
        try {
            writer.write(html);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Template compile(String name) {
        String source = loader.load(name);
        try {
            return new Template(name, Parser.parse(source));
        } catch (LoomaException e) {
            throw new LoomaException("In template '" + name + "': " + e.getMessage(), 0, e);
        }
    }
}
