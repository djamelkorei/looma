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
    private final Map<Class<?>, Object> state = new HashMap<>();
    private final List<UnaryOperator<String>> finishers = new ArrayList<>();
    private StringBuilder out = new StringBuilder();
    private Signal signal = Signal.NONE;
    private int depth;

    public RenderSession(Function<String, Template> templates) {
        this.templates = templates;
    }

    public String render(Template template, Map<String, ?> model) {
        Scope scope = new Scope(null);
        if (model != null) scope.putAll(model);
        renderTemplate(template, scope);
        String result = out.toString();
        for (UnaryOperator<String> finisher : finishers) {
            result = finisher.apply(result);
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
            Ctx ctx = new Ctx(scope);
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

        Ctx(Scope scope) {
            this.scope = scope;
        }

        @Override public Object eval(Expr expr) { return Expressions.eval(expr, scope); }
        @Override public String evalString(Expr expr) { return Expressions.evalString(expr, scope); }
        @Override public boolean test(Expr expr) { return Expressions.truthy(eval(expr)); }
        @Override public Object get(String variable) { return scope.get(variable); }
        @Override public void set(String variable, Object value) { scope.put(variable, value); }
        @Override public void setAll(Map<?, ?> variables) { scope.putAll(variables); }
        @Override public RenderContext child() { return new Ctx(new Scope(scope)); }
        @Override public RenderContext isolated() { return new Ctx(new Scope(null)); }

        @Override public void write(CharSequence text) { out.append(text); }

        @Override
        public void writeEscaped(Object value) {
            if (value == null) return;
            if (value instanceof Htmlable h) out.append(h.toHtml());
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
            return (T) state.computeIfAbsent(type, k -> initial.get());
        }

        @Override public void onFinish(UnaryOperator<String> finisher) { finishers.add(finisher); }
    }
}
