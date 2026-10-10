package io.github.djamelkorei.looma.directive;

import io.github.djamelkorei.looma.spi.Directive;
import io.github.djamelkorei.looma.spi.Expr;
import io.github.djamelkorei.looma.spi.FormBinder;
import io.github.djamelkorei.looma.spi.Html;
import io.github.djamelkorei.looma.spi.Node;
import io.github.djamelkorei.looma.spi.ParseContext;
import io.github.djamelkorei.looma.spi.RenderContext;
import io.github.djamelkorei.looma.spi.Syntax;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Form binding, like Thymeleaf's {@code th:object} / {@code th:field}:
 *
 * <pre>
 * &#64;bind('form')
 *   &lt;input type="email" &#64;field('email')&gt;           id="email" name="email" value="..."
 *   &lt;textarea &#64;name('bio')&gt;&#64;value('bio')&lt;/textarea&gt;
 *   &#64;checkbox('newsletter')                           hidden marker + &lt;input type="checkbox" ... checked&gt;
 *   &#64;checkbox('roles', 'ADMIN')                       one box of a multi-value field
 *   &lt;input type="radio" &#64;radio('plan', 'pro')&gt;
 *   &lt;select &#64;name('country')&gt;&lt;option &#64;option('country', 'FR')&gt;France&lt;/option&gt;&lt;/select&gt;
 * &#64;endbind
 * </pre>
 *
 * Field paths are fixed in the template ({@code 'address.city'}, {@code 'items[0].name'}); a {@code class} segment is
 * refused. Values come from the {@link FormBinder} ({@code _formBinder}) when there is one, otherwise from the
 * object's own properties, read through the expression sandbox.
 */
public final class FormBindingDirectives {

