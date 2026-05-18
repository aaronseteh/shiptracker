package com.shiptracker.controller;

import com.shiptracker.dto.ShipDTO;
import com.shiptracker.service.MarineApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final MarineApiService marineApiService;

    @GetMapping("/")
    public String home(Model model) {
        List<ShipDTO> ships = marineApiService.getAllShips();

        long enNavegacion = ships.stream().filter(s -> "En navegación".equals(s.getStatus())).count();
        long fondeados   = ships.stream().filter(s -> "Fondeado".equals(s.getStatus())).count();
        long atracados   = ships.stream().filter(s -> "Atracado".equals(s.getStatus())).count();

        model.addAttribute("totalShips", ships.size());
        model.addAttribute("enNavegacion", enNavegacion);
        model.addAttribute("fondeados", fondeados);
        model.addAttribute("atracados", atracados);
        model.addAttribute("recentShips", ships.subList(0, Math.min(6, ships.size())));
        return "index";
    }
}
