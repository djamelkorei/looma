package io.github.djamelkorei.looma.internal;

import io.github.djamelkorei.looma.LoomaException;
import java.util.Collection;
import java.util.Map;
import org.springframework.expression.EvaluationContext;
import io.github.djamelkorei.looma.spi.Expr;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;

/**
 * Expression support. Blade-flavoured input ({@code $user->name}, {@code $a ?? 'x'}) is translated
 * to SpEL. Uses {@link SimpleEvaluationContext}, so templates cannot use {@code T()}, constructors
 * or bean references.
 */
public final class Expressions {

    private static final SpelExpressionParser PARSER = new SpelExpressionParser();
    private static final EvaluationContext CONTEXT = SimpleEvaluationContext
            .forPropertyAccessors(new Accessors.ScopeAccessor(), new Accessors.MapAccessor(), new Sandbox.Properties())
            .withMethodResolvers(new Sandbox.Methods())
            .build();

    private Expressions() {}

    public static Expr parse(String source, int line) {
        if (source == null || source.isBlank()) {
            // SpEL throws IllegalArgumentException here; report it like any other template mistake
            throw new LoomaException("Missing expression", line, null);
        }
        try {
            String spel = translate(source);
            // Always parse with SpEL too: it validates the syntax and is the fallback for the fast path.
            return new Expr(PARSER.parseExpression(spel), source.trim(), line, FastPath.compile(spel));
        } catch (ParseException e) {
            throw new LoomaException("Invalid expression '" + source.trim() + "': " + e.getSimpleMessage(), line, e);
        }
    }

    public static Object eval(Expr expr, Scope scope) {
        if (expr.fastPath() instanceof FastPath.Plan plan) {
            Object value = plan.eval(scope);
            if (value != FastPath.MISS) return value;
        }
        try {
            return expr.spel().getValue(CONTEXT, scope);
        } catch (RuntimeException e) {
            throw new LoomaException("Error evaluating '" + expr.source() + "': " + e.getMessage(), expr.line(), e);
        }
    }

    public static String evalString(Expr expr, Scope scope) {
        return String.valueOf(eval(expr, scope));
    }

    /** PHP-style truthiness: null, false, 0, "", and empty collections/maps/arrays are false. */
    public static boolean truthy(Object v) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.doubleValue() != 0;
        if (v instanceof CharSequence s) return s.length() > 0;
        if (v instanceof Collection<?> c) return !c.isEmpty();
        if (v instanceof Map<?, ?> m) return !m.isEmpty();
        if (v.getClass().isArray()) return java.lang.reflect.Array.getLength(v) > 0;
        return true;
    }

    /** {@code $x} -> {@code x}, {@code ->} -> {@code .}, {@code ??} -> {@code ?:}; string literals untouched. */
    static String translate(String src) {
        StringBuilder sb = new StringBuilder(src.length());
        char quote = 0;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            if (quote != 0) {
                sb.append(c);
                if (c == '\\' && i + 1 < src.length()) sb.append(src.charAt(++i));
                else if (c == quote) quote = 0;
            } else if (c == '\'' || c == '"') {
                quote = c;
                sb.append(c);
            } else if (c == '$' && i + 1 < src.length() && Character.isJavaIdentifierStart(src.charAt(i + 1))) {
                // drop the sigil
            } else if (c == '-' && src.startsWith("->", i)) {
                sb.append('.');
                i++;
            } else if (c == '?' && src.startsWith("??", i)) {
                sb.append("?:");
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
