package io.github.djamelkorei.looma.directive;

import io.github.djamelkorei.looma.MessageResolver;
import io.github.djamelkorei.looma.spi.Directive;
import io.github.djamelkorei.looma.spi.Expr;
import io.github.djamelkorei.looma.spi.Node;
import io.github.djamelkorei.looma.spi.ParseContext;
import java.util.List;

/** {@code @lang('key', arg...)}: a localized, HTML-escaped message from the {@code _messages} variable. */
public final class LangDirective implements Directive {

    @Override public String name() { return "lang"; }

    @Override
    public Node parse(ParseContext p) {
        List<String> args = p.splitArgs();
        Expr key = p.expr(args.get(0));
        List<Expr> params = args.subList(1, args.size()).stream().map(p::expr).toList();
        return ctx -> {
            String k = ctx.evalString(key);
            Object[] values = params.stream().map(ctx::eval).toArray();
            String message = ctx.get("_messages") instanceof MessageResolver r ? r.resolve(k, values) : k;
            ctx.writeEscaped(message == null ? k : message);
        };
    }
}
