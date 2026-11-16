package com.icarucci.caosennochebuena;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class JuegoWebSocket extends TextWebSocketHandler {

    // A cada conexión le corresponde un código de sala.
    private final Map<WebSocketSession, String> salaPorConexion =
            new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession conexion) throws IOException {
        URI uri = conexion.getUri();
        String consulta = uri == null ? null : uri.getRawQuery();

        // Ejemplo de conexión válida: /ws?sala=NIEVE
        if (consulta == null || !consulta.matches("sala=[A-Z0-9]{4,8}")) {
            conexion.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        String codigoSala = consulta.substring("sala=".length());
        salaPorConexion.put(conexion, codigoSala);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession conexion, CloseStatus estado) {
        salaPorConexion.remove(conexion);
    }

    @Override
    protected synchronized void handleTextMessage(
            WebSocketSession origen, TextMessage mensaje) {
        String codigoSala = salaPorConexion.get(origen);

        if (codigoSala == null || !"tirar-bola".equals(mensaje.getPayload())) {
            return;
        }

        TextMessage actividad = new TextMessage("Alguien tiró una Bola de nieve ❄️");

        for (var entrada : salaPorConexion.entrySet()) {
            WebSocketSession destino = entrada.getKey();

            if (!codigoSala.equals(entrada.getValue()) || !destino.isOpen()) {
                continue;
            }

            try {
                destino.sendMessage(actividad);
            } catch (IOException | IllegalStateException error) {
                salaPorConexion.remove(destino);
            }
        }
    }
}