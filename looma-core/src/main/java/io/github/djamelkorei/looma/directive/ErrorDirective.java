package io.github.djamelkorei.looma.directive;

import io.github.djamelkorei.looma.spi.Directive;
import io.github.djamelkorei.looma.spi.Expr;
import io.github.djamelkorei.looma.spi.Node;
import io.github.djamelkorei.looma.spi.ParseContext;
import io.github.djamelkorei.looma.spi.RenderContext;
import io.github.djamelkorei.looma.spi.Syntax;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code @error('email') <span>{{ $message }}</span> @enderror}: renders its body only when the field has
 * validation errors, with {@code message} (the first) and {@code messages} (all) set.
 *
 * <p>Errors come from the {@code errors} variable, a map of field name to message(s). The Spring starter
 * fills it from the {@code BindingResult}s in the model, so {@code @Valid} forms work without any glue.
 */
public final class ErrorDirective implements Directive {

    @Override public String name() { return "error"; }
    @Override public Map<String, Syntax> tags() { return Map.of("enderror", Syntax.NONE); }

    @Override
    public Node parse(ParseContext p) {
        Expr field = p.expr();
        List<Node> body = p.block("enderror").nodes();
        return ctx -> {
            List<String> messages = messagesFor(ctx.get("errors"), ctx.evalString(field));
            if (messages.isEmpty()) return;
            RenderContext inner = ctx.child();
            inner.set("message", messages.get(0));
            inner.set("messages", messages);
            inner.render(body);
        };
    }

    private static List<String> messagesFor(Object errors, String field) {
        List<String> out = new ArrayList<>();
        Object value = errors instanceof Map<?, ?> m ? m.get(field) : null;
        if (value instanceof CharSequence s) {
            out.add(s.toString());
        } else if (value instanceof Iterable<?> items) {
            items.forEach(i -> out.add(String.valueOf(i)));
        } else if (value != null && value.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(value);
            for (int i = 0; i < len; i++) out.add(String.valueOf(java.lang.reflect.Array.get(value, i)));
        }
        return out;
    }
}
