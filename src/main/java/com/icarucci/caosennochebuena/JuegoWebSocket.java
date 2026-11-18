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
import java.time.Duration;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.scheduling.annotation.Scheduled;

@Component
public class JuegoWebSocket extends TextWebSocketHandler {

    private static final int MIN_JUGADORES = 4;
    private static final int MAX_JUGADORES = 8;
    // Tiempo que conservamos una sala sin jugadores conectados.
    private static final long TIEMPO_SIN_CONEXIONES =
            Duration.ofMinutes(5).toNanos();

    // Guarda desde cuándo cada sala está sin conexiones.
    // Usamos nanoTime para medir tiempo transcurrido sin depender
    // de los cambios de fecha u hora del servidor.
    private final Map<String, Long> sinConexionesDesde = new LinkedHashMap<>();
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
        // Solo abandonamos la sala anterior después de aceptar la nueva conexión.
        if (conexion.isOpen()) {
            abandonarSalaAnterior(
                    parametros.getFirst("salaAnterior"),
                    parametros.getFirst("identificadorAnterior"),
                    codigoSala);
        }
    }

    private void abandonarSalaAnterior(String codigoAnterior, String identificadorAnterior, String codigoNuevo) {
        if (codigoAnterior == null || identificadorAnterior == null
                || codigoAnterior.equals(codigoNuevo)) {
            return;
        }

        Jugador anterior = jugadores.get(identificadorAnterior);

        // Verificamos el acceso anterior antes de retirar ese asiento.
        if (anterior == null || !codigoAnterior.equals(anterior.codigoSala())) {
            return;
        }

        Partida partida = registroSalas.partidaDe(codigoAnterior);

        if (partida != null) {
            // Cambiar de sala cuenta como abandonar la partida anterior.
            partida.registrarAbandono(identificadorAnterior);
        }

        jugadores.remove(identificadorAnterior);
        cerrarConexion(
                anterior.conexion(),
                new CloseStatus(4011, "El jugador cambio de sala"));

        if (partida != null) {
            avisarPreparacionSala(codigoAnterior);
        }

        // Actualizamos los lugares ocupados y el anfitrión de la sala anterior.
        avisarJugadores(codigoAnterior);
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
            Partida partida = registroSalas.partidaDe(codigoSala);
            if (partida != null) {
                // Siempre comprobamos el resultado después de un abandono,
                // aunque todavía queden confirmaciones pendientes.
                partida.registrarAbandono(identificador);
                avisarPreparacionSala(codigoSala);
            }
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
            // Al reconectar, recuperamos el rol que ya estaba asignado.
            enviarRolPropio(identificador, jugador);
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
            Partida partida = registroSalas.partidaDe(codigoSala);
            String identificador = buscarIdentificador(origen);
            if (texto.startsWith("elegir-habilidad:")) {
                String nombreHabilidad = texto.substring("elegir-habilidad:".length());

                try {
                    Habilidad habilidad = Habilidad.valueOf(nombreHabilidad);

                    // Partida valida la etapa, las opciones asignadas y que no haya elegido antes.
                    partida.elegirHabilidad(identificador, habilidad);

                    // Todos reciben su estado privado actualizado al confirmar una elección.
                    avisarPreparacionSala(codigoSala);
                } catch (IllegalArgumentException error) {
                    // Si el identificador no existe, conservamos el estado actual.
                    enviarEstadoPreparacion(identificador, jugador);
                }

                return;
            }

            if ("confirmar-rol".equals(texto)) {
                // Cada jugador confirma su propio rol.
                // Partida evita contar dos veces al mismo jugador.
                partida.confirmarRol(identificador);
                enviarEstadoPreparacion(identificador, jugador);

                if (partida.getFase() == Partida.Fase.ELECCION_DE_HABILIDAD) {
                    // Actualizamos la etapa y las opciones privadas de quienes siguen en la sala.
                    avisarPreparacionSala(codigoSala);
                }
            }

            // Durante estas etapas, el servidor espera las confirmaciones.
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
            && contarConectados(codigoSala) >= MIN_JUGADORES) {
            iniciarPartida(codigoSala);
            }
    }

    private void iniciarPartida(String codigoSala) {
        // El reparto incluye a quienes tienen un asiento reservado en la sala.
        List<String> participantes = jugadores.entrySet().stream()
                .filter(entrada -> codigoSala.equals(entrada.getValue().codigoSala()))
                .map(Map.Entry::getKey)
                .toList();

        if (!registroSalas.iniciar(codigoSala, participantes)) {
            return;
        }

        enviarASala(codigoSala, "partida-iniciada");

        for (String identificador : participantes) {
            enviarRolPropio(identificador, jugadores.get(identificador));
        }

        avisarJugadores(codigoSala);
    }

    private void enviarEstadoPreparacion(String identificador, Jugador jugador) {
        Partida partida = registroSalas.partidaDe(jugador.codigoSala());
        if (partida.getFase() == Partida.Fase.TERMINADA) {
            // Cada jugador recibe el resultado junto con su propia condición de victoria.
            Rol rol = partida.rolDe(identificador);
            ResultadoPartida resultado = partida.getResultado();

            boolean gano = switch (resultado) {
                case VICTORIA_PAPA_NOEL ->
                        rol == Rol.PAPA_NOEL || rol == Rol.RODOLFO || rol == Rol.ELFO;
                case VICTORIA_LADRONES ->
                        rol == Rol.LADRON || rol == Rol.ARREPENTIDO;
                case VICTORIA_KRAMPUS -> rol == Rol.KRAMPUS;
                default -> false;
            };

            enviar(jugador, "partida-terminada:" + resultado.name() + "\n" + gano);
            return;
        }

        enviar(jugador, "rol-confirmado:" + partida.confirmoRol(identificador));
        enviar(jugador, "fase:" + partida.getFase().name());

        Habilidad elegida = partida.habilidadDe(identificador);

        if (elegida != null) {
            // La habilidad elegida se recupera también después de completar la preparación.
            enviar(jugador, "habilidad-elegida:"
                    + elegida.name() + "\n"
                    + elegida.getNombre() + "\n"
                    + elegida.getDescripcion());
        } else if (partida.getFase() == Partida.Fase.ELECCION_DE_HABILIDAD) {
            String opciones = partida.opcionesDe(identificador).stream()
                    .map(habilidad -> habilidad.name() + "\n"
                            + habilidad.getNombre() + "\n"
                            + habilidad.getDescripcion())
                    .collect(java.util.stream.Collectors.joining("\n"));

            enviar(jugador, "opciones-habilidad:" + opciones);
        }
    }

    private void avisarPreparacionSala(String codigoSala) {
        Partida partida = registroSalas.partidaDe(codigoSala);

        // Antes de avanzar, comprobamos si los participantes ya determinan un ganador.
        partida.evaluarResultado();

        for (var entrada : jugadores.entrySet()) {
            Jugador jugador = entrada.getValue();

            if (codigoSala.equals(jugador.codigoSala())) {
                enviarEstadoPreparacion(entrada.getKey(), jugador);
            }
        }
    }

    private void enviarRolPropio(String identificador, Jugador jugador) {
        Rol rol = registroSalas.rolDe(jugador.codigoSala(), identificador);

        if (rol != null) {
            // Enviamos este mensaje solo a la conexión del jugador dueño del rol.
            enviar(jugador, "rol-propio:" + rol.getNombre()
                    + "\n" + rol.getVidasIniciales()
                    + "\n" + rol.getObjetivo());
                    // Al iniciar o reconectar, recuperamos también la etapa y la confirmación.
                    enviarEstadoPreparacion(identificador, jugador);
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

    public synchronized List<SalaPublica> listarSalasPublicas() {
        // Consultamos el listado con el mismo bloqueo que usamos al entrar o salir.
        // Así, los contadores corresponden al estado actual de las salas.
        return registroSalas.codigosPublicos().stream()
                .filter(codigo -> {
                    Partida partida = registroSalas.partidaDe(codigo);

                    // Las partidas terminadas dejan de aparecer en el listado.
                    return partida == null
                            || partida.getFase() != Partida.Fase.TERMINADA;
                })
                .map(codigo -> {
                    Jugador anfitrion = buscarAnfitrion(codigo);

                    return new SalaPublica(
                            codigo,
                            anfitrion == null ? "" : anfitrion.nombre(),
                            jugadoresEnSala(codigo).size(),
                            contarConectados(codigo),
                            registroSalas.estaIniciada(codigo)
                                    ? "EN_PARTIDA"
                                    : "EN_ESPERA");
                })
                .toList();
    }

    // Estos son los datos públicos del listado. Los roles y las habilidades
    // pendientes de elegir quedan fuera de esta respuesta.
    public record SalaPublica(
            String codigo,
            String anfitrion,
            int ocupados,
            long conectados,
            String estado) {
    }

    private void avisarJugadores(String codigoSala) {
        // Sin asientos reservados, la sala se elimina inmediatamente.
        if (jugadoresEnSala(codigoSala).isEmpty()) {
            registroSalas.eliminar(codigoSala);
            chatPorSala.remove(codigoSala);
            historialPorSala.remove(codigoSala);
            sinConexionesDesde.remove(codigoSala);

            // Eliminamos también los accesos invalidados de esta sala.
            salaPorIdentificadorExpulsado.entrySet().removeIf(
                    entrada -> codigoSala.equals(entrada.getValue()));

            return;
        }

        if (contarConectados(codigoSala) == 0) {
            // Solo registramos la primera desconexión total.
            // Las actualizaciones posteriores no reinician la espera.
            sinConexionesDesde.putIfAbsent(codigoSala, System.nanoTime());
        } else {
            // Si alguien entra o se reconecta, cancelamos la cuenta.
            sinConexionesDesde.remove(codigoSala);
        }

        boolean iniciada = registroSalas.estaIniciada(codigoSala);
        List<Jugador> jugadoresDeSala = jugadoresEnSala(codigoSala);
        List<String> estados = jugadoresDeSala.stream()
                .map(jugador -> (jugador.conectado() ? "1:" : "0:") + jugador.nombre())
                .toList();

        Jugador anfitrion = iniciada ? null : buscarAnfitrion(codigoSala);
        boolean hayQuorum = !iniciada && contarConectados(codigoSala) >= MIN_JUGADORES;
        String lista = "jugadores:" + String.join("\n", estados);

        if (iniciada) {
            // Papá Noel es el único rol que anunciamos a toda la mesa al iniciar.
            String nombrePapaNoel = jugadores.entrySet().stream()
                    .filter(entrada -> codigoSala.equals(entrada.getValue().codigoSala()))
                    .filter(entrada -> registroSalas.rolDe(codigoSala, entrada.getKey()) == Rol.PAPA_NOEL)
                    .map(entrada -> entrada.getValue().nombre())
                    .findFirst()
                    .orElse("");

            // Mandamos el nombre antes de la lista para que el navegador pueda marcarlo.
            enviarASala(codigoSala, "papa-noel:" + nombrePapaNoel);
        }

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

    // Spring revisa las salas cada 10 segundos.
    // El bloqueo synchronized evita eliminar una sala mientras
    // otra operación está registrando una reconexión.
    @Scheduled(fixedDelay = 10_000)
    public synchronized void eliminarSalasSinConexiones() {
        long ahora = System.nanoTime();

        // Recorremos una copia porque la limpieza modifica el mapa original.
        for (String codigoSala : new ArrayList<>(sinConexionesDesde.keySet())) {
            if (contarConectados(codigoSala) > 0) {
                sinConexionesDesde.remove(codigoSala);
                continue;
            }

            long desde = sinConexionesDesde.get(codigoSala);

            if (ahora - desde < TIEMPO_SIN_CONEXIONES) {
                continue;
            }

            // Venció el plazo: liberamos todos los asientos reservados.
            jugadores.entrySet().removeIf(
                    entrada -> codigoSala.equals(entrada.getValue().codigoSala()));

            // Reutilizamos la limpieza existente para eliminar
            // la sala, la partida, los historiales y el temporizador.
            avisarJugadores(codigoSala);
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
