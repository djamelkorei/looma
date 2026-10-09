package io.github.djamelkorei.looma.spi;

import org.springframework.expression.Expression;

/** A parsed expression with its source text and template line, for error messages. */
public record Expr(Expression spel, String source, int line) {
}
