package io.github.djamelkorei.looma.internal;

import io.github.djamelkorei.looma.Template;
import io.github.djamelkorei.looma.ast.Node;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Renders one template (plus layout chain and includes) into a string. Not thread-safe; one per render. */
public final class RenderSession {

    private final Function<String, Template> templates;
    /** Section definitions per name; index 0 is the most-derived (child) definition. */
    private final Map<String, List<Node.Section>> sections = new HashMap<>();
    private final Map<String, List<String>> stacks = new HashMap<>();
    private final java.util.Set<String> stackNames = new java.util.LinkedHashSet<>();
    private final Deque<Chain> chains = new ArrayDeque<>();
    private StringBuilder out = new StringBuilder();

    private record Chain(List<Node.Section> sections, int index) {}

    private static String marker(String stack) {
        return "\u0000looma-stack:" + stack + "\u0000";
    }

    public RenderSession(Function<String, Template> templates) {
        this.templates = templates;
    }

    public String render(Template template, Map<String, ?> model) {
        Scope scope = new Scope(null);
        if (model != null) scope.putAll(model);
        renderTemplate(template, scope);
        String result = out.toString();
        for (String name : stackNames) {
            result = result.replace(marker(name), String.join("", stacks.getOrDefault(name, List.of())));
        }
        return result;
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
        // Child definitions are registered first, so they sit ahead of those of ancestor layouts.
        for (Node n : t.nodes()) {
            if (n instanceof Node.Section s) sections.computeIfAbsent(s.name(), k -> new ArrayList<>()).add(s);
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
                case Node.Section s -> {
                    List<Node.Section> chain = new ArrayList<>(sections.getOrDefault(s.name(), List.of()));
                    chain.add(s);
                    renderChain(chain, 0, scope);
                }
                case Node.Parent ignored -> {
                    Chain c = chains.peek();
                    if (c != null && c.index() + 1 < c.sections().size()) {
                        renderChain(c.sections(), c.index() + 1, scope);
                    }
                }
                case Node.Push p -> push(p, scope);
                case Node.Component c -> renderComponent(c, scope);
                case Node.Slot ignoredSlot -> { }
                case Node.Stack st -> {
                    stackNames.add(st.name());
                    out.append(marker(st.name()));
                }
                case Node.Yield y -> renderYield(y, scope);
                case Node.Extends ignored -> { }
            }
        }
    }

    private void echo(Node.Echo e, Scope scope) {
        Object v = Expressions.eval(e.expr(), scope);
        if (v == null) return;
        if (v instanceof io.github.djamelkorei.looma.Htmlable h) out.append(h.toHtml());
        else if (e.raw()) out.append(v);
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

    private void renderChain(List<Node.Section> chain, int index, Scope scope) {
        chains.push(new Chain(chain, index));
        try {
            renderNodes(chain.get(index).body(), scope);
        } finally {
            chains.pop();
        }
    }

    private void push(Node.Push p, Scope scope) {
        String rendered = capture(p.body(), scope);
        List<String> stack = stacks.computeIfAbsent(p.name(), k -> new ArrayList<>());
        if (p.prepend()) stack.add(0, rendered);
        else stack.add(rendered);
    }

    /** Renders nodes into a separate buffer and returns it. */
    private String capture(List<Node> nodes, Scope scope) {
        StringBuilder saved = out;
        out = new StringBuilder();
        try {
            renderNodes(nodes, scope);
            return out.toString();
        } finally {
            out = saved;
        }
    }

    /** Components get an isolated scope: only their attributes, {@code slot}, named slots and {@code attributes}. */
    private void renderComponent(Node.Component c, Scope scope) {
        Map<String, Object> attrs = new java.util.LinkedHashMap<>();
        for (Node.Attr a : c.attrs()) {
            attrs.put(a.name(), Expressions.eval(a.value(), scope));
        }
        Scope inner = new Scope(null);
        attrs.forEach((k, v) -> {
            inner.put(k, v);
            if (k.indexOf('-') >= 0) inner.put(camelCase(k), v);
        });
        c.slots().forEach((name, body) -> inner.put(camelCase(name), new SafeHtml(capture(body, scope))));
        inner.put("slot", new SafeHtml(capture(c.body(), scope)));
        inner.put("attributes", new ComponentAttributes(attrs));
        renderTemplate(templates.apply("components." + c.name()), inner);
    }

    private static String camelCase(String s) {
        StringBuilder sb = new StringBuilder();
        boolean up = false;
        for (char ch : s.toCharArray()) {
            if (ch == '-') up = true;
            else {
                sb.append(up ? Character.toUpperCase(ch) : ch);
                up = false;
            }
        }
        return sb.toString();
    }

    private void renderYield(Node.Yield y, Scope scope) {
        List<Node.Section> chain = sections.get(y.name());
        if (chain != null) {
            renderChain(chain, 0, scope);
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
