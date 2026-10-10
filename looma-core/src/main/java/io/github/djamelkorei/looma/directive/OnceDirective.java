package io.github.djamelkorei.looma.directive;

import io.github.djamelkorei.looma.spi.Directive;
import io.github.djamelkorei.looma.spi.Node;
import io.github.djamelkorei.looma.spi.ParseContext;
import io.github.djamelkorei.looma.spi.Syntax;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code @once ... @endonce}: renders its body the first time it is reached in a page and never again,
 * even inside a loop or a component rendered many times. Typical use: a component's {@code <style>} or script.
 */
public final class OnceDirective implements Directive {

    private static final class Seen {
        final Set<String> keys = new HashSet<>();
    }

    @Override public String name() { return "once"; }
    @Override public Syntax syntax() { return Syntax.NONE; }
    @Override public Map<String, Syntax> tags() { return Map.of("endonce", Syntax.NONE); }

    @Override
    public Node parse(ParseContext p) {
        List<Node> body = p.block("endonce").nodes();
        int index = p.index();
        return ctx -> {
            // Keyed by template and tag position, not by object identity: with caching off the template is re-parsed per use.
            if (ctx.state(Seen.class, Seen::new).keys.add(ctx.templateName() + "#" + index)) {
                ctx.render(body);
            }
        };
    }
}
