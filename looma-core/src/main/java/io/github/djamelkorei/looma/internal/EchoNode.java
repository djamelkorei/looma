package io.github.djamelkorei.looma.internal;

import io.github.djamelkorei.looma.Htmlable;
import io.github.djamelkorei.looma.spi.Expr;
import io.github.djamelkorei.looma.spi.Node;
import io.github.djamelkorei.looma.spi.RenderContext;

/** {@code {{ expr }}} (escaped) or {@code {!! expr !!}} (raw). */
record EchoNode(Expr expr, boolean raw) implements Node {

    @Override
    public void render(RenderContext ctx) {
        Object value = ctx.eval(expr);
        if (value == null) {
            return;
        }
        if (raw && !(value instanceof Htmlable)) {
            ctx.write(String.valueOf(value));
        } else {
            ctx.writeEscaped(value);
        }
    }
}
