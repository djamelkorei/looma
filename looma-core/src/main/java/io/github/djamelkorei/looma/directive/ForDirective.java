package io.github.djamelkorei.looma.directive;

import io.github.djamelkorei.looma.LoomaException;
import io.github.djamelkorei.looma.spi.Directive;
import io.github.djamelkorei.looma.spi.Expr;
import io.github.djamelkorei.looma.spi.Node;
import io.github.djamelkorei.looma.spi.ParseContext;
import io.github.djamelkorei.looma.spi.RenderContext;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import io.github.djamelkorei.looma.spi.Syntax;

/**
 * {@code @for($i = 0; $i < 10; $i++) ... @endfor}. The step may be {@code ++}, {@code --},
 * {@code += n} or {@code -= n} on the loop variable; there is no general assignment.
 */
public final class ForDirective implements Directive {

    /** Upper bound on passes, so a condition that never turns false cannot hang a request. */
    static final int MAX_ITERATIONS = 100_000;

    private static final Pattern INIT = Pattern.compile("(?s)^\\$?(\\w+)\\s*=(?!=)\\s*(.+)$");
    private static final Pattern STEP = Pattern.compile("(?s)^\\$?(\\w+)\\s*(\\+\\+|--|\\+=|-=)\\s*(.*)$");

    private record ForNode(String var, Expr init, Expr condition, Expr step, boolean decrement, List<Node> body)
            implements Node {
        @Override
        public void render(RenderContext ctx) {
            RenderContext inner = ctx.child();
            inner.set(var, ctx.eval(init));
            for (int pass = 0; inner.test(condition); pass++) {
                if (pass >= MAX_ITERATIONS) throw tooManyPasses("@for", condition);
                if (!inner.runLoopBody(body)) return;
                Object current = inner.get(var);
                Object amount = inner.eval(step);
                if (!(current instanceof Number c) || !(amount instanceof Number a)) {
                    throw new LoomaException("@for variable '" + var + "' and its step must be numbers, found "
                            + current + " and " + amount, step.line(), null);
                }
                inner.set(var, Numbers.add(c, decrement ? Numbers.negate(a) : a));
            }
        }
    }

    @Override public String name() { return "for"; }
    @Override public Map<String, Syntax> tags() { return Map.of("endfor", Syntax.NONE); }

    @Override
    public Node parse(ParseContext p) {
        List<String> parts = p.split(p.args() == null ? "" : p.args(), ';');
        if (parts.size() != 3) {
            throw p.error("Expected '@for(init; condition; step)', e.g. @for($i = 0; $i < 3; $i++)");
        }
        Matcher init = INIT.matcher(parts.get(0));
        Matcher step = STEP.matcher(parts.get(2));
        if (!init.matches()) {
            throw p.error("@for needs an initializer like '$i = 0' but found: " + parts.get(0));
        }
        if (!step.matches() || !step.group(1).equals(init.group(1))) {
            throw p.error("@for step must be '$" + init.group(1)
                    + "++', '--', '+= n' or '-= n' on the loop variable, but found: " + parts.get(2));
        }
        String op = step.group(2);
        boolean unit = op.equals("++") || op.equals("--");
        if (unit != step.group(3).isEmpty()) {
            throw p.error("Invalid @for step: " + parts.get(2));
        }
        Expr amount = p.expr(unit ? "1" : step.group(3));
        Expr initial = p.expr(init.group(2));
        Expr condition = p.expr(parts.get(1));
        List<Node> body = p.within("loop", () -> p.block("endfor")).nodes();
        return new ForNode(init.group(1), initial, condition, amount, op.startsWith("-"), body);
    }

    static LoomaException tooManyPasses(String directive, Expr condition) {
        return new LoomaException(directive + " exceeded " + MAX_ITERATIONS + " iterations; the condition '"
                + condition.source() + "' never became false", condition.line(), null);
    }
}
