package io.github.djamelkorei.looma.ast;

import org.springframework.expression.Expression;

/** A parsed expression together with its source text and template line, for error messages. */
public record Expr(Expression spel, String source, int line) {
}
