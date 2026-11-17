package com.icarucci.caosennochebuena;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class JuegoWebSocket extends TextWebSocketHandler {

    private static final int MIN_JUGADORES = 4;
    private static final int MAX_JUGADORES = 8;
    private final RegistroSalas registroSalas;

    // La clave identifica al jugador aunque cambie su conexión.
    // El orden de ingreso se usa para elegir al anfitrión en el lobby.
    private final Map<String, Jugador> jugadores = new LinkedHashMap<>();
    // Permite avisar de la expulsión aunque ese jugador estuviera desconectado.
    private final Map<String, String> salaPorIdentificadorExpulsado = new LinkedHashMap<>();
    private final Map<String, List<String>> chatPorSala = new LinkedHashMap<>();
    private final Map<String, List<String>> historialPorSala = new LinkedHashMap<>();

    public JuegoWebSocket(RegistroSalas registroSalas) {
        this.registroSalas = registroSalas;
    }

    @Override
    public synchronized void afterConnectionEstablished(WebSocketSession conexion)
            throws IOException {
        URI uri = conexion.getUri();

        if (uri == null) {
            conexion.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        var parametros = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        String codigoSala = parametros.getFirst("sala");

        if (codigoSala == null || !registroSalas.existe(codigoSala)) {
            conexion.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        String accion = parametros.getFirst("accion");
        if (accion != null) {
            recuperarSesion(conexion, codigoSala, parametros.getFirst("identificador"), accion);
            return;
        }

        // Las conexiones nuevas entran al lobby. Una partida iniciada requiere
        // un identificador que ya pertenezca a esa partida.
        if (registroSalas.estaIniciada(codigoSala)) {
            conexion.close(new CloseStatus(4010, "Partida iniciada"));
            return;
        }

        String nombreCodificado = parametros.getFirst("nombre");
        String nombre = nombreCodificado == null ? ""
                : URLDecoder.decode(nombreCodificado, StandardCharsets.UTF_8).strip();

        if (!textoValido(nombre, 20)) {
            conexion.close(new CloseStatus(4009, "Nombre invalido"));
            return;
        }

        // El nombre no sirve de contraseña. Aunque coincida, solo el navegador
        // que tiene el identificador original puede recuperar ese asiento.
        if (jugadoresEnSala(codigoSala).stream()
                .anyMatch(jugador -> jugador.nombre().equalsIgnoreCase(nombre))) {
            conexion.close(new CloseStatus(4012, "Nombre ocupado"));
            return;
        }

        // Los lugares reservados cuentan para el cupo, aunque se cierre un WebSocket.
        if (jugadoresEnSala(codigoSala).size() >= MAX_JUGADORES) {
            conexion.close(new CloseStatus(4008, "Sala llena"));
            return;
        }

        registrar(UUID.randomUUID().toString(), new Jugador(codigoSala, nombre, conexion));
    }

    private void recuperarSesion(WebSocketSession conexion, String codigoSala,
            String identificador, String accion) throws IOException {
        if (codigoSala.equals(salaPorIdentificadorExpulsado.get(identificador))) {
            conexion.close(new CloseStatus(4013, "Jugador expulsado"));
            return;
        }

        Jugador anterior = jugadores.get(identificador);
        if (anterior == null || !codigoSala.equals(anterior.codigoSala())) {
            conexion.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        if ("abandonar".equals(accion)) {
            jugadores.remove(identificador);
            cerrarConexion(anterior.conexion(), new CloseStatus(4011, "Sala abandonada"));
            avisarJugadores(codigoSala);
            conexion.sendMessage(new TextMessage("sala-abandonada"));
            conexion.close(CloseStatus.NORMAL);
            return;
        }

        if (!"reconectar".equals(accion)) {
            conexion.close(CloseStatus.POLICY_VIOLATION);
            return;
        }

        // El jugador ya tiene un asiento. Solo se actualiza su conexión.
        registrar(identificador, anterior.conectar(conexion));
        cerrarConexion(anterior.conexion(), new CloseStatus(4011, "Conexion reemplazada"));
    }

    private void registrar(String identificador, Jugador jugador) throws IOException {
        // Estas confirmaciones son privadas. El resto de la sala solo ve el nombre.
        jugador.conexion().sendMessage(new TextMessage("identificador:" + identificador));
        jugador.conexion().sendMessage(new TextMessage("sala-confirmada"));
        String codigoSala = jugador.codigoSala();
        jugadores.put(identificador, jugador);

        if (registroSalas.estaIniciada(codigoSala)) {
            enviar(jugador, "partida-iniciada");
            enviar(jugador, "historial:" + String.join("\n",
                    historialPorSala.getOrDefault(codigoSala, List.of())));
        } else {
            enviar(jugador, "chat-historial:" + String.join("\n",
                    chatPorSala.getOrDefault(codigoSala, List.of())));
        }
        avisarJugadores(codigoSala);
    }

    @Override
    public synchronized void afterConnectionClosed(
            WebSocketSession conexion, CloseStatus estado) {
        String identificador = buscarIdentificador(conexion);
        if (identificador == null) {
            return;
        }

        // Marcamos la desconexión y conservamos el asiento hasta que se abandone.
        // El cierre de una conexión que ya fue reemplazada se ignora arriba.
        Jugador jugador = jugadores.get(identificador);
        jugadores.put(identificador, jugador.conectar(null));
        avisarJugadores(jugador.codigoSala());
    }

    @Override
    protected synchronized void handleTextMessage(
            WebSocketSession origen, TextMessage mensaje) {
        Jugador jugador = jugadores.get(buscarIdentificador(origen));
        if (jugador == null) {
            return;
        }

        String codigoSala = jugador.codigoSala();
        String texto = mensaje.getPayload();

        // Después del inicio se procesan jugadas públicas.
        if (registroSalas.estaIniciada(codigoSala)) {
            if ("tirar-bola".equals(texto)) {
                String jugada = jugador.nombre() + " tiró una Bola de nieve ❄️";
                historialPorSala.computeIfAbsent(codigoSala, clave -> new ArrayList<>()).add(jugada);
                enviarASala(codigoSala, "actividad:" + jugada);
            }
            return;
        }

        // En el lobby se aceptan mensajes de chat y acciones del anfitrión.
        if (texto.startsWith("expulsar:")) {
            expulsarJugador(jugador, texto.substring("expulsar:".length()));
        } else if (texto.startsWith("chat:")) {
            String contenido = texto.substring("chat:".length()).strip();
            if (textoValido(contenido, 200)) {
                String linea = jugador.nombre() + ": " + contenido;
                chatPorSala.computeIfAbsent(codigoSala, clave -> new ArrayList<>()).add(linea);
                enviarASala(codigoSala, "chat:" + linea);
            }
        } else if ("iniciar-partida".equals(texto)
                && buscarAnfitrion(codigoSala) == jugador
                && contarConectados(codigoSala) >= MIN_JUGADORES
                && registroSalas.iniciar(codigoSala)) {
            // El mínimo se comprueba con conexiones activas. Los asientos
            // reservados se conservan para que esos jugadores puedan volver.
            enviarASala(codigoSala, "partida-iniciada");
            avisarJugadores(codigoSala);
        }
    }

    private void expulsarJugador(Jugador solicitante, String nombreObjetivo) {
        String codigoSala = solicitante.codigoSala();
        if (buscarAnfitrion(codigoSala) != solicitante) {
            return;
        }

        // Los nombres son únicos dentro de la sala. Buscamos solo entre sus invitados.
        var objetivo = jugadores.entrySet().stream()
                .filter(entrada -> codigoSala.equals(entrada.getValue().codigoSala())
                        && nombreObjetivo.equals(entrada.getValue().nombre())
                        && entrada.getValue() != solicitante)
                .findFirst()
                .orElse(null);

        if (objetivo == null) {
            return;
        }

        // Primero liberamos el asiento e invalidamos el acceso anterior.
        // El cierre posterior del socket no vuelve a modificar la lista.
        String identificador = objetivo.getKey();
        Jugador expulsado = jugadores.remove(identificador);
        salaPorIdentificadorExpulsado.put(identificador, codigoSala);
        cerrarConexion(expulsado.conexion(), new CloseStatus(4013, "Jugador expulsado"));
        avisarJugadores(codigoSala);
    }

    private boolean textoValido(String texto, int maximo) {
        return !texto.isBlank() && texto.length() <= maximo
                && texto.codePoints().noneMatch(Character::isISOControl);
    }

    private String buscarIdentificador(WebSocketSession conexion) {
        return jugadores.entrySet().stream()
                .filter(entrada -> entrada.getValue().conexion() == conexion)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private List<Jugador> jugadoresEnSala(String codigoSala) {
        return jugadores.values().stream()
                .filter(jugador -> codigoSala.equals(jugador.codigoSala()))
                .toList();
    }

    private long contarConectados(String codigoSala) {
        return jugadoresEnSala(codigoSala).stream().filter(Jugador::conectado).count();
    }

    private Jugador buscarAnfitrion(String codigoSala) {
        // El orden del lobby cambia cuando alguien abandona su asiento.
        return jugadoresEnSala(codigoSala).stream()
                .findFirst()
                .orElse(null);
    }

    private void avisarJugadores(String codigoSala) {
        boolean iniciada = registroSalas.estaIniciada(codigoSala);
        List<Jugador> jugadoresDeSala = jugadoresEnSala(codigoSala);
        List<String> estados = jugadoresDeSala.stream()
                .map(jugador -> (jugador.conectado() ? "1:" : "0:") + jugador.nombre())
                .toList();

        Jugador anfitrion = iniciada ? null : buscarAnfitrion(codigoSala);
        boolean hayQuorum = !iniciada && contarConectados(codigoSala) >= MIN_JUGADORES;
        String lista = "jugadores:" + String.join("\n", estados);

        for (Jugador jugador : jugadoresDeSala) {
            if (!iniciada) {
                // La pestaña recibe su permiso antes de dibujar la lista.
                enviar(jugador, "anfitrion:" + (jugador == anfitrion));
            }
            enviar(jugador, lista);
            if (!iniciada) {
                enviar(jugador, "puede-iniciar:" + (jugador == anfitrion && hayQuorum));
            }
        }
    }

    private void enviarASala(String codigoSala, String mensaje) {
        for (Jugador jugador : jugadoresEnSala(codigoSala)) {
            enviar(jugador, mensaje);
        }
    }

    private void enviar(Jugador jugador, String mensaje) {
        if (!jugador.conectado()) {
            return;
        }

        try {
            jugador.conexion().sendMessage(new TextMessage(mensaje));
        } catch (IOException | IllegalStateException error) {
            cerrarConexion(jugador.conexion(), CloseStatus.SERVER_ERROR);
        }
    }

    private void cerrarConexion(WebSocketSession conexion, CloseStatus estado) {
        if (conexion == null || !conexion.isOpen()) {
            return;
        }

        try {
            conexion.close(estado);
        } catch (IOException error) {
            // La pestaña puede haberse cerrado mientras enviábamos el aviso.
        }
    }

    private record Jugador(String codigoSala, String nombre, WebSocketSession conexion) {
        boolean conectado() {
            return conexion != null && conexion.isOpen();
        }

        Jugador conectar(WebSocketSession nuevaConexion) {
            return new Jugador(codigoSala, nombre, nuevaConexion);
        }
    }
}
