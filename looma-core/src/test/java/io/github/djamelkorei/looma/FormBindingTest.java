package io.github.djamelkorei.looma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.djamelkorei.looma.spi.FormBinder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** @bind / @field / @name / @value / @checkbox / @radio / @option. */
class FormBindingTest {

    final Map<String, String> files = new HashMap<>();
    final LoomaEngine engine = new LoomaEngine(new TemplateLoader() {
        public boolean exists(String name) { return files.containsKey(name); }
        public String load(String name) { return files.get(name); }
    }, false);

    String render(String template, Map<String, Object> model) {
        files.put("t", template);
        return engine.render("t", model);
    }

    public record Address(String city) {}

    public static class Form {
        public String getEmail() { return "ada@example.com"; }
        public String getBio() { return "Line <one>"; }
        public boolean isNewsletter() { return true; }
        public boolean isTerms() { return false; }
        public List<String> getRoles() { return List.of("ADMIN", "EDITOR"); }
        public String getPlan() { return "pro"; }
        public String getCountry() { return "FR"; }
        public Address getAddress() { return new Address("Paris \"Centre\""); }
        public Address getBilling() { return null; }
    }

    final Map<String, Object> model = Map.of("form", new Form());

    @Test
    void fieldNameAndValue() {
        assertEquals("<input id=\"email\" name=\"email\" value=\"ada@example.com\">",
                render("@bind('form')<input @field('email')>@endbind", model));
        assertEquals("<textarea id=\"bio\" name=\"bio\">Line &lt;one&gt;</textarea>",
                render("@bind('form')<textarea @name('bio')>@value('bio')</textarea>@endbind", model));
    }

    @Test
    void nestedPathsAreEscapedAndNullSafe() {
        assertEquals("id=\"address-city\" name=\"address.city\" value=\"Paris &quot;Centre&quot;\"",
                render("@bind('form')@field('address.city')@endbind", model));
        assertEquals("id=\"billing-city\" name=\"billing.city\" value=\"\"", render("@bind('form')@field('billing.city')@endbind", model));
        assertEquals("value=\"\"", render("@bind('missing')<x @field('email')>@endbind", Map.of()).replaceAll(".*(value=\"\").*", "$1"));
    }

    @Test
    void booleanAndMultiValueCheckboxesIncludeSpringsMarker() {
        assertEquals("<input type=\"hidden\" name=\"_newsletter\" value=\"on\"><input type=\"checkbox\" id=\"newsletter\" "
                + "name=\"newsletter\" value=\"true\" checked>", render("@bind('form')@checkbox('newsletter')@endbind", model));
        assertEquals("<input type=\"hidden\" name=\"_terms\" value=\"on\"><input type=\"checkbox\" id=\"terms\" name=\"terms\" "
                + "value=\"true\" class=\"check\">", render("@bind('form')@checkbox('terms', true, {'class': 'check'})@endbind", model));
        String roles = render("@bind('form')@checkbox('roles', 'ADMIN')@checkbox('roles', 'VIEWER')@endbind", model);
        assertTrue(roles.contains("id=\"roles-ADMIN\" name=\"roles\" value=\"ADMIN\" checked>"), roles);
        assertTrue(roles.contains("id=\"roles-VIEWER\" name=\"roles\" value=\"VIEWER\">"), roles);
    }

    @Test
    void radiosAndOptions() {
        assertEquals("<input type=\"radio\" id=\"plan-pro\" name=\"plan\" value=\"pro\" checked>"
                + "<input type=\"radio\" id=\"plan-free\" name=\"plan\" value=\"free\">",
                render("@bind('form')<input type=\"radio\" @radio('plan', 'pro')><input type=\"radio\" @radio('plan', 'free')>@endbind", model));
        assertEquals("<option value=\"FR\" selected>France</option><option value=\"DE\">Germany</option>",
                render("@bind('form')<option @option('country', 'FR')>France</option><option @option('country', 'DE')>Germany</option>@endbind", model));
        assertEquals("<option value=\"EDITOR\" selected>", render("@bind('form')<option @option('roles', 'EDITOR')>@endbind", model));
    }

    @Test
    void aFormBinderSuppliesValues() {
        FormBinder binder = (objectName, target, path) -> objectName + ":" + path;
        assertEquals("value=\"form:email\"", render("@bind('form')@field('email')@endbind",
                Map.of("form", new Form(), "_formBinder", binder)).replaceAll(".*(value=\"[^\"]*\").*", "$1"));
    }

    @Test
    void misuseIsReportedAtParseTime() {
        for (String bad : List.of("@field('email')", "@bind($form)x@endbind", "@bind('form')@field('class.name')@endbind",
                "@bind('form')@field('a b')@endbind", "@bind('form')@radio('plan')@endbind", "@bind('form')@option('x')@endbind")) {
            assertThrows(LoomaException.class, () -> render(bad, model), bad);
        }
    }

    /** The form-binding example from docs/syntax.md, verbatim. */
    @Test
    void docsExampleRenders() {
        String html = render("<form method=\"post\" action=\"/signup\">\n    @csrf\n    @bind('form')\n        <input type=\"email\" @field('email') @class({'invalid': $errors.containsKey('email')})>\n        @error('email')<small>{{ $message }}</small>@enderror\n\n        <textarea @name('bio')>@value('bio')</textarea>\n        <input type=\"password\" @name('password')>            {{-- never echo a password back --}}\n\n        <label>@checkbox('newsletter') Send me news</label>\n        @foreach($allRoles as $role)\n            <label>@checkbox('roles', $role) {{ $role }}</label>\n        @endforeach\n\n        <label><input type=\"radio\" @radio('plan', 'free')> Free</label>\n        <label><input type=\"radio\" @radio('plan', 'pro')> Pro</label>\n\n        <select @name('country')>\n            <option @option('country', 'FR')>France</option>\n            <option @option('country', 'DE')>Germany</option>\n        </select>\n    @endbind\n</form>\n", Map.of("form", new Form(), "allRoles", List.of("ADMIN", "VIEWER")));
        assertTrue(html.contains("id=\"email\" name=\"email\" value=\"ada@example.com\""), html);
        assertTrue(html.contains("<textarea id=\"bio\" name=\"bio\">Line &lt;one&gt;</textarea>"), html);
        assertTrue(html.contains("<input type=\"password\" id=\"password\" name=\"password\">"), html);
        assertTrue(html.contains("value=\"ADMIN\" checked> ADMIN") && html.contains("value=\"VIEWER\"> VIEWER"), html);
        assertTrue(html.contains("value=\"pro\" checked> Pro") && html.contains("<option value=\"FR\" selected>France"), html);
    }
}
