package io.github.djamelkorei.looma.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.junit.jupiter.api.BeforeEach;

// MockMvc is built by hand (not @AutoConfigureMockMvc, which moved packages in Spring Boot 4)
// so these tests run unchanged on every supported Boot version.
@SpringBootTest
class DemoApplicationTests {

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void rendersHomeWithLayoutLoopAndEscaping() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<title>Home - Demo</title>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Signed in as Ada &lt;Lovelace&gt;")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1/2: Hello Looma")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2/2: Draft: Blade for Java")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("(draft)")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<div class=\"alert alert-info mb-4\">Rendered by a component</div>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<script>console.log('home')</script>")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("never reaches"))));
    }

    public static class FakeCsrf {
        public String getParameterName() { return "_csrf"; }
        public String getToken() { return "tok123"; }
    }

    @Test
    void formShowsCsrfMethodAndAuthenticatedUser() throws Exception {
        mvc.perform(get("/form").requestAttr("_csrf", new FakeCsrf()).principal(() -> "ada"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\" value=\"tok123\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_method\" value=\"PUT\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Posting as ada")));
    }

    @Test
    void formShowsGuestWithoutPrincipal() throws Exception {
        mvc.perform(get("/form"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Please sign in")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("_csrf"))));
    }

    @Test
    void langUsesRequestLocale() throws Exception {
        mvc.perform(get("/"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hello, Ada &lt;Lovelace&gt;!")));
        mvc.perform(get("/").locale(java.util.Locale.FRENCH))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Bonjour, Ada &lt;Lovelace&gt; !")));
    }

    @Test
    void invalidSignupShowsFieldErrorsAndKeepsInput() throws Exception {
        mvc.perform(post("/signup").param("name", "").param("email", "not-an-email"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<small class=\"error\">Name is required</small>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<small class=\"error\">Enter a valid email</small>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"not-an-email\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"invalid\"")));
    }

    @Test
    void validSignupShowsWelcomePage() throws Exception {
        mvc.perform(post("/signup").param("name", "Ada <L>").param("email", "ada@example.com"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Welcome, Ada &lt;L&gt;!")));
    }

    @Test
    void emptySignupFormHasNoErrorsShown() throws Exception {
        mvc.perform(get("/signup"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("class=\"error\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("invalid"))));
    }
}
