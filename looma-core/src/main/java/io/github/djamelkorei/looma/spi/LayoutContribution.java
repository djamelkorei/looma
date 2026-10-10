package io.github.djamelkorei.looma.spi;

/**
 * A top-level node of a template that {@code @extends} a layout and, instead of rendering in place,
 * contributes to the layout (a {@code @section}, for example). Called once, before the layout renders.
 */
public interface LayoutContribution extends Node {

    void contribute(RenderContext ctx);
}
