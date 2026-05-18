package com.shiptracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiptracker.dto.ShipDTO;
import com.shiptracker.event.ShipStatusChangedEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class AisStreamService {

    private static final Logger log = LoggerFactory.getLogger(AisStreamService.class);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Value("${aisstream.api.key:}")
    private String apiKey;

    @Value("${aisstream.bbox.minLat:25.0}")
    private double bboxMinLat;

    @Value("${aisstream.bbox.maxLat:32.0}")
    private double bboxMaxLat;

    @Value("${aisstream.bbox.minLng:-20.5}")
    private double bboxMinLng;

    @Value("${aisstream.bbox.maxLng:-11.5}")
    private double bboxMaxLng;

    private final ApplicationEventPublisher eventPublisher;

    public AisStreamService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, ShipDTO>    shipCache      = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String>     lastStatus     = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<WebSocketSession> browserSessions = new CopyOnWriteArrayList<>();

    private java.net.http.WebSocket aisSocket;
    private volatile boolean connected = false;
    private final StringBuilder msgBuffer = new StringBuilder();
    private volatile long messagesReceived = 0;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public boolean isConnected() {
        return connected;
    }

    public long getMessagesReceived() {
        return messagesReceived;
    }

    public Collection<ShipDTO> getCachedShips() {
        return shipCache.values();
    }

    public boolean hasData() {
        return !shipCache.isEmpty();
    }

    // ── Viewport dinámico ────────────────────────────────────────

    public void updateViewport(double minLat, double maxLat, double minLng, double maxLng) {
        if (!isConfigured()) return;
        this.bboxMinLat = minLat;
        this.bboxMaxLat = maxLat;
        this.bboxMinLng = minLng;
        this.bboxMaxLng = maxLng;
        shipCache.clear();
        lastStatus.clear();
        log.info("Viewport actualizado: [{},{}] [{},{}] — reconectando AIS...", minLat, maxLat, minLng, maxLng);
        if (aisSocket != null) {
            aisSocket.abort();
        }
        connect();
    }

    // ── Ciclo de vida ────────────────────────────────────────────

    @PreDestroy
    public void shutdown() {
        if (aisSocket != null) {
            aisSocket.abort();
        }
    }

    @PostConstruct
    public void init() {
        log.info("AisStreamService init — configurado: {}, key: {}",
                isConfigured(),
                apiKey.length() > 8 ? apiKey.substring(0, 8) + "..." : "(vacía)");
        if (isConfigured()) {
            connect();
        }
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void reconnectIfNeeded() {
        if (isConfigured() && !connected) {
            log.info("Reconectando a aisstream.io...");
            connect();
        }
    }

    private void connect() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            aisSocket = client.newWebSocketBuilder()
                    .buildAsync(URI.create("wss://stream.aisstream.io/v0/stream"), new AisListener())
                    .join();
        } catch (Exception e) {
            log.error("Error conectando a aisstream.io: {}", e.getMessage());
            connected = false;
        }
    }

    // ── Sesiones de navegadores ──────────────────────────────────

    public void addBrowserSession(WebSocketSession session) {
        browserSessions.add(session);
        if (hasData()) {
            sendSnapshot(session);
        }
    }

    public void removeBrowserSession(WebSocketSession session) {
        browserSessions.remove(session);
    }

    private void sendSnapshot(WebSocketSession session) {
        try {
            String json = objectMapper.writeValueAsString(
                    objectMapper.createObjectNode()
                            .put("type", "snapshot")
                            .set("ships", objectMapper.valueToTree(shipCache.values()))
            );
            synchronized (session) {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(json));
                }
            }
        } catch (Exception e) {
            log.warn("Error enviando snapshot: {}", e.getMessage());
        }
    }

    private void broadcastUpdate(ShipDTO ship) {
        if (browserSessions.isEmpty()) return;
        try {
            String json = objectMapper.writeValueAsString(
                    objectMapper.createObjectNode()
                            .put("type", "update")
                            .set("ship", objectMapper.valueToTree(ship))
            );
            TextMessage msg = new TextMessage(json);
            List<WebSocketSession> dead = new ArrayList<>();
            for (WebSocketSession session : browserSessions) {
                try {
                    synchronized (session) {
                        if (session.isOpen()) {
                            session.sendMessage(msg);
                        } else {
                            dead.add(session);
                        }
                    }
                } catch (Exception e) {
                    dead.add(session);
                }
            }
            browserSessions.removeAll(dead);
        } catch (Exception e) {
            log.warn("Error en broadcast: {}", e.getMessage());
        }
    }

    // ── Listener WebSocket hacia aisstream.io ────────────────────

    private class AisListener implements java.net.http.WebSocket.Listener {

        @Override
        public void onOpen(java.net.http.WebSocket ws) {
            connected = true;
            log.info("Conectado a aisstream.io — enviando suscripción...");
            ws.request(Long.MAX_VALUE); // pedir todos los mensajes de una vez
            String sub = buildSubscription();
            log.info("Suscripción: {}", sub);
            ws.sendText(sub, true).whenComplete((result, err) -> {
                if (err != null) log.error("Error enviando suscripción: {}", err.getMessage());
                else             log.info("Suscripción enviada OK — esperando mensajes AIS...");
            });
        }

        @Override
        public CompletionStage<?> onText(java.net.http.WebSocket ws, CharSequence data, boolean last) {
            return handleFrame(data.toString(), last);
        }

        @Override
        public CompletionStage<?> onBinary(java.net.http.WebSocket ws, ByteBuffer data, boolean last) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            return handleFrame(new String(bytes, StandardCharsets.UTF_8), last);
        }

        private CompletionStage<?> handleFrame(String chunk, boolean last) {
            msgBuffer.append(chunk);
            if (last) {
                String raw = msgBuffer.toString();
                messagesReceived++;
                if (messagesReceived <= 3) {
                    log.info("AIS mensaje #{}: {}", messagesReceived,
                            raw.length() > 300 ? raw.substring(0, 300) + "..." : raw);
                }
                processMessage(raw);
                msgBuffer.setLength(0);
            }
            return null;
        }

        @Override
        public CompletionStage<?> onClose(java.net.http.WebSocket ws, int statusCode, String reason) {
            connected = false;
            log.info("Desconectado de aisstream.io ({} {})", statusCode, reason);
            return null;
        }

        @Override
        public void onError(java.net.http.WebSocket ws, Throwable error) {
            connected = false;
            log.error("Error aisstream.io: {}", error.getMessage());
        }
    }

    // ── Parseo de mensajes AIS ───────────────────────────────────

    private void processMessage(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            String type = root.path("MessageType").asText();
            JsonNode meta = root.path("MetaData");

            String mmsi = String.valueOf(meta.path("MMSI").asLong());
            if (mmsi.equals("0")) return;

            ShipDTO ship = shipCache.computeIfAbsent(mmsi, k -> {
                ShipDTO s = new ShipDTO();
                s.setMmsi(k);
                return s;
            });

            if ("PositionReport".equals(type)) {
                JsonNode pos = root.path("Message").path("PositionReport");
                double lat = pos.path("Latitude").asDouble();
                double lng = pos.path("Longitude").asDouble();

                // Filtrar posiciones inválidas (0,0 suele ser error AIS)
                if (lat == 0 && lng == 0) return;

                ship.setLatitude(lat);
                ship.setLongitude(lng);
                ship.setSpeed(Math.round(pos.path("Sog").asDouble() * 10.0) / 10.0);
                ship.setCourse(Math.round(pos.path("Cog").asDouble() * 10.0) / 10.0);

                String newStatus  = mapStatus(pos.path("NavigationalStatus").asInt());
                String prevStatus = lastStatus.put(mmsi, newStatus);
                ship.setStatus(newStatus);
                ship.setLastUpdate(LocalDateTime.now().format(FMT));

                if (prevStatus != null && !prevStatus.equals(newStatus)) {
                    eventPublisher.publishEvent(new ShipStatusChangedEvent(
                            mmsi,
                            ship.getName() != null ? ship.getName() : "MMSI " + mmsi,
                            prevStatus,
                            newStatus,
                            ship.getDestination()
                    ));
                }

                if (ship.getName() == null || ship.getName().isBlank()) {
                    String metaName = meta.path("ShipName").asText("").trim();
                    ship.setName(metaName.isBlank() ? "MMSI " + mmsi : metaName);
                }
                if (ship.getType() == null || ship.getType().isBlank()) {
                    ship.setType("Otro");
                }
                if (ship.getFlag() == null || ship.getFlag().isBlank()) {
                    ship.setFlag(mmsiToFlag(mmsi));
                }

                broadcastUpdate(ship);

            } else if ("ShipStaticData".equals(type)) {
                JsonNode data = root.path("Message").path("ShipStaticData");
                String name = data.path("Name").asText(meta.path("ShipName").asText("")).trim();
                if (!name.isBlank()) ship.setName(name);

                String callSign = data.path("CallSign").asText("").trim();
                if (!callSign.isBlank()) ship.setCallSign(callSign);

                int imo = data.path("ImoNumber").asInt(0);
                if (imo > 0) ship.setImo(String.valueOf(imo));

                int typeCode = data.path("Type").asInt(0);
                if (typeCode > 0) ship.setType(mapShipType(typeCode));

                JsonNode dim = data.path("Dimension");
                double length = dim.path("A").asDouble() + dim.path("B").asDouble();
                double width = dim.path("C").asDouble() + dim.path("D").asDouble();
                if (length > 0) ship.setLength(length);
                if (width > 0) ship.setWidth(width);

                double draught = data.path("MaximumStaticDraught").asDouble(0);
                if (draught > 0) ship.setDraught(draught);

                String destination = data.path("Destination").asText("").trim();
                if (!destination.isBlank() && !destination.equals("@@@@@@@@@@@@@@@")) {
                    ship.setDestination(destination);
                }

                ship.setFlag(mmsiToFlag(mmsi));
                ship.setLastUpdate(LocalDateTime.now().format(FMT));
            }

        } catch (Exception e) {
            log.warn("Error procesando mensaje AIS: {}", e.getMessage());
        }
    }

    // ── Mapeos AIS ───────────────────────────────────────────────

    private String mapShipType(int code) {
        if (code >= 70 && code <= 79) return "Cargo";
        if (code >= 80 && code <= 89) return "Tanque";
        if (code >= 60 && code <= 69) return "Pasajeros";
        if (code == 30) return "Pesca";
        if (code >= 40 && code <= 49) return "Alta velocidad";
        if (code >= 50 && code <= 59) return "Especial";
        if (code == 36 || code == 37) return "Recreo";
        return "Otro";
    }

    private String mapStatus(int code) {
        return switch (code) {
            case 0 -> "En navegación";
            case 1 -> "Fondeado";
            case 5 -> "Atracado";
            case 2, 3 -> "Sin mando";
            case 7 -> "En maniobra";
            default -> "Desconocido";
        };
    }

    // Deriva el código de bandera (país) del MMSI (primeros 3 dígitos = MID)
    private String mmsiToFlag(String mmsi) {
        if (mmsi == null || mmsi.length() < 3) return "??";
        return switch (mmsi.substring(0, 3)) {
            case "211", "218" -> "DE";
            case "224", "225" -> "ES";
            case "227" -> "FR";
            case "232", "233", "234", "235" -> "GB";
            case "244", "245", "246" -> "NL";
            case "247" -> "IT";
            case "248", "249" -> "MT";
            case "255" -> "PT";
            case "259" -> "NO";
            case "265", "266" -> "SE";
            case "273" -> "RU";
            case "303", "338" -> "US";
            case "316" -> "CA";
            case "351", "352", "353" -> "PA";
            case "431", "432" -> "JP";
            case "477" -> "HK";
            case "518" -> "CK";
            case "538" -> "MH";
            case "566", "567" -> "SG";
            case "636" -> "LR";
            default -> "??";
        };
    }

    // ── Suscripción a aisstream.io ───────────────────────────────

    private String buildSubscription() {
        return """
                {
                    "APIKey": "%s",
                    "BoundingBoxes": [[[%s, %s], [%s, %s]]],
                    "FilterMessageTypes": ["PositionReport", "ShipStaticData"]
                }
                """.formatted(apiKey, bboxMinLat, bboxMinLng, bboxMaxLat, bboxMaxLng);
    }
}
