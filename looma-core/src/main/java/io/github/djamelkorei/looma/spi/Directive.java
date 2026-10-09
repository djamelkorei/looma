package io.github.djamelkorei.looma.spi;

import java.util.Map;

/**
 * An {@code @name(args)} directive. This is the one extension point of the engine: every built-in
 * directive is an implementation of this interface, and so are yours.
 *
 * <pre>{@code
 * public final class UpperDirective implements Directive {
 *     public String name() { return "upper"; }
 *     public Node parse(ParseContext p) {
 *         Expr value = p.expr();
 *         return ctx -> ctx.writeEscaped(String.valueOf(ctx.eval(value)).toUpperCase());
 *     }
 * }
 * }</pre>
 *
 * Register it with {@code LoomaEngine.builder(loader).directive(new UpperDirective())}, or, with the
 * Spring Boot starter, simply expose it as a bean.
 */
public interface Directive {

    /** The directive name without the '@', letters only, e.g. {@code "upper"}. */
    String name();

    /** How the opening tag is written. Defaults to {@link Syntax#REQUIRED}. */
    default Syntax syntax() {
        return Syntax.REQUIRED;
    }

    /**
     * Secondary tags that belong to this directive's block, with their syntax: for {@code @if} that is
     * {@code elseif}, {@code else} and {@code endif}. They are only valid inside {@link #parse}.
     */
    default Map<String, Syntax> tags() {
        return Map.of();
    }

    /**
     * Parses one use of the directive. The opening tag is already consumed; call
     * {@link ParseContext#block} to read a body up to your closing tags.
     *
     * @return the node to render, or {@code null} if the directive produces no output
     */
    Node parse(ParseContext p);
}
