package io.github.djamelkorei.looma;

import io.github.djamelkorei.looma.ast.Node;
import java.util.List;

/** A parsed template. Immutable and safe to share between threads. */
public record Template(String name, List<Node> nodes) {
}
