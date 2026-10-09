package io.github.djamelkorei.looma;

import io.github.djamelkorei.looma.spi.Expr;
import io.github.djamelkorei.looma.spi.Node;
import java.util.List;

/**
 * A parsed template. Immutable and safe to share between threads.
 *
 * @param layout the expression from {@code @extends}, or null
 */
public record Template(String name, List<Node> nodes, Expr layout) {
}
