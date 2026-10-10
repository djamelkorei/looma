package io.github.djamelkorei.looma.demo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/** A validated form: shows @error, @csrf and @class working together. */
@Controller
public class SignupController {

    public static class SignupForm {
        @NotBlank(message = "Name is required")
        private String name;

        @Email(message = "Enter a valid email")
        @NotBlank(message = "Email is required")
        private String email;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    @GetMapping("/signup")
    public String form(Model model) {
        model.addAttribute("form", new SignupForm());
        return "signup";
    }

    @PostMapping("/signup")
    public String submit(@Valid @ModelAttribute("form") SignupForm form, BindingResult result, Model model) {
        if (result.hasErrors()) {
            return "signup"; // the BindingResult in the model feeds @error
        }
        model.addAttribute("name", form.getName());
        return "signup-done";
    }
}
