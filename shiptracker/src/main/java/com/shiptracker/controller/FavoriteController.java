package com.shiptracker.controller;

import com.shiptracker.dto.ShipDTO;
import com.shiptracker.model.Favorite;
import com.shiptracker.model.User;
import com.shiptracker.service.FavoriteService;
import com.shiptracker.service.MarineApiService;
import com.shiptracker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;
    private final UserService userService;
    private final MarineApiService marineApiService;

    @GetMapping
    public String favorites(Model model, Authentication auth) {
        User user = userService.findByUsername(auth.getName()).orElseThrow();
        List<Favorite> favorites = favoriteService.getUserFavorites(user);
        model.addAttribute("favorites", favorites);
        return "favorites";
    }

    @PostMapping("/add")
    public String addFavorite(@RequestParam String mmsi,
                               @RequestParam(required = false) String notes,
                               Authentication auth,
                               RedirectAttributes flash) {
        User user = userService.findByUsername(auth.getName()).orElseThrow();
        ShipDTO ship = marineApiService.getShipByMmsi(mmsi)
                .orElseThrow(() -> new RuntimeException("Barco no encontrado"));
        favoriteService.addFavorite(user, ship, notes);
        flash.addFlashAttribute("success", "Barco añadido a tus favoritos");
        return "redirect:/ships/" + mmsi;
    }

    @PostMapping("/remove")
    public String removeFavorite(@RequestParam String mmsi,
                                  Authentication auth,
                                  RedirectAttributes flash) {
        User user = userService.findByUsername(auth.getName()).orElseThrow();
        favoriteService.removeFavorite(user, mmsi);
        flash.addFlashAttribute("success", "Barco eliminado de favoritos");
        return "redirect:/favorites";
    }

    @PostMapping("/{id}/notes")
    public String updateNotes(@PathVariable Long id,
                               @RequestParam String notes,
                               RedirectAttributes flash) {
        favoriteService.updateNotes(id, notes);
        flash.addFlashAttribute("success", "Notas actualizadas correctamente");
        return "redirect:/favorites";
    }

    @PostMapping("/{id}/alerts")
    public String updateAlerts(@PathVariable Long id,
                                @RequestParam(defaultValue = "false") boolean alertOnDeparture,
                                @RequestParam(defaultValue = "false") boolean alertOnArrival,
                                RedirectAttributes flash) {
        favoriteService.updateAlerts(id, alertOnDeparture, alertOnArrival);
        flash.addFlashAttribute("success", "Alertas de notificación actualizadas");
        return "redirect:/favorites";
    }
}
