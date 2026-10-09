package io.github.djamelkorei.looma.directive;

import io.github.djamelkorei.looma.CsrfToken;
import io.github.djamelkorei.looma.spi.Directive;
import io.github.djamelkorei.looma.spi.Expr;
import io.github.djamelkorei.looma.spi.Html;
import io.github.djamelkorei.looma.spi.Node;
import io.github.djamelkorei.looma.spi.ParseContext;
import io.github.djamelkorei.looma.spi.Syntax;

/** {@code @csrf} and {@code @method('PUT')}: hidden inputs for forms. */
public final class FormDirectives {

    private FormDirectives() {}

    /** Renders the {@code _csrf} variable as a hidden input, or nothing if there is none. */
    public static Directive csrf() {
        return new Directive() {
            @Override public String name() { return "csrf"; }
            @Override public Syntax syntax() { return Syntax.NONE; }
            @Override public Node parse(ParseContext p) {
                return ctx -> {
                    if (ctx.get("_csrf") instanceof CsrfToken t) {
                        ctx.write("<input type=\"hidden\" name=\"" + Html.escape(t.parameterName())
                                + "\" value=\"" + Html.escape(t.token()) + "\">");
                    }
                };
            }
        };
    }

    /** HTML forms only support GET and POST; this adds the {@code _method} field used to spoof the rest. */
    public static Directive method() {
        return new Directive() {
            @Override public String name() { return "method"; }
            @Override public Node parse(ParseContext p) {
                Expr verb = p.expr();
                return ctx -> ctx.write("<input type=\"hidden\" name=\"_method\" value=\""
                        + Html.escape(ctx.evalString(verb)) + "\">");
            }
        };
    }
}
