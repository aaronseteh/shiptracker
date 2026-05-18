package com.shiptracker.controller;

import com.shiptracker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @GetMapping("/login")
    public String login(@RequestParam(required = false) String error, Model model) {
        if (error != null) {
            model.addAttribute("error", "Usuario o contraseña incorrectos");
        }
        return "login";
    }

    @GetMapping("/register")
    public String registerForm() {
        return "register";
    }

    @PostMapping("/register")
    public String register(@RequestParam String username,
                            @RequestParam String email,
                            @RequestParam String password,
                            @RequestParam String confirmPassword,
                            RedirectAttributes flash) {
        if (!password.equals(confirmPassword)) {
            flash.addFlashAttribute("error", "Las contraseñas no coinciden");
            return "redirect:/register";
        }
        if (password.length() < 6) {
            flash.addFlashAttribute("error", "La contraseña debe tener al menos 6 caracteres");
            return "redirect:/register";
        }
        try {
            userService.register(username, email, password);
            flash.addFlashAttribute("success", "Cuenta creada correctamente. ¡Inicia sesión!");
            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("error", e.getMessage());
            return "redirect:/register";
        }
    }
}
