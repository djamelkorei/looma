package io.github.djamelkorei.looma.internal;

import io.github.djamelkorei.looma.spi.Node;
import io.github.djamelkorei.looma.spi.RenderContext;

record TextNode(String value) implements Node {

    @Override
    public void render(RenderContext ctx) {
        ctx.write(value);
    }
}
