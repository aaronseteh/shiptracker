package com.shiptracker.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shiptracker.service.AisStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@RequiredArgsConstructor
public class ShipWebSocketHandler extends TextWebSocketHandler {

    private final AisStreamService aisStreamService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        aisStreamService.addBrowserSession(session);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        aisStreamService.removeBrowserSession(session);
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            if ("viewport".equals(node.path("type").asText())) {
                double minLat = node.path("minLat").asDouble();
                double maxLat = node.path("maxLat").asDouble();
                double minLng = node.path("minLng").asDouble();
                double maxLng = node.path("maxLng").asDouble();
                aisStreamService.updateViewport(minLat, maxLat, minLng, maxLng);
            }
        } catch (Exception e) {
            // ignorar mensajes malformados
        }
    }
}
