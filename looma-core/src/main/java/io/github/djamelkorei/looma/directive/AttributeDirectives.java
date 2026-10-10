package io.github.djamelkorei.looma.directive;

import io.github.djamelkorei.looma.spi.Directive;
import io.github.djamelkorei.looma.spi.Expr;
import io.github.djamelkorei.looma.spi.Html;
import io.github.djamelkorei.looma.spi.Node;
import io.github.djamelkorei.looma.spi.ParseContext;
import io.github.djamelkorei.looma.spi.RenderContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code @class(...)} and {@code @style(...)}: build an attribute from fixed and conditional parts.
 *
 * <pre>
 * &lt;li @class('nav', {'active': $current, 'disabled': !$enabled})&gt;   -&gt; class="nav active"
 * &lt;p @style('margin: 0', {'color: red': $error})&gt;                    -&gt; style="margin: 0; color: red;"
 * </pre>
 *
 * A string is always included, a map includes the keys whose value is truthy, a list is flattened.
 * If nothing is left the attribute is omitted entirely.
 */
public final class AttributeDirectives {

    private AttributeDirectives() {}

    public static Directive classDirective() {
        return attribute("class", " ", false);
    }

    public static Directive style() {
        return attribute("style", " ", true);
    }

    private static Directive attribute(String attribute, String separator, boolean semicolons) {
        return new Directive() {
            @Override public String name() { return attribute; }
            @Override public Node parse(ParseContext p) {
                List<Expr> parts = p.splitArgs().stream().map(p::expr).toList();
                return ctx -> {
                    List<String> values = new ArrayList<>();
                    for (Expr part : parts) {
                        collect(ctx, ctx.eval(part), values);
                    }
                    if (values.isEmpty()) return;
                    StringBuilder sb = new StringBuilder();
                    for (String v : values) {
                        String piece = semicolons && !v.endsWith(";") ? v + ";" : v;
                        if (sb.length() > 0) sb.append(separator);
                        sb.append(piece);
                    }
                    ctx.write(attribute + "=\"" + Html.escape(sb) + "\"");
                };
            }
        };
    }

    private static void collect(RenderContext ctx, Object value, List<String> out) {
        if (value == null || value instanceof Boolean) {
            return;
        } else if (value instanceof Map<?, ?> map) {
            map.forEach((k, v) -> {
                if (ctx.truthy(v)) add(out, String.valueOf(k));
            });
        } else if (value instanceof Iterable<?> items) {
            items.forEach(item -> collect(ctx, item, out));
        } else if (value.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < len; i++) collect(ctx, java.lang.reflect.Array.get(value, i), out);
        } else {
            add(out, String.valueOf(value));
        }
    }

    private static void add(List<String> out, String value) {
        String trimmed = value.trim();
        if (!trimmed.isEmpty()) out.add(trimmed);
    }
}