    private static final Pattern PATH = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*|\\[\\d+])*");
    private static final String BOUND = "__bound";
    private static final String BOUND_NAME = "__boundName";

    private FormBindingDirectives() {}

    public static List<Directive> all() {
        return List.of(bind(), field("field"), field("name"), value(), checkbox(), radio(), option());
    }

    /** A parsed field reference: its path, and an expression reading it from the bound object. */
    private record Field(String path, Expr read) {
        Object value(RenderContext ctx) {
            String objectName = String.valueOf(ctx.get(BOUND_NAME));
            if (ctx.get("_formBinder") instanceof FormBinder binder) {
                return binder.value(objectName, ctx.get(BOUND), path);
            }
            return ctx.eval(read);
        }

        String id() {
            return path.replaceAll("[\\[\\]]", "").replace('.', '-');
        }
    }

    private static Field field(ParseContext p, String rawPath) {
        if (!p.isWithin("bind")) throw p.error("@" + p.name() + " can only be used inside @bind");
        String path = p.literal(rawPath);
        if (!PATH.matcher(path).matches()) throw p.error("Invalid field path: '" + path + "'");
        for (String segment : path.split("[.\\[]")) {
            if (segment.equals("class")) throw p.error("Field path segment 'class' is not allowed");
        }
        // safe navigation, so an empty nested object renders an empty field instead of failing
        return new Field(path, p.expr(BOUND + "?." + path.replace(".", "?.")));
    }

    static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** Checked/selected: equal as text, or contained in a collection/array (multi-value fields). */
    static boolean matches(Object fieldValue, Object option) {
        String wanted = text(option);
        if (fieldValue instanceof Collection<?> c) return c.stream().anyMatch(v -> text(v).equals(wanted));
        if (fieldValue != null && fieldValue.getClass().isArray()) {
            for (Object v : Iterables.toList(fieldValue)) if (text(v).equals(wanted)) return true;
            return false;
        }
        return text(fieldValue).equals(wanted);
    }

    private static Directive bind() {
        return new Directive() {
            @Override public String name() { return "bind"; }
            @Override public Map<String, Syntax> tags() { return Map.of("endbind", Syntax.NONE); }
            @Override public Node parse(ParseContext p) {
                String objectName = p.literalOrNull(p.args());
                if (objectName == null || !objectName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                    throw p.error("Expected @bind('modelAttributeName'), e.g. @bind('form')");
                }
                List<Node> body = p.within("bind", () -> p.block("endbind")).nodes();
                return ctx -> {
                    RenderContext inner = ctx.child();
                    inner.set(BOUND_NAME, objectName);
                    inner.set(BOUND, ctx.get(objectName));
                    inner.render(body);
                };
            }
        };
    }

    /** {@code @field}: id, name and value. {@code @name}: id and name only (textarea, select, password). */
    private static Directive field(String directive) {
        boolean withValue = directive.equals("field");
        return new Directive() {
            @Override public String name() { return directive; }
            @Override public Node parse(ParseContext p) {
                Field f = field(p, p.args());
                return ctx -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("id=\"").append(Html.escape(f.id())).append("\" name=\"").append(Html.escape(f.path())).append('"');
                    if (withValue) sb.append(" value=\"").append(Html.escape(text(f.value(ctx)))).append('"');
                    ctx.write(sb);
                };
            }
        };
    }

    /** {@code @value('bio')}: the escaped value, e.g. as textarea content. */
    private static Directive value() {
        return new Directive() {
            @Override public String name() { return "value"; }
            @Override public Node parse(ParseContext p) {
                Field f = field(p, p.args());
                return ctx -> ctx.writeEscaped(text(f.value(ctx)));
            }
        };
    }

    /**
     * {@code @checkbox('newsletter')} for a boolean field, {@code @checkbox('roles', 'ADMIN')} for one value of a
     * collection; an optional map adds attributes: {@code @checkbox('tos', true, {'class': 'form-check'})}. Writes
     * Spring's hidden {@code _field} marker too, so unticking a box is submitted as "false"/empty.
     */
    private static Directive checkbox() {
        return new Directive() {
            @Override public String name() { return "checkbox"; }
            @Override public Node parse(ParseContext p) {
                List<String> a = p.splitArgs();
                Field f = field(p, a.get(0));
                Expr option = a.size() > 1 ? p.expr(a.get(1)) : null;
                Expr attrs = a.size() > 2 ? p.expr(a.get(2)) : null;
                return ctx -> {
                    Object current = f.value(ctx);
                    Object opt = option == null ? Boolean.TRUE : ctx.eval(option);
                    boolean booleanField = Boolean.TRUE.equals(opt);
                    boolean checked = booleanField ? ctx.truthy(current) && !"false".equals(text(current)) : matches(current, opt);
                    String id = booleanField ? f.id() : f.id() + "-" + text(opt).replaceAll("[^A-Za-z0-9_-]", "_");
                    StringBuilder sb = new StringBuilder();
                    sb.append("<input type=\"hidden\" name=\"_").append(Html.escape(f.path())).append("\" value=\"on\">");
                    sb.append("<input type=\"checkbox\" id=\"").append(Html.escape(id)).append("\" name=\"")
                            .append(Html.escape(f.path())).append("\" value=\"").append(Html.escape(booleanField ? "true" : text(opt)))
                            .append('"');
                    if (attrs != null && ctx.eval(attrs) instanceof Map<?, ?> extra) {
                        extra.forEach((k, v) -> sb.append(' ').append(Html.escape(String.valueOf(k))).append("=\"")
                                .append(Html.escape(text(v))).append('"'));
                    }
                    if (checked) sb.append(" checked");
                    ctx.write(sb.append('>'));
                };
            }
        };
    }

    /** {@code <input type="radio" @radio('plan', 'pro')>}: id, name, value, and checked when it is the field's value. */
    private static Directive radio() {
        return new Directive() {
            @Override public String name() { return "radio"; }
            @Override public Node parse(ParseContext p) {
                List<String> a = p.splitArgs();
                if (a.size() != 2) throw p.error("Expected @radio('field', value)");
                Field f = field(p, a.get(0));
                Expr option = p.expr(a.get(1));
                return ctx -> {
                    String opt = text(ctx.eval(option));
                    String id = f.id() + "-" + opt.replaceAll("[^A-Za-z0-9_-]", "_");
                    ctx.write("id=\"" + Html.escape(id) + "\" name=\"" + Html.escape(f.path()) + "\" value=\"" + Html.escape(opt) + "\""
                            + (matches(f.value(ctx), opt) ? " checked" : ""));
                };
            }
        };
    }

    /** {@code <option @option('country', 'FR')>}: value, and selected when it is (or is among) the field's value. */
    private static Directive option() {
        return new Directive() {
            @Override public String name() { return "option"; }
            @Override public Node parse(ParseContext p) {
                List<String> a = p.splitArgs();
                if (a.size() != 2) throw p.error("Expected @option('field', value)");
                Field f = field(p, a.get(0));
                Expr option = p.expr(a.get(1));
                return ctx -> {
                    Object opt = ctx.eval(option);
                    ctx.write("value=\"" + Html.escape(text(opt)) + "\"" + (matches(f.value(ctx), opt) ? " selected" : ""));
                };
            }
        };
    }
}
