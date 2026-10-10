package io.github.djamelkorei.looma.directive;

import io.github.djamelkorei.looma.spi.LayoutContribution;
import io.github.djamelkorei.looma.spi.Node;
import io.github.djamelkorei.looma.spi.RenderContext;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@code @section}. In a child template (one that {@code @extends}) the engine registers it and
 * renders nothing; in a layout it renders the most-derived definition of the same name.
 */
public record SectionNode(String name, List<Node> body) implements LayoutContribution {

    /** In a child template: register this definition so the layout's {@code @yield} finds it. */
    @Override
    public void contribute(RenderContext ctx) {
        ctx.state(Sections.class, Sections::new).register(this);
    }

    @Override
    public void render(RenderContext ctx) {
        Sections sections = ctx.state(Sections.class, Sections::new);
        List<SectionNode> chain = new ArrayList<>(sections.definitions(name));
        chain.add(this); // the layout's own content is the innermost fallback
        sections.render(ctx, chain, 0);
    }
}
