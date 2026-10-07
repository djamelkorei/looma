package io.github.djamelkorei.looma;

import io.github.djamelkorei.looma.internal.Parser;
import io.github.djamelkorei.looma.internal.RenderSession;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Entry point: loads, parses (and optionally caches) templates and renders them against a model.
 * Instances are thread-safe; parsed templates are immutable and shared between concurrent renders.
 */
public final class LoomaEngine {

    private final TemplateLoader loader;
    private final boolean cache;
    private final Map<String, Template> cached = new ConcurrentHashMap<>();
    private final Set<String> known = ConcurrentHashMap.newKeySet();

    public LoomaEngine(TemplateLoader loader, boolean cache) {
        this.loader = loader;
        this.cache = cache;
    }

    /** Whether a template exists. With caching on, positive answers are remembered (this runs per request). */
    public boolean exists(String name) {
        if (cache && known.contains(name)) {
            return true;
        }
        boolean found = loader.exists(name);
        if (cache && found) {
            known.add(name);
        }
        return found;
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
            throw e.inTemplate(name);
        }
    }
}
