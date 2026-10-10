package io.github.djamelkorei.looma;

import io.github.djamelkorei.looma.spi.Expr;
import io.github.djamelkorei.looma.spi.Node;
import java.util.List;

/**
 * A parsed template. Immutable and safe to share between threads.
 *
 * @param layout     the expression from {@code @extends}, or null
 * @param references other templates this one names literally (includes, layouts, components), so
 *                   {@link LoomaEngine#check} can report ones that do not exist
 */
public record Template(String name, List<Node> nodes, Expr layout, List<Reference> references) {

    /** A template named in the source, with the line where it is used. */
    public record Reference(String name, int line) {}
}
