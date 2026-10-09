package io.github.djamelkorei.looma.directive;

import io.github.djamelkorei.looma.spi.Directive;
import io.github.djamelkorei.looma.spi.Expr;
import io.github.djamelkorei.looma.spi.Node;
import io.github.djamelkorei.looma.spi.ParseContext;
import io.github.djamelkorei.looma.spi.Syntax;

/**
 * {@code @break} and {@code @continue}, optionally conditional: {@code @break($i == 3)}.
 * {@code @break} also ends a {@code @switch} case.
 */
public final class LoopControlDirective implements Directive {

    private final boolean isBreak;

    private LoopControlDirective(boolean isBreak) {
        this.isBreak = isBreak;
    }

    public static LoopControlDirective breakDirective() {
        return new LoopControlDirective(true);
    }

    public static LoopControlDirective continueDirective() {
        return new LoopControlDirective(false);
    }

    @Override public String name() { return isBreak ? "break" : "continue"; }
    @Override public Syntax syntax() { return Syntax.OPTIONAL; }

    @Override
    public Node parse(ParseContext p) {
        boolean allowed = p.isWithin("loop") || (isBreak && p.isWithin("switch"));
        if (!allowed) {
            throw p.error("@" + name() + " can only be used inside a loop" + (isBreak ? " or @switch" : ""));
        }
        Expr condition = p.args() == null ? null : p.expr();
        return ctx -> {
            if (condition == null || ctx.test(condition)) {
                if (isBreak) ctx.breakLoop();
                else ctx.continueLoop();
            }
        };
    }
}
