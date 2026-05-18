package com.shiptracker.controller;

import com.shiptracker.dto.ShipDTO;
import com.shiptracker.service.AisStreamService;
import com.shiptracker.service.FavoriteService;
import com.shiptracker.service.MarineApiService;
import com.shiptracker.service.ShipPhotoService;
import com.shiptracker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class MapController {

    private final MarineApiService marineApiService;
    private final AisStreamService aisStreamService;
    private final UserService userService;
    private final FavoriteService favoriteService;
    private final ShipPhotoService shipPhotoService;

    @GetMapping("/map")
    public String map(Model model, Authentication auth) {
        List<ShipDTO> ships = marineApiService.getAllShips();
        model.addAttribute("ships", ships);

        if (auth != null) {
            userService.findByUsername(auth.getName()).ifPresent(user -> {
                List<String> favMmsis = favoriteService.getUserFavorites(user)
                        .stream().map(f -> f.getMmsi()).toList();
                model.addAttribute("favoriteMmsis", favMmsis);
            });
        }
        return "map";
    }

    // ── REST endpoints para AJAX ──────────────────────────────────

    @GetMapping("/api/ships")
    @ResponseBody
    public ResponseEntity<List<ShipDTO>> apiShips(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String type) {

        List<ShipDTO> ships = marineApiService.getAllShips();

        if (name != null && !name.isBlank()) {
            ships = marineApiService.searchByName(name);
        }
        if (type != null && !type.isBlank()) {
            String t = type;
            ships = ships.stream().filter(s -> t.equalsIgnoreCase(s.getType())).toList();
        }
        return ResponseEntity.ok(ships);
    }

    @GetMapping("/api/ships/{mmsi}")
    @ResponseBody
    public ResponseEntity<ShipDTO> apiShipByMmsi(@PathVariable String mmsi) {
        return marineApiService.getShipByMmsi(mmsi)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/ships/{mmsi}/photo")
    @ResponseBody
    public ResponseEntity<?> shipPhoto(@PathVariable String mmsi) {
        String photoUrl = shipPhotoService.getPhotoUrl(mmsi).orElse(null);
        return ResponseEntity.ok(java.util.Map.of("photoUrl", photoUrl != null ? photoUrl : ""));
    }

    @GetMapping("/api/ais/status")
    @ResponseBody
    public ResponseEntity<?> aisStatus() {
        return ResponseEntity.ok(java.util.Map.of(
                "configured",        aisStreamService.isConfigured(),
                "connected",         aisStreamService.isConnected(),
                "messagesReceived",  aisStreamService.getMessagesReceived(),
                "cachedShips",       aisStreamService.getCachedShips().size()
        ));
    }
}
