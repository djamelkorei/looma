package io.github.djamelkorei.looma.directive;

import io.github.djamelkorei.looma.spi.Directive;
import io.github.djamelkorei.looma.spi.Expr;
import io.github.djamelkorei.looma.spi.Node;
import io.github.djamelkorei.looma.spi.ParseContext;
import io.github.djamelkorei.looma.spi.Syntax;
import java.util.List;
import java.util.Map;

/** Template inheritance: {@code @extends}, {@code @section}, {@code @yield} and {@code @parent}. */
public final class LayoutDirectives {

    private LayoutDirectives() {}

    /** {@code @extends('layouts.app')}: renders nothing itself; records the parent layout. */
    public static Directive extendsDirective() {
        return new Directive() {
            @Override public String name() { return "extends"; }
            @Override public Node parse(ParseContext p) {
                p.layout(p.expr());
                return null;
            }
        };
    }

    /** {@code @section('name') ... @endsection}, or inline {@code @section('name', 'value')}. */
    public static Directive section() {
        return new Directive() {
            @Override public String name() { return "section"; }
            @Override public Map<String, Syntax> tags() { return Map.of("endsection", Syntax.NONE, "show", Syntax.NONE); }
            @Override public Node parse(ParseContext p) {
                List<String> args = p.splitArgs();
                String name = p.literal(args.get(0));
                if (args.size() > 1) {
                    return new SectionNode(name, List.of(p.echo(p.expr(args.get(1)), false)));
                }
                return new SectionNode(name, p.block("endsection", "show").nodes());
            }
        };
    }

    /** {@code @yield('name', 'default')}: outputs a section defined by a child template. */
    public static Directive yield() {
        return new Directive() {
            @Override public String name() { return "yield"; }
            @Override public Node parse(ParseContext p) {
                List<String> args = p.splitArgs();
                String name = p.literal(args.get(0));
                Expr fallback = args.size() > 1 ? p.expr(args.get(1)) : null;
                return ctx -> {
                    List<SectionNode> chain = ctx.state(Sections.class, Sections::new).definitions(name);
                    if (!chain.isEmpty()) {
                        ctx.state(Sections.class, Sections::new).render(ctx, chain, 0);
                    } else if (fallback != null) {
                        ctx.writeEscaped(ctx.eval(fallback));
                    }
                };
            }
        };
    }

    /** {@code @parent}: inside a section, outputs the same section of the parent layout. */
    public static Directive parent() {
        return new Directive() {
            @Override public String name() { return "parent"; }
            @Override public Syntax syntax() { return Syntax.NONE; }
            @Override public Node parse(ParseContext p) {
                return ctx -> ctx.state(Sections.class, Sections::new).renderParent(ctx);
            }
        };
    }
}
