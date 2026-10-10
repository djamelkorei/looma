package io.github.djamelkorei.looma.internal;

import io.github.djamelkorei.looma.Htmlable;
import io.github.djamelkorei.looma.LoomaException;
import io.github.djamelkorei.looma.Template;
import io.github.djamelkorei.looma.spi.Expr;
import io.github.djamelkorei.looma.spi.LayoutContribution;
import io.github.djamelkorei.looma.spi.Html;
import io.github.djamelkorei.looma.spi.Node;
import io.github.djamelkorei.looma.spi.RenderContext;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Renders one template (plus layout chain, includes and components) into a string.
 * Owns everything that is shared during a single render; not thread-safe, create one per render.
 */
public final class RenderSession {

    /** Upper bound for nested include/extends/component rendering; guards against runaway recursion. */
    static final int MAX_DEPTH = 100;

    private enum Signal { NONE, BREAK, CONTINUE }

    private final Function<String, Template> templates;
    private Map<Class<?>, Object> state;            // created on first use: most pages need none
    private List<UnaryOperator<String>> finishers;
    private StringBuilder out;
    private Signal signal = Signal.NONE;
    private int depth;

    private final Scope globals;

    public RenderSession(Function<String, Template> templates) {
        this(templates, 256, null);
    }

    /**
     * @param initialCapacity expected output size, so the buffer does not have to grow repeatedly
     * @param globals         helpers visible to every template, looked up after the model (may be null)
     */
    public RenderSession(Function<String, Template> templates, int initialCapacity, Scope globals) {
        this.templates = templates;
        this.out = new StringBuilder(initialCapacity);
        this.globals = globals;
    }

    public String render(Template template, Map<String, ?> model) {
        Scope scope = new Scope(globals, model); // reads the model in place, no copy; helpers come after it
        renderTemplate(template, scope);
        String result = out.toString();
        if (finishers != null) {
            for (UnaryOperator<String> finisher : finishers) {
                result = finisher.apply(result);
            }
        }
        return result;
    }

    private void renderTemplate(Template t, Scope scope) {
        if (++depth > MAX_DEPTH) {
            depth--;
            throw new LoomaException("Maximum nesting depth of " + MAX_DEPTH
                    + " exceeded while rendering '" + t.name() + "' (recursive include/extends/component?)")
                    .inTemplate(t.name());
        }
        try {
            Ctx ctx = new Ctx(scope, t.name());
            if (t.layout() == null) {
                ctx.render(t.nodes());
                return;
            }
            // A child template only contributes to its layout; the layout produces the output.
            for (Node n : t.nodes()) {
                if (n instanceof LayoutContribution c) c.contribute(ctx);
            }
            renderTemplate(templates.apply(ctx.evalString(t.layout())), scope);
        } catch (LoomaException e) {
            throw e.inTemplate(t.name());
        } finally {
            depth--;
        }
    }

    private final class Ctx implements RenderContext {

        private final Scope scope;
        private final String templateName;

        Ctx(Scope scope, String templateName) {
            this.scope = scope;
            this.templateName = templateName;
        }

        @Override public String templateName() { return templateName; }

        @Override public Object eval(Expr expr) { return Expressions.eval(expr, scope); }
        @Override public String evalString(Expr expr) { return Expressions.evalString(expr, scope); }
        @Override public boolean test(Expr expr) { return Expressions.truthy(eval(expr)); }
        @Override public boolean truthy(Object value) { return Expressions.truthy(value); }
        @Override public Object get(String variable) { return scope.get(variable); }
        @Override public void set(String variable, Object value) { scope.put(variable, value); }
        @Override public void setAll(Map<?, ?> variables) { scope.putAll(variables); }
        @Override public RenderContext child() { return new Ctx(new Scope(scope), templateName); }
        @Override public RenderContext isolated() { return new Ctx(new Scope(globals), templateName); } // helpers only

        @Override public void write(CharSequence text) { out.append(text); }

        @Override
        public void writeEscaped(Object value) {
            if (value == null) return;
            if (value instanceof String str) Html.escape(str, out);
            else if (value instanceof Integer i) out.append(i.intValue());   // digits only: nothing to escape
            else if (value instanceof Long l) out.append(l.longValue());
            else if (value instanceof Boolean b) out.append(b.booleanValue());
            else if (value instanceof Htmlable h) out.append(h.toHtml());
            else Html.escape(String.valueOf(value), out);
        }

        @Override
        public void render(List<Node> nodes) {
            for (Node node : nodes) {
                if (signal != Signal.NONE) return;
                node.render(this);
            }
        }

        @Override
        public String capture(List<Node> nodes) {
            StringBuilder saved = out;
            out = new StringBuilder();
            try {
                render(nodes);
                return out.toString();
            } finally {
                out = saved;
            }
        }

        @Override
        public void renderTemplate(String name) {
            RenderSession.this.renderTemplate(templates.apply(name), scope);
        }

        @Override
        public boolean runLoopBody(List<Node> body) {
            render(body);
            Signal s = signal;
            signal = Signal.NONE;
            return s != Signal.BREAK;
        }

        @Override
        public void runSwitchBody(List<Node> body) {
            render(body);
            if (signal == Signal.BREAK) signal = Signal.NONE;
        }

        @Override public void breakLoop() { signal = Signal.BREAK; }
        @Override public void continueLoop() { signal = Signal.CONTINUE; }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T state(Class<T> type, Supplier<T> initial) {
            if (state == null) state = new HashMap<>();
            return (T) state.computeIfAbsent(type, k -> initial.get());
        }

        @Override public void onFinish(UnaryOperator<String> finisher) {
            if (finishers == null) finishers = new ArrayList<>();
            finishers.add(finisher);
        }
    }
}
