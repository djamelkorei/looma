package io.github.djamelkorei.looma.directive;

import io.github.djamelkorei.looma.spi.Directive;
import io.github.djamelkorei.looma.spi.Node;
import io.github.djamelkorei.looma.spi.ParseContext;
import io.github.djamelkorei.looma.spi.Syntax;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code @push('js') ... @endpush}, {@code @prepend} and {@code @stack('js')}. A stack may be declared
 * before anything is pushed (a layout's {@code <head>} usually is): {@code @stack} writes a placeholder
 * that is filled in once the whole page has rendered.
 */
public final class StackDirectives {

    private StackDirectives() {}

    private static final class Stacks {
        final Map<String, List<String>> items = new java.util.HashMap<>();
        final Set<String> placed = new HashSet<>();
    }

    private static String marker(String stack) {
        return "\u0000looma-stack:" + stack + "\u0000";
    }

    public static Directive push() {
        return pushing("push", false);
    }

    public static Directive prepend() {
        return pushing("prepend", true);
    }

    private static Directive pushing(String name, boolean prepend) {
        String end = "end" + name;
        return new Directive() {
            @Override public String name() { return name; }
            @Override public Map<String, Syntax> tags() { return Map.of(end, Syntax.NONE); }
            @Override public Node parse(ParseContext p) {
                String stack = p.literal(p.args());
                List<Node> body = p.block(end).nodes();
                return ctx -> {
                    String rendered = ctx.capture(body);
                    List<String> items = ctx.state(Stacks.class, Stacks::new).items
                            .computeIfAbsent(stack, k -> new ArrayList<>());
                    if (prepend) items.add(0, rendered);
                    else items.add(rendered);
                };
            }
        };
    }

    public static Directive stack() {
        return new Directive() {
            @Override public String name() { return "stack"; }
            @Override public Node parse(ParseContext p) {
                String stack = p.literal(p.args());
                return ctx -> {
                    Stacks stacks = ctx.state(Stacks.class, Stacks::new);
                    if (stacks.placed.add(stack)) {
                        ctx.onFinish(page -> page.replace(marker(stack),
                                String.join("", stacks.items.getOrDefault(stack, List.of()))));
                    }
                    ctx.write(marker(stack));
                };
            }
        };
    }
}
