package io.github.djamelkorei.looma.it;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/** Looma + Spring Security with CSRF protection left on (the default). */
@SpringBootApplication
@Controller
public class ItApplication {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(a -> a.anyRequest().permitAll()).httpBasic(Customizer.withDefaults()).build();
    }

    /** A post may be edited only by its owner. */
    public record Post(String title, String owner) {}

    @Bean
    org.springframework.security.access.PermissionEvaluator permissionEvaluator() {
        return new org.springframework.security.access.PermissionEvaluator() {
            @Override
            public boolean hasPermission(org.springframework.security.core.Authentication auth, Object target, Object permission) {
                return "edit".equals(permission) && target instanceof Post post && post.owner().equals(auth.getName());
            }

            @Override
            public boolean hasPermission(org.springframework.security.core.Authentication auth, java.io.Serializable id,
                    String type, Object permission) {
                return false;
            }
        };
    }

    @GetMapping("/posts")
    String posts(org.springframework.ui.Model model) {
        model.addAttribute("mine", new Post("Mine", "ada"));
        model.addAttribute("theirs", new Post("Theirs", "bob"));
        return "posts";
    }

    @GetMapping("/form")
    String form() {
        return "form";
    }

    @PostMapping("/submit")
    @ResponseBody
    String submit() {
        return "accepted";
    }

    public static void main(String[] args) {
        SpringApplication.run(ItApplication.class, args);
    }
}
