package io.github.djamelkorei.looma.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DemoApplicationTests {

    @Autowired
    MockMvc mvc;

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
}
