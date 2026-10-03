package io.github.djamelkorei.looma.internal;

import io.github.djamelkorei.looma.Template;
import io.github.djamelkorei.looma.ast.Node;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Renders one template (plus layout chain and includes) into a string. Not thread-safe; one per render. */
public final class RenderSession {

    private final Function<String, Template> templates;
    private final Map<String, Node.Section> sections = new HashMap<>();
    private final StringBuilder out = new StringBuilder();

    public RenderSession(Function<String, Template> templates) {
        this.templates = templates;
    }

    public String render(Template template, Map<String, ?> model) {
        Scope scope = new Scope(null);
        if (model != null) scope.putAll(model);
        renderTemplate(template, scope);
        return out.toString();
    }

    private void renderTemplate(Template t, Scope scope) {
        Node.Extends ext = null;
        for (Node n : t.nodes()) {
            if (n instanceof Node.Extends e) ext = e;
        }
        if (ext == null) {
            renderNodes(t.nodes(), scope);
            return;
        }
        // Child sections win over those of ancestors, so the first definition is kept.
        for (Node n : t.nodes()) {
            if (n instanceof Node.Section s) sections.putIfAbsent(s.name(), s);
        }
        renderTemplate(templates.apply(Expressions.evalString(ext.name(), scope)), scope);
    }

    private void renderNodes(List<Node> nodes, Scope scope) {
        for (Node node : nodes) {
            switch (node) {
                case Node.Text t -> out.append(t.value());
                case Node.Echo e -> echo(e, scope);
                case Node.If i -> renderIf(i, scope);
                case Node.Switch sw -> renderSwitch(sw, scope);
                case Node.Foreach f -> renderForeach(f, scope);
                case Node.Include inc -> renderInclude(inc, scope);
                case Node.Section s -> renderNodes(sections.getOrDefault(s.name(), s).body(), scope);
                case Node.Yield y -> renderYield(y, scope);
                case Node.Extends ignored -> { }
            }
        }
    }

    private void echo(Node.Echo e, Scope scope) {
        Object v = Expressions.eval(e.expr(), scope);
        if (v == null) return;
        if (e.raw()) out.append(v);
        else Html.escape(String.valueOf(v), out);
    }

    private void renderIf(Node.If node, Scope scope) {
        for (Node.Branch b : node.branches()) {
            if (Expressions.truthy(Expressions.eval(b.test(), scope))) {
                renderNodes(b.body(), scope);
                return;
            }
        }
        renderNodes(node.orElse(), scope);
    }

    private void renderSwitch(Node.Switch sw, Scope scope) {
        Object subject = Expressions.eval(sw.subject(), scope);
        Node.Case fallback = null;
        for (Node.Case c : sw.cases()) {
            if (c.tests().isEmpty()) {
                fallback = c;
                continue;
            }
            for (var test : c.tests()) {
                if (looselyEqual(subject, Expressions.eval(test, scope))) {
                    renderNodes(c.body(), scope);
                    return;
                }
            }
        }
        if (fallback != null) renderNodes(fallback.body(), scope);
    }

    private static boolean looselyEqual(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) return x.doubleValue() == y.doubleValue();
        if (a instanceof Enum<?> e && b instanceof String s) return e.name().equals(s);
        if (a instanceof String s && b instanceof Enum<?> e) return e.name().equals(s);
        return java.util.Objects.equals(a, b);
    }

    private void renderForeach(Node.Foreach f, Scope scope) {
        List<Object> items = toList(Expressions.eval(f.items(), scope));
        if (items.isEmpty()) {
            renderNodes(f.empty(), scope);
            return;
        }
        Loop parent = scope.get("loop") instanceof Loop l ? l : null;
        Scope inner = new Scope(scope);
        for (int i = 0; i < items.size(); i++) {
            inner.put(f.var(), items.get(i));
            inner.put("loop", new Loop(i, items.size(), parent));
            renderNodes(f.body(), inner);
        }
    }

    private void renderInclude(Node.Include inc, Scope scope) {
        Scope inner = new Scope(scope);
        if (inc.data() != null && Expressions.eval(inc.data(), scope) instanceof Map<?, ?> data) {
            inner.putAll(data);
        }
        renderTemplate(templates.apply(Expressions.evalString(inc.name(), scope)), inner);
    }

    private void renderYield(Node.Yield y, Scope scope) {
        Node.Section s = sections.get(y.name());
        if (s != null) {
            renderNodes(s.body(), scope);
        } else if (y.fallback() != null) {
            Object v = Expressions.eval(y.fallback(), scope);
            if (v != null) Html.escape(String.valueOf(v), out);
        }
    }

    private static List<Object> toList(Object v) {
        List<Object> list = new ArrayList<>();
        if (v == null) {
            return list;
        } else if (v instanceof Iterable<?> it) {
            it.forEach(list::add);
        } else if (v instanceof Map<?, ?> m) {
            m.forEach((k, val) -> list.add(new MapEntry(k, val)));
        } else if (v.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(v);
            for (int i = 0; i < len; i++) list.add(java.lang.reflect.Array.get(v, i));
        } else {
            list.add(v);
        }
        return list;
    }
}
