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
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("never reaches"))));
    }
}
