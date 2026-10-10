package io.github.djamelkorei.looma.directive;

import io.github.djamelkorei.looma.spi.Directive;
import io.github.djamelkorei.looma.spi.Expr;
import io.github.djamelkorei.looma.spi.Node;
import io.github.djamelkorei.looma.spi.ParseContext;
import java.util.List;

/**
 * {@code @checked(cond)}, {@code @selected(cond)}, {@code @disabled(cond)}, {@code @readonly(cond)} and
 * {@code @required(cond)}: write the HTML boolean attribute only when the condition is truthy.
 *
 * <pre>
 * &lt;input type="checkbox" name="tos" @checked($form.acceptedTerms)&gt;
 * &lt;option value="fr" @selected($lang == 'fr')&gt;French&lt;/option&gt;
 * &lt;button @disabled($cart.isEmpty())&gt;Pay&lt;/button&gt;
 * </pre>
 */
public final class BooleanAttributeDirectives {

    private BooleanAttributeDirectives() {}

    public static List<Directive> all() {
        return List.of(of("checked"), of("selected"), of("disabled"), of("readonly"), of("required"));
    }

    private static Directive of(String attribute) {
        return new Directive() {
            @Override public String name() { return attribute; }
            @Override public Node parse(ParseContext p) {
                Expr condition = p.expr();
                return ctx -> {
                    if (ctx.test(condition)) ctx.write(attribute);
                };
            }
        };
    }
}
