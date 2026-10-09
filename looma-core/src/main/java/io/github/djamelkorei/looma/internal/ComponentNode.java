package io.github.djamelkorei.looma.internal;

import io.github.djamelkorei.looma.spi.Expr;
import io.github.djamelkorei.looma.spi.Node;
import io.github.djamelkorei.looma.spi.RenderContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code <x-name attr="v" :attr="expr">body</x-name>}. Renders {@code components/name} in an isolated
 * scope holding only the attributes, {@code slot}, named slots and {@code attributes}.
 */
record ComponentNode(String name, List<Attr> attrs, List<Node> body, Map<String, List<Node>> slots)
        implements Node {

    record Attr(String name, Expr value) {}

    @Override
    public void render(RenderContext ctx) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Attr a : attrs) {
            values.put(a.name(), ctx.eval(a.value()));
        }
        RenderContext inner = ctx.isolated();
        values.forEach((k, v) -> {
            inner.set(k, v);
            if (k.indexOf('-') >= 0) inner.set(camelCase(k), v);
        });
        // Slots are rendered in the caller's scope, then handed over as already-escaped markup.
        slots.forEach((slotName, nodes) -> inner.set(camelCase(slotName), new SafeHtml(ctx.capture(nodes))));
        inner.set("slot", new SafeHtml(ctx.capture(body)));
        inner.set("attributes", new ComponentAttributes(values));
        inner.renderTemplate("components." + name);
    }

    private static String camelCase(String s) {
        StringBuilder sb = new StringBuilder();
        boolean up = false;
        for (char ch : s.toCharArray()) {
            if (ch == '-') {
                up = true;
            } else {
                sb.append(up ? Character.toUpperCase(ch) : ch);
                up = false;
            }
        }
        return sb.toString();
    }
}
