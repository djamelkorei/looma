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
    /** Largest output seen per template, used to size the next render's buffer. */
    private final Map<String, Integer> sizeHints = new ConcurrentHashMap<>();

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
        if (!cache) return compile(name);
        Template template = cached.get(name); // lock-free read on the hot path
        return template != null ? template : cached.computeIfAbsent(name, this::compile);
    }

    /**
     * Parses the given templates and returns every problem found: syntax errors, and literal
     * {@code @include} / {@code @extends} / {@code <x-...>} references to templates that do not exist.
     * An empty list means all are valid. Use it at startup or in CI to find mistakes before a request does.
     * Templates are parsed but not rendered, so errors that depend on data, or on dynamic template names, are not found.
     */
    public List<LoomaException> check(Iterable<String> names) {
        List<LoomaException> errors = new ArrayList<>();
        for (String name : names) {
            try {
                for (Template.Reference ref : template(name).references()) {
                    if (!loader.exists(ref.name())) {
                        errors.add(new LoomaException("References missing template '" + ref.name() + "'", ref.line(), null)
                                .inTemplate(name));
                    }
                }
            } catch (LoomaException e) {
                errors.add(e.template() != null ? e : e.inTemplate(name));
            }
        }
        return errors;
    }

    public String render(String name, Map<String, ?> model) {
        Template template = template(name);
        int hint = sizeHints.getOrDefault(name, 256);
        String html = new RenderSession(this::template, hint).render(template, model);
        if (html.length() > hint && cache) {
            sizeHints.merge(name, html.length(), Math::max);
        }
        return html;
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
            return new Template(name, parsed.nodes(), parsed.layout(), parsed.references());
        } catch (LoomaException e) {
            throw e.inTemplate(name);
        }
    }
}
