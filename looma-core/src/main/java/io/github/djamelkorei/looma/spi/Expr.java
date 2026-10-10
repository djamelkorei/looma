package io.github.djamelkorei.looma.spi;

import org.springframework.expression.Expression;

/**
 * A parsed expression with its source text, template line and template name, for error messages.
 *
 * @param fastPath engine-internal evaluation shortcut for simple expressions; treat as opaque (may be null)
 * @param template the template the expression was written in, so an error inside a {@code @section}, slot or include
 *                 names the right file (may be null)
 */
public record Expr(Expression spel, String source, int line, Object fastPath, String template) {

    public Expr(Expression spel, String source, int line) {
        this(spel, source, line, null, null);
    }

    public Expr(Expression spel, String source, int line, Object fastPath) {
        this(spel, source, line, fastPath, null);
    }
}
