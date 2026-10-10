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
