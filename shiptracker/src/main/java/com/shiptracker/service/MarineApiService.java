package com.shiptracker.service;

import com.shiptracker.dto.ShipDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MarineApiService {

    @Value("${marine.api.mock:true}")
    private boolean mockMode;

    private final AisStreamService aisStreamService;

    public MarineApiService(AisStreamService aisStreamService) {
        this.aisStreamService = aisStreamService;
    }

    // ── Métodos públicos ─────────────────────────────────────────

    public List<ShipDTO> getAllShips() {
        if (aisStreamService.isConfigured() && aisStreamService.hasData()) {
            return new ArrayList<>(aisStreamService.getCachedShips());
        }
        return buildMockShips();
    }

    public List<ShipDTO> searchByName(String name) {
        return getAllShips().stream()
                .filter(s -> s.getName() != null && s.getName().toLowerCase().contains(name.toLowerCase()))
                .collect(Collectors.toList());
    }

    public List<ShipDTO> filterByType(String type) {
        return getAllShips().stream()
                .filter(s -> type.equalsIgnoreCase(s.getType()))
                .collect(Collectors.toList());
    }

    public Optional<ShipDTO> getShipByMmsi(String mmsi) {
        if (aisStreamService.isConfigured() && aisStreamService.hasData()) {
            return aisStreamService.getCachedShips().stream()
                    .filter(s -> mmsi.equals(s.getMmsi())).findFirst();
        }
        return buildMockShips().stream().filter(s -> mmsi.equals(s.getMmsi())).findFirst();
    }

    // ── Datos mock ───────────────────────────────────────────────

    private List<ShipDTO> buildMockShips() {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        List<ShipDTO> ships = new ArrayList<>();

        // Mediterráneo
        ships.add(ship("215269000", "9234567", "EVER GIVEN",       "Cargo",     "PA", "H3RC",  36.896,  10.187, 12.5, 275.0, "En navegación", "BARCELONA",  now, 400.0, 59.0));
        ships.add(ship("538006998", "9690914", "MSC OSCAR",         "Cargo",     "MH", "V7AH2", 37.975,  23.734,  8.3, 180.0, "Fondeado",      "EL PIREO",   now, 395.0, 59.0));
        ships.add(ship("229005000", "9320421", "COSTA SMERALDA",   "Pasajeros", "IT", "IBCE",  43.296,   5.369, 18.0, 110.0, "En navegación", "MARSELLA",   now, 337.0, 42.0));
        ships.add(ship("232004218", "9782534", "BRITANNIA",        "Pasajeros", "GB", "MQVD2", 43.710,   7.262,  0.0,   0.0, "Atracado",      "NIZA",       now, 330.0, 38.0));
        ships.add(ship("351143000", "9723397", "HAMMONIA FIONIA",  "Tanque",    "PA", "3ECT6", 38.115,  13.361, 11.2, 290.0, "En navegación", "PALERMO",    now, 183.0, 27.0));
        ships.add(ship("229354000", "9344754", "SEABOURN QUEST",   "Pasajeros", "MT", "9HIT9", 35.992,  -5.614, 16.5,  90.0, "En navegación", "BARCELONA",  now, 198.0, 26.0));
        ships.add(ship("256800000", "9210690", "CARONTE",          "RoRo",      "MT", "9HA32", 37.975,  15.640, 12.0,  35.0, "En navegación", "REGGIO CAL", now,  75.0, 14.0));

        // Costa española
        ships.add(ship("224143670", "9345123", "VILLA DE TEIDE",   "Pasajeros", "ES", "EBLS",  28.099, -15.413, 17.5, 280.0, "En navegación", "LAS PALMAS", now, 176.0, 26.0));
        ships.add(ship("224567890", "9456789", "BAHAMA MAMA",      "Tanque",    "ES", "EALM",  36.529,  -6.292,  0.0,   0.0, "Fondeado",      "CÁDIZ",      now, 183.0, 32.0));
        ships.add(ship("224001234", "9512345", "CIUDAD DE CÁDIZ",  "Cargo",     "ES", "EABCD", 37.984,  -1.016,  9.5, 155.0, "En navegación", "CARTAGENA",  now, 145.0, 22.0));

        // Atlántico
        ships.add(ship("636018432", "9362461", "ATLANTIC STAR",    "Cargo",     "LR", "A8SO8", 45.508,  -8.734, 14.5, 200.0, "En navegación", "VIGO",       now, 292.0, 32.0));
        ships.add(ship("232015421", "9789034", "QUEEN MARY 2",     "Pasajeros", "GB", "GBQM",  48.390,  -4.486, 22.0, 270.0, "En navegación", "NEW YORK",   now, 345.0, 41.0));
        ships.add(ship("311000131", "9215030", "NORDIC BOTHNIA",   "Tanque",    "BS", "C6VM9", 52.070,  -9.420, 13.8, 320.0, "En navegación", "RÓTERDAM",   now, 228.0, 32.0));

        // Canal de la Mancha / Mar del Norte
        ships.add(ship("244670315", "9786785", "MSC GULSUN",       "Cargo",     "NL", "PDND",  51.922,   4.479,  6.2,   0.0, "Atracado",      "RÓTERDAM",   now, 400.0, 62.0));
        ships.add(ship("235108053", "9745044", "PRIDE OF HULL",    "RoRo",      "GB", "2FGP7", 53.745,  -0.336, 20.1,  80.0, "En navegación", "RÓTERDAM",   now, 215.0, 32.0));
        ships.add(ship("244720553", "9543216", "DFDS CROWN",       "Pasajeros", "NL", "PBYJ",  51.909,   4.476, 15.3,  10.0, "En navegación", "NEWCASTLE",  now, 210.0, 28.0));

        // Pacífico / Asia
        ships.add(ship("477307700", "9786556", "COSCO SHIPPING",   "Cargo",     "HK", "VROB2", 22.319, 114.169,  0.0,   0.0, "Atracado",      "HONG KONG",  now, 400.0, 59.0));
        ships.add(ship("431000553", "9345678", "PACIFIC PRINCESS", "Pasajeros", "JP", "7JGA",  35.676, 139.650, 15.0, 180.0, "En navegación", "YOKOHAMA",   now, 180.0, 25.0));

        return ships;
    }

    private ShipDTO ship(String mmsi, String imo, String name, String type, String flag,
                         String callSign, double lat, double lng, double speed, double course,
                         String status, String dest, String update, double length, double width) {
        ShipDTO s = new ShipDTO();
        s.setMmsi(mmsi);
        s.setImo(imo);
        s.setName(name);
        s.setType(type);
        s.setFlag(flag);
        s.setCallSign(callSign);
        s.setLatitude(lat);
        s.setLongitude(lng);
        s.setSpeed(speed);
        s.setCourse(course);
        s.setStatus(status);
        s.setDestination(dest);
        s.setLastUpdate(update);
        s.setLength(length);
        s.setWidth(width);
        return s;
    }
}
