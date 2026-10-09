package io.github.djamelkorei.looma.spi;

/**
 * A piece of a parsed template. Nodes are immutable and shared between concurrent renders, so keep
 * all per-render state in the {@link RenderContext}, never in fields.
 */
@FunctionalInterface
public interface Node {

    void render(RenderContext ctx);
}
