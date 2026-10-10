package io.github.djamelkorei.looma.spi;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * What a {@link Node} may do while rendering. One context is bound to one variable scope; use
 * {@link #child()} when a node needs its own variables (loops, includes).
 */
public interface RenderContext {

    /** Name of the template whose nodes are currently rendering. */
    String templateName();

    // --- expressions and variables

    Object eval(Expr expr);

    String evalString(Expr expr);

    /** Evaluates with PHP-style truthiness: null, false, 0, "" and empty collections are false. */
    boolean test(Expr expr);

    /** The same truthiness rule applied to a value you already have. */
    boolean truthy(Object value);

    Object get(String variable);

    /** Sets a variable in this context's scope only. */
    void set(String variable, Object value);

    void setAll(Map<?, ?> variables);

    /** A new scope that sees this one's variables; assignments stay local. */
    RenderContext child();

    /** A new scope that sees nothing from this one (used by components). */
    RenderContext isolated();

    // --- output

    /** Writes text as-is. */
    void write(CharSequence text);

    /** Writes a value HTML-escaped ({@code null} writes nothing; {@link io.github.djamelkorei.looma.Htmlable}s are written as-is). */
    void writeEscaped(Object value);

    void render(List<Node> nodes);

    /** Renders nodes into a separate buffer and returns it. */
    String capture(List<Node> nodes);

    /** Renders another template in this scope. */
    void renderTemplate(String name);

    // --- loops

    /**
     * Renders one loop pass. Returns false if the pass ended with {@link #breakLoop()}, meaning the
     * loop must stop; a {@link #continueLoop()} is consumed and returns true.
     */
    boolean runLoopBody(List<Node> body);

    /** Renders a switch case; a {@link #breakLoop()} inside it ends the case only. */
    void runSwitchBody(List<Node> body);

    void breakLoop();

    void continueLoop();

    // --- per-render state

    /** State shared by all nodes of this render, created on first use. Keyed by type. */
    <T> T state(Class<T> type, Supplier<T> initial);

    /** Registers a function applied to the complete output once rendering has finished. */
    void onFinish(UnaryOperator<String> finisher);
}
