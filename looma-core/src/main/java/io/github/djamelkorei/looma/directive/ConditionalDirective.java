package io.github.djamelkorei.looma.directive;

import io.github.djamelkorei.looma.spi.Directive;
import io.github.djamelkorei.looma.spi.Expr;
import io.github.djamelkorei.looma.spi.Node;
import io.github.djamelkorei.looma.spi.ParseContext;
import io.github.djamelkorei.looma.spi.ParseContext.Block;
import io.github.djamelkorei.looma.spi.ParseContext.Tag;
import io.github.djamelkorei.looma.spi.RenderContext;
import io.github.djamelkorei.looma.spi.Syntax;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * {@code @if}, {@code @unless}, {@code @isset}, {@code @auth} and {@code @guest}: a condition, a body,
 * optionally {@code @else}. They differ only in how the arguments turn into a condition.
 */
public final class ConditionalDirective implements Directive {

    private record Branch(Expr test, List<Node> body) {}

    private record IfNode(List<Branch> branches, List<Node> orElse) implements Node {
        @Override
        public void render(RenderContext ctx) {
            for (Branch b : branches) {
                if (ctx.test(b.test())) {
                    ctx.render(b.body());
                    return;
                }
            }
            ctx.render(orElse);
        }
    }

    private final String name;
    private final String end;
    private final boolean allowElseif;
    private final Syntax syntax;
    private final Function<ParseContext, String> condition;

    private ConditionalDirective(String name, boolean allowElseif, Syntax syntax,
            Function<ParseContext, String> condition) {
        this.name = name;
        this.end = "end" + name;
        this.allowElseif = allowElseif;
        this.syntax = syntax;
        this.condition = condition;
    }

    public static ConditionalDirective ifDirective() {
        return new ConditionalDirective("if", true, Syntax.REQUIRED, ParseContext::args);
    }

    public static ConditionalDirective unless() {
        return new ConditionalDirective("unless", false, Syntax.REQUIRED, p -> "!(" + p.args() + ")");
    }

    public static ConditionalDirective isset() {
        return new ConditionalDirective("isset", false, Syntax.REQUIRED, p -> "(" + p.args() + ") != null");
    }

    /**
     * {@code @auth}: the {@code principal} variable is set (the Spring starter provides it).
     * {@code @auth('ADMIN')} / {@code @auth('ADMIN', 'EDITOR')}: and the request is in at least one of the
     * roles, checked with {@code request.isUserInRole(...)} (Spring Security maps that to its authorities).
     */
    public static ConditionalDirective auth() {
        return new ConditionalDirective("auth", false, Syntax.OPTIONAL, p -> {
            if (p.args() == null) return "principal != null";
            String roles = p.splitArgs().stream().map(r -> "request.isUserInRole(" + r + ")")
                    .collect(java.util.stream.Collectors.joining(" || "));
            return "principal != null && (" + roles + ")";
        });
    }

    /** {@code @can('posts:edit')} or {@code @can('edit', $post)}: asks the {@code _authorizer}; denied without one. */
    public static ConditionalDirective can() {
        return new ConditionalDirective("can", false, Syntax.REQUIRED, ConditionalDirective::canCondition);
    }

    public static ConditionalDirective cannot() {
        return new ConditionalDirective("cannot", false, Syntax.REQUIRED, p -> "!(" + canCondition(p) + ")");
    }

    /** {@code @canany('posts:edit', 'posts:delete')}. */
    public static ConditionalDirective canany() {
        return new ConditionalDirective("canany", false, Syntax.REQUIRED,
                p -> "(_authorizer != null && _authorizer.canAny(" + p.args() + "))");
    }

    private static String canCondition(ParseContext p) {
        if (p.splitArgs().size() > 2) throw p.error("Expected @" + p.name() + "('authority') or @" + p.name() + "('permission', $target)");
        return "(_authorizer != null && _authorizer.can(" + p.args() + "))";
    }

    public static ConditionalDirective guest() {
        return new ConditionalDirective("guest", false, Syntax.NONE, p -> "principal == null");
    }

    @Override public String name() { return name; }
    @Override public Syntax syntax() { return syntax; }

    @Override
    public Map<String, Syntax> tags() {
        return allowElseif
                ? Map.of("elseif", Syntax.REQUIRED, "else", Syntax.NONE, end, Syntax.NONE)
                : Map.of("else", Syntax.NONE, end, Syntax.NONE);
    }

    @Override
    public Node parse(ParseContext p) {
        String[] stops = allowElseif ? new String[] {"elseif", "else", end} : new String[] {"else", end};
        List<Branch> branches = new ArrayList<>();
        List<Node> orElse = List.of();
        Expr test = p.expr(condition.apply(p));
        Block block = p.block(stops);
        branches.add(new Branch(test, block.nodes()));
        while (!block.end().name().equals(end)) {
            Tag tag = block.end();
            if (tag.name().equals("elseif")) {
                Expr elseifTest = p.expr(tag);
                block = p.block(stops);
                branches.add(new Branch(elseifTest, block.nodes()));
            } else {
                orElse = p.block(end).nodes();
                break;
            }
        }
        return new IfNode(branches, orElse);
    }
}
