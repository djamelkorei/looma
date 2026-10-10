package io.github.djamelkorei.looma.directive;

import io.github.djamelkorei.looma.LoomaException;
import io.github.djamelkorei.looma.spi.Directive;
import io.github.djamelkorei.looma.spi.Expr;
import io.github.djamelkorei.looma.spi.Node;
import io.github.djamelkorei.looma.spi.ParseContext;
import io.github.djamelkorei.looma.spi.RenderContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Includes that depend on a condition or on which templates exist:
 *
 * <pre>
 * &#64;includeIf('partials.promo')                     include only if the template exists
 * &#64;includeWhen($user.admin, 'partials.admin', {'x': 1})
 * &#64;includeUnless($cart.isEmpty(), 'partials.cart')
 * &#64;includeFirst({'themes.dark.nav', 'nav'})         the first template that exists
 * &#64;each('partials.row', $orders, 'order', 'partials.no-orders')   one include per item
 * </pre>
 *
 * Data maps use SpEL syntax ({@code {'key': value}}); template lists use {@code {'a', 'b'}}.
 */
public final class ConditionalIncludeDirectives {

    private ConditionalIncludeDirectives() {}

    public static List<Directive> all() {
        return List.of(includeIf(), includeWhen(true), includeWhen(false), includeFirst(), each());
    }

    /** Renders a template in a child scope, with optional extra variables. */
    static void include(RenderContext ctx, String template, Expr data) {
        RenderContext inner = ctx.child();
        if (data != null && ctx.eval(data) instanceof Map<?, ?> values) {
            inner.setAll(values);
        }
        inner.renderTemplate(template);
    }

    private static Directive includeIf() {
        return new Directive() {
            @Override public String name() { return "includeIf"; }
            @Override public Node parse(ParseContext p) {
                List<String> a = p.splitArgs();
                Expr template = p.expr(a.get(0));
                Expr data = a.size() > 1 ? p.expr(a.get(1)) : null;
                // no p.references(): a missing template is the expected case here
                return ctx -> {
                    String name = ctx.evalString(template);
                    if (ctx.templateExists(name)) include(ctx, name, data);
                };
            }
        };
    }

    private static Directive includeWhen(boolean when) {
        String directive = when ? "includeWhen" : "includeUnless";
        return new Directive() {
            @Override public String name() { return directive; }
            @Override public Node parse(ParseContext p) {
                List<String> a = p.splitArgs();
                if (a.size() < 2) throw p.error("Expected '@" + directive + "(condition, 'template', {data})'");
                Expr condition = p.expr(a.get(0));
                Expr template = p.expr(a.get(1));
                String fixed = p.literalOrNull(a.get(1));
                if (fixed != null) p.references(fixed);
                Expr data = a.size() > 2 ? p.expr(a.get(2)) : null;
                return ctx -> {
                    if (ctx.test(condition) == when) include(ctx, ctx.evalString(template), data);
                };
            }
        };
    }

    private static Directive includeFirst() {
        return new Directive() {
            @Override public String name() { return "includeFirst"; }
            @Override public Node parse(ParseContext p) {
                List<String> a = p.splitArgs();
                Expr candidates = p.expr(a.get(0));
                Expr data = a.size() > 1 ? p.expr(a.get(1)) : null;
                int line = p.line();
                return ctx -> {
                    List<String> names = new ArrayList<>();
                    for (Object o : Iterables.toList(ctx.eval(candidates))) names.add(String.valueOf(o));
                    for (String name : names) {
                        if (ctx.templateExists(name)) {
                            include(ctx, name, data);
                            return;
                        }
                    }
                    throw new LoomaException("@includeFirst: none of these templates exist: " + names, line, null);
                };
            }
        };
    }

    private static Directive each() {
        return new Directive() {
            @Override public String name() { return "each"; }
            @Override public Node parse(ParseContext p) {
                List<String> a = p.splitArgs();
                if (a.size() < 3) throw p.error("Expected '@each('template', $items, 'item', 'empty-template')'");
                Expr template = p.expr(a.get(0));
                Expr items = p.expr(a.get(1));
                String var = p.literal(a.get(2));
                Expr empty = a.size() > 3 ? p.expr(a.get(3)) : null;
                for (int i : new int[] {0, 3}) {
                    String fixed = i < a.size() ? p.literalOrNull(a.get(i)) : null;
                    if (fixed != null) p.references(fixed);
                }
                return ctx -> {
                    List<Object> list = Iterables.toList(ctx.eval(items));
                    if (list.isEmpty()) {
                        if (empty != null) include(ctx, ctx.evalString(empty), null);
                        return;
                    }
                    String name = ctx.evalString(template);
                    Loop parent = ctx.get("loop") instanceof Loop l ? l : null;
                    Loop loop = new Loop(0, list.size(), parent);
                    for (int i = 0; i < list.size(); i++) {
                        loop.index(i);
                        RenderContext inner = ctx.child();
                        inner.set(var, list.get(i));
                        inner.set("loop", loop);
                        inner.renderTemplate(name);
                    }
                };
            }
        };
    }
}
