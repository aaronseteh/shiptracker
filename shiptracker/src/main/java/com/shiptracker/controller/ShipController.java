package com.shiptracker.controller;

import com.shiptracker.dto.ShipDTO;
import com.shiptracker.dto.VesselInfoDTO;
import com.shiptracker.service.EquasisService;
import com.shiptracker.service.FavoriteService;
import com.shiptracker.service.MarineApiService;
import com.shiptracker.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/ships")
@RequiredArgsConstructor
public class ShipController {

    private final MarineApiService marineApiService;
    private final FavoriteService favoriteService;
    private final UserService userService;
    private final EquasisService equasisService;

    @GetMapping
    public String ships(@RequestParam(required = false) String search,
                        @RequestParam(required = false) String type,
                        Model model) {
        List<ShipDTO> ships;

        if (search != null && !search.isBlank()) {
            ships = marineApiService.searchByName(search);
        } else if (type != null && !type.isBlank()) {
            ships = marineApiService.filterByType(type);
        } else {
            ships = marineApiService.getAllShips();
        }

        model.addAttribute("ships", ships);
        model.addAttribute("search", search);
        model.addAttribute("selectedType", type);
        model.addAttribute("totalCount", ships.size());
        return "ships";
    }

    @GetMapping("/{mmsi}")
    public String shipDetail(@PathVariable String mmsi, Model model, Authentication auth) {
        ShipDTO ship = marineApiService.getShipByMmsi(mmsi)
                .orElseThrow(() -> new RuntimeException("Barco no encontrado: " + mmsi));

        // Datos técnicos enriquecidos vía Equasis (gratis, requiere IMO)
        Optional<VesselInfoDTO> vesselInfo = Optional.empty();
        if (ship.getImo() != null && !ship.getImo().isBlank()) {
            vesselInfo = equasisService.getByImo(ship.getImo());
        }

        model.addAttribute("ship", ship);
        model.addAttribute("isFavorite", false);
        model.addAttribute("vesselInfo", vesselInfo.orElse(null));

        String typeKey = ship.getType() == null ? "default" : switch (ship.getType().toLowerCase()) {
            case "cargo", "container" -> "cargo";
            case "tanker" -> "tanker";
            case "passenger" -> "passenger";
            case "roro", "ro-ro" -> "roro";
            case "fishing" -> "fishing";
            default -> "default";
        };
        model.addAttribute("shipImagePath", "/images/ships/" + typeKey + ".svg");

        if (auth != null) {
            userService.findByUsername(auth.getName()).ifPresent(user ->
                    model.addAttribute("isFavorite", favoriteService.isFavorite(user, mmsi))
            );
        }
        return "ship-detail";
    }
}
