package com.icarucci.caosennochebuena.configuracion;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import com.icarucci.caosennochebuena.websocket.JuegoWebSocket;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final JuegoWebSocket juegoWebSocket;

    public WebSocketConfig(JuegoWebSocket juegoWebSocket) {
        this.juegoWebSocket = juegoWebSocket;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(juegoWebSocket, "/ws");
    }
}