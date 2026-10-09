package io.github.djamelkorei.looma.internal;

import io.github.djamelkorei.looma.spi.Node;
import io.github.djamelkorei.looma.spi.RenderContext;
import java.util.List;

/** {@code <x-slot:name>}; only a transient child of a component, collected by the parser. */
record SlotNode(String name, List<Node> body) implements Node {

    @Override
    public void render(RenderContext ctx) {
        // never rendered directly
    }
}
