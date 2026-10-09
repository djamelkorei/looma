package io.github.djamelkorei.looma.directive;

import io.github.djamelkorei.looma.spi.RenderContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-render registry of section definitions. For each name the list is ordered most-derived first, so
 * {@code @parent} can step from a child's definition to its layout's.
 */
public final class Sections {

    private record Position(List<SectionNode> chain, int index) {}

    private final Map<String, List<SectionNode>> definitions = new HashMap<>();
    private final Deque<Position> active = new ArrayDeque<>();

    /** Called by the engine for each top-level section of a template that extends a layout. */
    public void register(SectionNode section) {
        definitions.computeIfAbsent(section.name(), k -> new ArrayList<>()).add(section);
    }

    List<SectionNode> definitions(String name) {
        return definitions.getOrDefault(name, List.of());
    }

    void render(RenderContext ctx, List<SectionNode> chain, int index) {
        active.push(new Position(chain, index));
        try {
            ctx.render(chain.get(index).body());
        } finally {
            active.pop();
        }
    }

    void renderParent(RenderContext ctx) {
        Position current = active.peek();
        if (current != null && current.index() + 1 < current.chain().size()) {
            render(ctx, current.chain(), current.index() + 1);
        }
    }
}
