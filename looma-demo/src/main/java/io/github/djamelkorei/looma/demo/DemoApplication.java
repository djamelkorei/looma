package io.github.djamelkorei.looma.demo;

import java.util.List;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@SpringBootApplication
@Controller
public class DemoApplication {

    public static class Post {
        private final String title;
        private final boolean published;

        public Post(String title, boolean published) {
            this.title = title;
            this.published = published;
        }

        public String getTitle() { return title; }
        public boolean isPublished() { return published; }
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("user", "Ada <Lovelace>");
        model.addAttribute("posts", List.of(
                new Post("Hello Looma", true),
                new Post("Draft: Blade for Java", false)));
        return "home";
    }

    /** htmx-style partial update: only the list items, e.g. for hx-get="/posts" hx-target="#posts". */
    @GetMapping("/posts")
    public String postsFragment(Model model) {
        home(model);
        return "home :: posts";
    }

    @GetMapping("/form")
    public String form() {
        return "form";
    }

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
