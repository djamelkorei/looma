package io.github.djamelkorei.looma.directive;

import io.github.djamelkorei.looma.spi.Directive;
import io.github.djamelkorei.looma.spi.Expr;
import io.github.djamelkorei.looma.spi.Node;
import io.github.djamelkorei.looma.spi.ParseContext;
import io.github.djamelkorei.looma.spi.RenderContext;
import io.github.djamelkorei.looma.spi.Syntax;
import java.util.List;
import java.util.Map;

/** {@code @while($it.hasNext()) ... @endwhile}: the body must change the condition (e.g. {@code $it.next()}). */
public final class WhileDirective implements Directive {

    private record WhileNode(Expr condition, List<Node> body) implements Node {
        @Override
        public void render(RenderContext ctx) {
            for (int pass = 0; ctx.test(condition); pass++) {
                if (pass >= ForDirective.MAX_ITERATIONS) throw ForDirective.tooManyPasses("@while", condition);
                if (!ctx.runLoopBody(body)) return;
            }
        }
    }

    @Override public String name() { return "while"; }
    @Override public Map<String, Syntax> tags() { return Map.of("endwhile", Syntax.NONE); }

    @Override
    public Node parse(ParseContext p) {
        Expr condition = p.expr();
        return new WhileNode(condition, p.within("loop", () -> p.block("endwhile")).nodes());
    }
}
