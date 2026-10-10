package io.github.djamelkorei.looma.directive;

import io.github.djamelkorei.looma.spi.Directive;
import io.github.djamelkorei.looma.spi.Node;
import io.github.djamelkorei.looma.spi.ParseContext;
import io.github.djamelkorei.looma.spi.Syntax;
import java.util.List;
import java.util.Map;

/**
 * {@code @fragment('rows') ... @endfragment}: a named part of a page that can be rendered on its own, for htmx and
 * similar partial updates. In a normal render it changes nothing. Render only the fragment with
 * {@code LoomaEngine.renderFragment(template, "rows", model)}, or in Spring return the view name
 * {@code "contacts :: rows"}.
 */
public final class FragmentDirective implements Directive {

    /** Engine-internal: which fragment the current render is looking for, and what it produced. */
    public static final class Target {
        private final String name;
        private String html;

        public Target(String name) {
            this.name = name;
        }

        /** The fragment's output, or null if the template has no fragment with that name. */
        public String html() {
            return html;
        }
    }

    @Override public String name() { return "fragment"; }
    @Override public Map<String, Syntax> tags() { return Map.of("endfragment", Syntax.NONE); }

    @Override
    public Node parse(ParseContext p) {
        String name = p.literal(p.args() == null ? "" : p.args());
        List<Node> body = p.block("endfragment").nodes();
        return ctx -> {
            Target target = ctx.state(Target.class, () -> null);
            if (target != null && target.name.equals(name) && target.html == null) {
                String html = ctx.capture(body);
                target.html = html;
                ctx.write(html);
            } else {
                ctx.render(body);
            }
        };
    }
}
