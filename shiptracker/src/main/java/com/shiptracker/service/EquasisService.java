package com.shiptracker.service;

import com.shiptracker.dto.VesselInfoDTO;
import jakarta.annotation.PostConstruct;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EquasisService {

    private static final Logger log = LoggerFactory.getLogger(EquasisService.class);

    private static final String LOGIN_URL  = "http://www.equasis.org/EquasisWeb/authen/HomePage";
    private static final String SHIP_URL   = "http://www.equasis.org/EquasisWeb/restricted/ShipInfo?fs=ShipSummary&P_IMO=";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    @Value("${equasis.username:}")
    private String username;

    @Value("${equasis.password:}")
    private String password;

    private final ConcurrentHashMap<String, Optional<VesselInfoDTO>> cache = new ConcurrentHashMap<>();
    private volatile boolean loggedIn = false;
    private HttpClient httpClient;

    public boolean isConfigured() {
        return username != null && !username.isBlank()
            && password != null && !password.isBlank();
    }

    @PostConstruct
    public void init() {
        if (isConfigured()) {
            log.info("EquasisService iniciado — intentando login con {}", username);
            login();
        }
    }

    public Optional<VesselInfoDTO> getByImo(String imo) {
        if (!isConfigured() || imo == null || imo.isBlank()) return Optional.empty();
        return cache.computeIfAbsent(imo, this::fetch);
    }

    // ── Login ────────────────────────────────────────────────────────

    private synchronized void login() {
        try {
            CookieManager cookieManager = new CookieManager();
            cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
            httpClient = HttpClient.newBuilder()
                    .cookieHandler(cookieManager)
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();

            String body = "j_email="    + URLEncoder.encode(username, StandardCharsets.UTF_8)
                        + "&j_password=" + URLEncoder.encode(password, StandardCharsets.UTF_8)
                        + "&submit=Login";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(LOGIN_URL))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            String html = resp.body();

            // Login correcto si la respuesta ya no contiene el campo de contraseña
            if (resp.statusCode() == 200 && !html.contains("j_password")) {
                loggedIn = true;
                log.info("Login en Equasis exitoso ({} → HTTP {})", username, resp.statusCode());
            } else {
                loggedIn = false;
                log.warn("Login en Equasis fallido — verifica usuario/contraseña en application.properties");
            }
        } catch (Exception e) {
            loggedIn = false;
            log.error("Error conectando a Equasis: {}", e.getMessage());
        }
    }

    // ── Consulta de buque ────────────────────────────────────────────

    private Optional<VesselInfoDTO> fetch(String imo) {
        try {
            if (!loggedIn) login();
            if (!loggedIn) return Optional.empty();

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(SHIP_URL + URLEncoder.encode(imo, StandardCharsets.UTF_8)))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .GET()
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());

            // Sesión expirada → reintento único
            if (resp.body().contains("j_password")) {
                log.info("Sesión Equasis expirada, relogeando...");
                loggedIn = false;
                login();
                if (!loggedIn) return Optional.empty();
                resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            }

            if (resp.statusCode() != 200) return Optional.empty();
            return parseHtml(resp.body(), imo);

        } catch (Exception e) {
            log.warn("Error consultando Equasis para IMO {}: {}", imo, e.getMessage());
            return Optional.empty();
        }
    }

    // ── Parseo HTML ──────────────────────────────────────────────────

    private Optional<VesselInfoDTO> parseHtml(String html, String imo) {
        try {
            Document doc = Jsoup.parse(html);
            VesselInfoDTO dto = new VesselInfoDTO();

            // Recorre todos los <tr> buscando pares clave-valor (th+td o td+td)
            for (Element row : doc.select("tr")) {
                String key   = cellText(row, "th");
                String value = cellText(row, "td");

                if (key.isEmpty() && row.select("td").size() >= 2) {
                    Elements tds = row.select("td");
                    key   = tds.get(0).text().trim();
                    value = tds.get(1).text().trim();
                }

                if (key.isEmpty() || value.isEmpty()) continue;

                String k = key.toLowerCase().replaceAll("[:\\s*]+$", "").trim();

                if      (k.contains("gross tonnage") || k.equals("gt"))           dto.setGrossTonnage(parseInt(value));
                else if (k.contains("deadweight")    || k.equals("dwt"))          dto.setDeadweight(parseInt(value));
                else if (k.contains("year of build") || k.contains("year built")) dto.setYearBuilt(parseInt(value));
                else if (k.equals("flag"))                                         dto.setFlagCountry(value);
                else if (k.contains("ship type")     || k.contains("vessel type"))dto.setVesselTypeDetailed(value);
                else if (k.contains("port of registry"))                           dto.setPortOfRegistry(value);
                else if (k.contains("operator"))                                   dto.setOperator(value);
            }

            // Sin datos = el buque no existe o el HTML cambió de estructura
            if (dto.getGrossTonnage() == null && dto.getYearBuilt() == null && dto.getDeadweight() == null) {
                log.debug("Equasis: sin datos técnicos para IMO {} (buque sin registrar o HTML no reconocido)", imo);
                return Optional.empty();
            }

            log.info("Equasis: GT={}, DWT={}, año={} para IMO {}",
                    dto.getGrossTonnage(), dto.getDeadweight(), dto.getYearBuilt(), imo);
            return Optional.of(dto);

        } catch (Exception e) {
            log.warn("Error parseando HTML de Equasis: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String cellText(Element row, String tag) {
        Element el = row.selectFirst(tag);
        return el == null ? "" : el.text().trim();
    }

    private Integer parseInt(String value) {
        try {
            return Integer.parseInt(value.replaceAll("[^0-9]", "").trim());
        } catch (Exception e) {
            return null;
        }
    }
}
