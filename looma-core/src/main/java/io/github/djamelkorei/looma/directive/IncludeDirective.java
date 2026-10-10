package io.github.djamelkorei.looma.directive;

import io.github.djamelkorei.looma.spi.Directive;
import io.github.djamelkorei.looma.spi.Expr;
import io.github.djamelkorei.looma.spi.Node;
import io.github.djamelkorei.looma.spi.ParseContext;
import io.github.djamelkorei.looma.spi.RenderContext;
import java.util.List;
import java.util.Map;

/** {@code @include('partials.nav', {'key': value})}: renders another template in a child scope. */
public final class IncludeDirective implements Directive {

    @Override public String name() { return "include"; }

    @Override
    public Node parse(ParseContext p) {
        List<String> args = p.splitArgs();
        Expr template = p.expr(args.get(0));
        String fixed = p.literalOrNull(args.get(0));
        if (fixed != null) p.references(fixed);
        Expr data = args.size() > 1 ? p.expr(args.get(1)) : null;
        return ctx -> {
            RenderContext inner = ctx.child();
            if (data != null && ctx.eval(data) instanceof Map<?, ?> values) {
                inner.setAll(values);
            }
            inner.renderTemplate(ctx.evalString(template));
        };
    }
}
