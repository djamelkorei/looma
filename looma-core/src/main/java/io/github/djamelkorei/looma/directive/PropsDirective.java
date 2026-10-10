package io.github.djamelkorei.looma.directive;

import io.github.djamelkorei.looma.LoomaException;
import io.github.djamelkorei.looma.spi.Directive;
import io.github.djamelkorei.looma.spi.Expr;
import io.github.djamelkorei.looma.spi.Node;
import io.github.djamelkorei.looma.spi.ParseContext;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code @props(...)} at the top of a component template declares its properties:
 *
 * <pre>
 * &#64;props({'type': 'info', 'dismissible': false})      with defaults
 * &#64;props({'title', 'icon'})                           names only
 * </pre>
 *
 * A property the caller did not pass gets its default, and every declared property is removed from
 * {@code $attributes}, so {@code {{ $attributes }}} only writes the HTML attributes that are left.
 */
public final class PropsDirective implements Directive {

    private static final String NAMES = "__propsNames";

    @Override
    public String name() {
        return "props";
    }

    @Override
    public Node parse(ParseContext p) {
        Expr spec = p.expr();
        Expr strip = p.expr("attributes.except(" + NAMES + ")");
        int line = p.line();
        return ctx -> {
            Object value = ctx.eval(spec);
            Map<String, Object> defaults = new LinkedHashMap<>();
            List<String> names = new ArrayList<>();
            if (value instanceof Map<?, ?> map) {
                map.forEach((k, v) -> {
                    names.add(String.valueOf(k));
                    defaults.put(String.valueOf(k), v);
                });
            } else if (value instanceof Iterable<?> || (value != null && value.getClass().isArray())) {
                for (Object o : Iterables.toList(value)) names.add(String.valueOf(o));
            } else {
                throw new LoomaException("@props expects {'name': default, ...} or {'name', ...}", line, null);
            }
            for (String name : names) {
                if (ctx.get(name) == null && defaults.get(name) != null) ctx.set(name, defaults.get(name));
            }
            if (ctx.get("attributes") != null) {
                List<String> remove = new ArrayList<>(names);
                for (String name : names) remove.add(kebab(name)); // alertType was passed as alert-type
                ctx.set(NAMES, String.join(",", remove));
                ctx.set("attributes", ctx.eval(strip));
            }
        };
    }

    private static String kebab(String camel) {
        StringBuilder sb = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) sb.append('-').append(Character.toLowerCase(c));
            else sb.append(c);
        }
        return sb.toString();
    }
}
