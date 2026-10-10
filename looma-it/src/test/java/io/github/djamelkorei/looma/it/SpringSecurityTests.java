package io.github.djamelkorei.looma.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.junit.jupiter.api.BeforeEach;

/** Looma running behind real Spring Security: the token @csrf renders must be the one Security accepts. */
@SpringBootTest
class SpringSecurityTests {

    private static final Pattern TOKEN = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"");

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private String renderedToken(MockHttpSession session) throws Exception {
        String html = mvc.perform(get("/form").session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher m = TOKEN.matcher(html);
        assertThat(m.find()).as("@csrf should render a hidden _csrf input in:\n" + html).isTrue();
        return m.group(1);
    }

    @Test
    void renderedCsrfTokenIsAcceptedByTheSecurityFilter() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String token = renderedToken(session);
        mvc.perform(post("/submit").session(session).param("_csrf", token))
                .andExpect(status().isOk()).andExpect(content().string("accepted"));
    }

    @Test
    void postWithoutTheTokenIsRejected() throws Exception {
        MockHttpSession session = new MockHttpSession();
        renderedToken(session);
        mvc.perform(post("/submit").session(session)).andExpect(status().isForbidden());
    }

    @Test
    void aTokenFromAnotherSessionIsRejected() throws Exception {
        String token = renderedToken(new MockHttpSession());
        mvc.perform(post("/submit").session(new MockHttpSession()).param("_csrf", token))
                .andExpect(status().isForbidden());
    }

    @Test
    void authAndGuestFollowTheSecurityContext() throws Exception {
        mvc.perform(get("/form")).andExpect(content().string(org.hamcrest.Matchers.containsString("guest")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Hi "))));
        mvc.perform(get("/form").with(user("ada")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hi ada")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("guest"))));
    }

    @Test
    void authWithRolesUsesSpringSecurityAuthorities() throws Exception {
        mvc.perform(get("/form").with(user("ada").roles("ADMIN")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("class=\"role\"")));
        mvc.perform(get("/form").with(user("bob").roles("USER")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Hi bob")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("class=\"role\""))));
        mvc.perform(get("/form"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("class=\"role\""))));
    }

    private String posts(org.springframework.test.web.servlet.request.RequestPostProcessor who) throws Exception {
        var request = get("/posts");
        if (who != null) request = request.with(who);
        return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void canChecksAuthoritiesAndThePermissionEvaluator() throws Exception {
        String ada = posts(user("ada").authorities(
                new org.springframework.security.core.authority.SimpleGrantedAuthority("posts:create"),
                new org.springframework.security.core.authority.SimpleGrantedAuthority("reports:view")));
        assertThat(ada).contains("class=\"create\"", "class=\"edit-mine\"", "class=\"readonly-theirs\"", "class=\"reports\"")
                .doesNotContain("class=\"edit-theirs\"");

        String bob = posts(user("bob"));  // no authorities, owns "Theirs"
        assertThat(bob).contains("class=\"edit-theirs\"").doesNotContain("class=\"create\"", "class=\"edit-mine\"",
                "class=\"reports\"", "class=\"readonly-theirs\"");
    }

    @Test
    void anonymousVisitorsAreDeniedEverything() throws Exception {
        assertThat(posts(null)).contains("class=\"readonly-theirs\"")
                .doesNotContain("class=\"create\"", "class=\"edit-mine\"", "class=\"edit-theirs\"", "class=\"reports\"");
    }
}
