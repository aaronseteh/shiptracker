package com.shiptracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiptracker.dto.VesselInfoDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VesselInfoService {

    private static final Logger log = LoggerFactory.getLogger(VesselInfoService.class);
    private static final String BASE_URL = "https://api.datalastic.com/api/v0/vessel";

    @Value("${datalastic.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Caché en memoria: MMSI → info del buque (Optional.empty si no encontrado)
    private final ConcurrentHashMap<String, Optional<VesselInfoDTO>> cache = new ConcurrentHashMap<>();

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public Optional<VesselInfoDTO> getByMmsi(String mmsi) {
        if (!isConfigured() || mmsi == null || mmsi.isBlank()) {
            return Optional.empty();
        }
        return cache.computeIfAbsent(mmsi, this::fetchFromApi);
    }

    private Optional<VesselInfoDTO> fetchFromApi(String mmsi) {
        try {
            String url = BASE_URL + "?api-key=" + apiKey + "&mmsi=" + mmsi;
            String response = restTemplate.getForObject(url, String.class);
            if (response == null) return Optional.empty();

            JsonNode root = objectMapper.readTree(response);
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) return Optional.empty();

            VesselInfoDTO info = objectMapper.treeToValue(data, VesselInfoDTO.class);
            log.debug("Datalastic info obtenida para MMSI {}", mmsi);
            return Optional.of(info);

        } catch (Exception e) {
            log.warn("No se pudo obtener info de Datalastic para MMSI {}: {}", mmsi, e.getMessage());
            return Optional.empty();
        }
    }
}
