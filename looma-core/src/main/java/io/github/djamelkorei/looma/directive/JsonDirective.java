package io.github.djamelkorei.looma.directive;

import io.github.djamelkorei.looma.spi.Directive;
import io.github.djamelkorei.looma.spi.Expr;
import io.github.djamelkorei.looma.spi.Node;
import io.github.djamelkorei.looma.spi.ParseContext;

/**
 * {@code @json($data)}: writes the value as JSON, safe inside {@code <script>}.
 * Inside an HTML attribute use single quotes: {@code <div data-x='@json($x)'>}.
 */
public final class JsonDirective implements Directive {

    @Override public String name() { return "json"; }

    @Override
    public Node parse(ParseContext p) {
        Expr value = p.expr();
        return ctx -> ctx.write(Json.write(ctx.eval(value)));
    }
}
