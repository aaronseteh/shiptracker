package com.shiptracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ShipPhotoService {

    private static final Logger log = LoggerFactory.getLogger(ShipPhotoService.class);
    private static final String MT_BASE = "https://www.marinetraffic.com/en/ais/details/ships/mmsi:";

    // Cubre ambos ordenes de atributos en el <meta>
    private static final Pattern P_PROP_FIRST =
        Pattern.compile("<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_CONTENT_FIRST =
        Pattern.compile("<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:image[\"']", Pattern.CASE_INSENSITIVE);

    // Cache: MMSI → URL (cadena vacía = no disponible)
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    private final RestTemplate restTemplate;

    public ShipPhotoService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(6000);
        factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(factory);
    }

    public Optional<String> getPhotoUrl(String mmsi) {
        if (cache.containsKey(mmsi)) {
            String cached = cache.get(mmsi);
            return cached.isEmpty() ? Optional.empty() : Optional.of(cached);
        }
        String url = fetchPhotoUrl(mmsi);
        cache.put(mmsi, url != null ? url : "");
        return Optional.ofNullable(url);
    }

    private String fetchPhotoUrl(String mmsi) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36");
            headers.set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            headers.set("Accept-Language", "es-ES,es;q=0.9,en;q=0.7");

            ResponseEntity<String> response = restTemplate.exchange(
                MT_BASE + mmsi, HttpMethod.GET, new HttpEntity<>(headers), String.class);

            String body = response.getBody();
            if (body == null) return null;

            String found = extract(P_PROP_FIRST, body);
            if (found == null) found = extract(P_CONTENT_FIRST, body);
            if (found == null) found = extractBySearch(body);

            if (found != null && isPhotoUrl(found)) {
                log.debug("Foto MT encontrada para MMSI {}: {}", mmsi, found);
                return found;
            }
        } catch (Exception e) {
            log.debug("No se pudo obtener foto MT para MMSI {}: {}", mmsi, e.getMessage());
        }
        return null;
    }

    private String extract(Pattern pattern, String html) {
        Matcher m = pattern.matcher(html);
        return m.find() ? m.group(1).trim() : null;
    }

    // Búsqueda posicional como respaldo si el regex falla por formato inesperado
    private String extractBySearch(String html) {
        int idx = html.indexOf("og:image");
        while (idx >= 0) {
            int tagStart = html.lastIndexOf("<meta", idx);
            if (tagStart < 0) { idx = html.indexOf("og:image", idx + 1); continue; }
            int tagEnd = html.indexOf(">", idx);
            if (tagEnd < 0) break;
            String tag = html.substring(tagStart, tagEnd + 1);
            int ci = tag.indexOf("content=\"");
            if (ci >= 0) {
                int cs = ci + 9;
                int ce = tag.indexOf("\"", cs);
                if (ce > cs) return tag.substring(cs, ce).trim();
            }
            idx = html.indexOf("og:image", idx + 1);
        }
        return null;
    }

    // Descarta imágenes genéricas del sitio (logos, banners, etc.)
    private boolean isPhotoUrl(String url) {
        if (url == null || url.length() < 10) return false;
        return url.startsWith("http") &&
               (url.contains("photos.marinetraffic.com") || url.contains("showphoto"));
    }
}
