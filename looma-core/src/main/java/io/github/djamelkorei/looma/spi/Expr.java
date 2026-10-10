package io.github.djamelkorei.looma.spi;

import org.springframework.expression.Expression;

/**
 * A parsed expression with its source text and template line, for error messages.
 *
 * @param fastPath engine-internal evaluation shortcut for simple expressions; treat as opaque (may be null)
 */
public record Expr(Expression spel, String source, int line, Object fastPath) {

    public Expr(Expression spel, String source, int line) {
        this(spel, source, line, null);
    }
}
