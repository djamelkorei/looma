package io.github.djamelkorei.looma;

import io.github.djamelkorei.looma.directive.BuiltinDirectives;
import io.github.djamelkorei.looma.internal.DirectiveRegistry;
import io.github.djamelkorei.looma.internal.Parser;
import io.github.djamelkorei.looma.internal.RenderSession;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import io.github.djamelkorei.looma.spi.Directive;
import java.util.ArrayList;
import java.util.List;
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
    private final DirectiveRegistry registry;
    private final Map<String, Template> cached = new ConcurrentHashMap<>();
    private final Set<String> known = ConcurrentHashMap.newKeySet();

    /** An engine with the built-in directives only. Use {@link #builder} to add your own. */
    public LoomaEngine(TemplateLoader loader, boolean cache) {
        this(loader, cache, List.of());
    }

    private LoomaEngine(TemplateLoader loader, boolean cache, List<Directive> custom) {
        this.loader = loader;
        this.cache = cache;
        List<Directive> all = new ArrayList<>(BuiltinDirectives.all());
        all.addAll(custom);
        this.registry = new DirectiveRegistry(all);
    }

    public static Builder builder(TemplateLoader loader) {
        return new Builder(loader);
    }

    public static final class Builder {
        private final TemplateLoader loader;
        private boolean cache = true;
        private final List<Directive> directives = new ArrayList<>();

        private Builder(TemplateLoader loader) {
            this.loader = loader;
        }

        /** Whether parsed templates are cached (default true). Turn off during development. */
        public Builder cache(boolean cache) {
            this.cache = cache;
            return this;
        }

        /** Adds a custom directive; its name must not clash with a built-in or another custom one. */
        public Builder directive(Directive directive) {
            directives.add(directive);
            return this;
        }

        public Builder directives(Iterable<? extends Directive> more) {
            more.forEach(directives::add);
            return this;
        }

        public LoomaEngine build() {
            return new LoomaEngine(loader, cache, List.copyOf(directives));
        }
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

    /**
     * Parses the given templates and returns every syntax error found, one per broken template; an empty
     * list means all are valid. Use it at startup or in CI to find mistakes before a request does.
     * Templates are parsed but not rendered, so errors that depend on data are not found.
     */
    public List<LoomaException> check(Iterable<String> names) {
        List<LoomaException> errors = new ArrayList<>();
        for (String name : names) {
            try {
                template(name);
            } catch (LoomaException e) {
                errors.add(e.template() != null ? e : e.inTemplate(name));
            }
        }
        return errors;
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
            Parser.Result parsed = Parser.parse(source, registry);
            return new Template(name, parsed.nodes(), parsed.layout());
        } catch (LoomaException e) {
            throw e.inTemplate(name);
        }
    }
}
