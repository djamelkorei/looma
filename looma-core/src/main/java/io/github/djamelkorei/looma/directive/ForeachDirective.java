package io.github.djamelkorei.looma.directive;

import io.github.djamelkorei.looma.spi.Directive;
import io.github.djamelkorei.looma.spi.Expr;
import io.github.djamelkorei.looma.spi.Node;
import io.github.djamelkorei.looma.spi.ParseContext;
import io.github.djamelkorei.looma.spi.ParseContext.Block;
import io.github.djamelkorei.looma.spi.RenderContext;
import io.github.djamelkorei.looma.spi.Syntax;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code @foreach($items as $item)} and {@code @forelse($items as $item) ... @empty ... @endforelse}.
 * Exposes {@code loop} (see {@link Loop}) inside the body.
 */
public final class ForeachDirective implements Directive {

    private static final Pattern ARGS = Pattern.compile("(?s)^(.*\\S)\\s+as\\s+\\$?(\\w+)$");

    private record ForeachNode(Expr items, String var, List<Node> body, List<Node> empty) implements Node {
        @Override
        public void render(RenderContext ctx) {
            List<Object> list = Iterables.toList(ctx.eval(items));
            if (list.isEmpty()) {
                ctx.render(empty);
                return;
            }
            Loop parent = ctx.get("loop") instanceof Loop l ? l : null;
            RenderContext inner = ctx.child();
            Loop loop = new Loop(0, list.size(), parent);
            inner.set("loop", loop);
            for (int i = 0; i < list.size(); i++) {
                loop.index(i);
                inner.set(var, list.get(i));
                if (!inner.runLoopBody(body)) return;
            }
        }
    }

    private final boolean forelse;

    private ForeachDirective(boolean forelse) {
        this.forelse = forelse;
    }

    public static ForeachDirective foreach() {
        return new ForeachDirective(false);
    }

    public static ForeachDirective forelse() {
        return new ForeachDirective(true);
    }

    @Override public String name() { return forelse ? "forelse" : "foreach"; }

    @Override
    public Map<String, Syntax> tags() {
        return forelse ? Map.of("empty", Syntax.NONE, "endforelse", Syntax.NONE) : Map.of("endforeach", Syntax.NONE);
    }

    @Override
    public Node parse(ParseContext p) {
        Matcher m = ARGS.matcher(p.args() == null ? "" : p.args());
        if (!m.matches()) {
            throw p.error("Expected '@" + name() + "(items as item)'");
        }
        Expr items = p.expr(m.group(1));
        Block body = p.within("loop", () -> forelse ? p.block("empty", "endforelse") : p.block("endforeach"));
        List<Node> empty = forelse && body.end().name().equals("empty") ? p.block("endforelse").nodes() : List.of();
        return new ForeachNode(items, m.group(2), body.nodes(), empty);
    }
}
