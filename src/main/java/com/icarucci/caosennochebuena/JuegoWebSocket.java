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

    private final RegistroSalas registroSalas;

    // Spring nos entrega el mismo registro que usa el controlador.
    public JuegoWebSocket(RegistroSalas registroSalas) {
        this.registroSalas = registroSalas;
    }

    @Override
    public synchronized void afterConnectionEstablished(WebSocketSession conexion)
            throws IOException {
        URI uri = conexion.getUri();
        String consulta = uri == null ? null : uri.getRawQuery();

        // Primero comprobamos que la URL traiga un código con el formato esperado.
        if (consulta == null || !consulta.matches("sala=[A-Z0-9]{4,8}")) {
            conexion.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        String codigoSala = consulta.substring("sala=".length());

        // Después verificamos que el servidor haya creado esa sala.
        if (!registroSalas.existe(codigoSala)) {
            conexion.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        // Comprobamos si queda lugar antes de registrar al nuevo jugador.
        if (contarJugadores(codigoSala) >= 8) {
            conexion.close(new CloseStatus(4008, "Sala llena"));
            return;
        }

        // Primero confirmamos el ingreso y después actualizamos a toda la sala.
        salaPorConexion.put(conexion, codigoSala);
        conexion.sendMessage(new TextMessage("sala-confirmada"));
        avisarCantidadJugadores(codigoSala);
    }
        

    @Override
    public synchronized void afterConnectionClosed(
            WebSocketSession conexion, CloseStatus estado) {
        // Al retirar la conexión, obtenemos el código de la sala que dejó.
        String codigoSala = salaPorConexion.remove(conexion);

        if (codigoSala != null) {
            avisarCantidadJugadores(codigoSala);
        }
    }

    @Override
    protected synchronized void handleTextMessage(
            WebSocketSession origen, TextMessage mensaje) {
        String codigoSala = salaPorConexion.get(origen);

        // Solo procesamos la jugada si el jugador pertenece a una sala.
        if (codigoSala == null || !"tirar-bola".equals(mensaje.getPayload())) {
            return;
        }

        enviarASala(codigoSala, "Alguien tiró una Bola de nieve ❄️");
    }

    private long contarJugadores(String codigoSala) {
        // Contamos únicamente las conexiones abiertas de esta sala.
        return salaPorConexion.entrySet().stream()
                .filter(entrada -> codigoSala.equals(entrada.getValue())
                        && entrada.getKey().isOpen())
                .count();
    }

    private void avisarCantidadJugadores(String codigoSala) {
        // El prefijo permite que la página distinga el contador de una jugada.
        enviarASala(codigoSala, "jugadores:" + contarJugadores(codigoSala));
    }

    private void enviarASala(String codigoSala, String texto) {
        TextMessage mensaje = new TextMessage(texto);

        // Usamos el mismo envío para las jugadas y para el contador.
        for (var entrada : salaPorConexion.entrySet()) {
            WebSocketSession destino = entrada.getKey();

            if (!codigoSala.equals(entrada.getValue()) || !destino.isOpen()) {
                continue;
            }

            try {
                destino.sendMessage(mensaje);
            } catch (IOException | IllegalStateException error) {
                // Si una pestaña se cerró durante el envío, seguimos con las demás.
                salaPorConexion.remove(destino);
            }
        }
    }
}