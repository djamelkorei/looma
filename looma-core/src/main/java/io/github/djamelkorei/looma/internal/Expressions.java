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
    private static final Accessors.ScopeAccessor SCOPE_ACCESSOR = new Accessors.ScopeAccessor();
    private static final Accessors.MapAccessor MAP_ACCESSOR = new Accessors.MapAccessor();
    private static final Sandbox.Properties PROPERTIES = new Sandbox.Properties();
    private static final Sandbox.Methods METHODS = new Sandbox.Methods();
    private static final EvaluationContext CONTEXT = stepContext();

    /** A fresh context with the same accessors and sandbox, for variables bound to one evaluation. */
    static SimpleEvaluationContext stepContext() {
        return SimpleEvaluationContext.forPropertyAccessors(SCOPE_ACCESSOR, MAP_ACCESSOR, PROPERTIES)
                .withMethodResolvers(METHODS)
                .build();
    }

    private Expressions() {}

    public static Expr parse(String source, int line) {
        return parse(source, line, null);
    }

    /** @param template the template the expression belongs to, so evaluation errors name the right file */
    public static Expr parse(String source, int line, String template) {
        if (source == null || source.isBlank()) {
            // SpEL throws IllegalArgumentException here; report it like any other template mistake
            throw new LoomaException("Missing expression", line, null);
        }
        try {
            String spel = translate(source);
            // Always parse with SpEL too: it validates the syntax and is the fallback for the fast path.
            return new Expr(PARSER.parseExpression(spel), source.trim(), line, FastPath.compile(spel), template);
        } catch (ParseException e) {
            throw new LoomaException("Invalid expression '" + source.trim() + "': " + e.getSimpleMessage(), line, e);
        }
    }

    public static Object eval(Expr expr, Scope scope) {
        try {
            if (expr.fastPath() instanceof FastPath.Plan plan) {
                return plan.eval(scope); // inside the try: errors are reported like SpEL's
            }
            return expr.spel().getValue(CONTEXT, scope);
        } catch (RuntimeException e) {
            LoomaException error = new LoomaException("Error evaluating '" + expr.source() + "': " + e.getMessage(), expr.line(), e);
            // Tag with the expression's own template: a section body renders while the layout is active.
            throw expr.template() != null ? error.inTemplate(expr.template()) : error;
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

    /**
     * {@code $x} -> {@code x}, {@code ->} -> {@code .}, {@code ??} -> {@code ?:}, {@code @bean} -> {@code _beans.bean};
     * string literals untouched.
     */
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
            } else if (c == '@' && i + 1 < src.length() && Character.isJavaIdentifierStart(src.charAt(i + 1))) {
                sb.append("_beans."); // @priceService -> _beans.priceService (resolved by BeanRegistry)
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
