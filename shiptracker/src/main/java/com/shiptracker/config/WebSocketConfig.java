package com.shiptracker.config;

import com.shiptracker.websocket.ShipWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@EnableScheduling
@EnableAsync
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final ShipWebSocketHandler shipWebSocketHandler;

    @Override
    public void registerWebSocketHandlers(@NonNull WebSocketHandlerRegistry registry) {
        registry.addHandler(shipWebSocketHandler, "/ws/ships")
                .setAllowedOrigins("*");
    }
}
