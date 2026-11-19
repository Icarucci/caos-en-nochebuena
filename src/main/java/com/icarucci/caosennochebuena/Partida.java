package com.icarucci.caosennochebuena;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.time.Duration;

public class Partida {

    // Cada turno dura un minuto. Medimos el tiempo transcurrido
    // con nanoTime para no depender de cambios en la hora del servidor.
    private static final long DURACION_TURNO_NANOS = Duration.ofMinutes(1).toNanos();

    public enum Fase {
        REPARTO_DE_ROLES,
        ELECCION_DE_HABILIDAD,
        PREPARACION_COMPLETA,
        EN_JUEGO,
        TERMINADA
    }

    private final Map<String, Rol> roles;
    // Las opciones se sortean una sola vez y se conservan durante la partida.
    private final Map<String, List<Habilidad>> opcionesPorJugador;

    // Registramos la habilidad que elige cada participante.
    private final Map<String, Habilidad> habilidadesElegidas = new HashMap<>();
    private final Set<String> jugadoresQueConfirmaron = new HashSet<>();
    // Conservamos su rol, pero dejamos de esperar su confirmación.
    private final Set<String> jugadoresQueAbandonaron = new HashSet<>();
    // Cuenta los turnos propios que vencieron consecutivamente.
    private final Map<String, Integer> turnosPerdidos = new HashMap<>();
    // La eliminación no borra el rol ni la habilidad del jugador.
    private final Set<String> jugadoresEliminados = new HashSet<>();
    private Fase fase = Fase.REPARTO_DE_ROLES;
    private ResultadoPartida resultado = ResultadoPartida.EN_CURSO;
    // Permanece vacío mientras se eligen las habilidades.
    // Una vez sorteado, conservamos el mismo orden durante toda la partida.
    private List<String> ordenDeTurnos = List.of();
    // Durante la preparación todavía no hay un turno activo.
    private int numeroDeTurno = 0;

    // Posición del jugador actual dentro del orden sorteado.
    private int posicionDeTurno = 0;
    // Instante límite del turno actual, según el reloj monotónico del servidor.
    private long vencimientoDelTurno;

    public Partida(Map<String, Rol> roles) {
        this.roles = Map.copyOf(roles);

        // Usamos los mismos identificadores del reparto de roles.
        // Las opciones quedan guardadas hasta que llegue la etapa de mostrarlas.
        RepartidorDeHabilidades repartidor = new RepartidorDeHabilidades();
        this.opcionesPorJugador = repartidor.sortear(List.copyOf(roles.keySet()));
    }

    public Rol rolDe(String identificador) {
        return roles.get(identificador);
    }

    public synchronized boolean confirmarRol(String identificador) {
        if (fase != Fase.REPARTO_DE_ROLES
                || !roles.containsKey(identificador)
                || jugadoresQueAbandonaron.contains(identificador)) {
            return false;
        }

        if (!jugadoresQueConfirmaron.add(identificador)) {
            return false;
        }

        avanzarSiTodosConfirmaron();
        return true;
    }

    public synchronized void registrarAbandono(String identificador) {
        if (!roles.containsKey(identificador)
                || jugadoresQueAbandonaron.contains(identificador)) {
            return;
        }

        // Guardamos esta condición antes de modificar los participantes.
        boolean abandonoElJugadorDeTurno =
                fase == Fase.EN_JUEGO
                && identificador.equals(getJugadorDeTurno());

        jugadoresQueAbandonaron.add(identificador);

        // Durante la preparación dejamos de esperar a quien abandonó.
        avanzarSiTodosConfirmaron();
        avanzarSiTodosEligieron();

        // Un abandono puede terminar la partida por la composición de roles.
        evaluarResultado();

        // Si la partida sigue, evitamos quedar esperando al jugador que se fue.
        if (fase == Fase.EN_JUEGO && abandonoElJugadorDeTurno) {
            avanzarTurno();
        }
    }

    private void avanzarSiTodosConfirmaron() {
        if (fase != Fase.REPARTO_DE_ROLES) {
            return;
        }

        // Necesitamos que quede alguien participando para avanzar.
        boolean quedaAlguien = jugadoresQueAbandonaron.size() < roles.size();

        // Cada participante debe haber confirmado o abandonado explícitamente.
        boolean todosListos = roles.keySet().stream()
                .allMatch(identificador ->
                        jugadoresQueConfirmaron.contains(identificador)
                        || jugadoresQueAbandonaron.contains(identificador));

        if (quedaAlguien && todosListos) {
            fase = Fase.ELECCION_DE_HABILIDAD;
        }
    }

    // Permite recuperar la confirmación del jugador después de reconectar.
    public synchronized boolean confirmoRol(String identificador) {
        return jugadoresQueConfirmaron.contains(identificador);
    }

    public synchronized List<Habilidad> opcionesDe(String identificador) {
        // Cada jugador puede recibir únicamente sus propias opciones.
        return opcionesPorJugador.getOrDefault(identificador, List.of());
    }

    public synchronized boolean elegirHabilidad(String identificador, Habilidad habilidad) {

        // Solo se puede elegir durante esta etapa y mientras se siga participando.
        if (fase != Fase.ELECCION_DE_HABILIDAD
                || jugadoresQueAbandonaron.contains(identificador)) {
            return false;
        }

        // La elección es definitiva y tiene que ser una de las dos opciones asignadas.
        if (habilidadesElegidas.containsKey(identificador)
                || !opcionesDe(identificador).contains(habilidad)) {
            return false;
        }

        habilidadesElegidas.put(identificador, habilidad);
        avanzarSiTodosEligieron();
        return true;
    }

    public synchronized Habilidad habilidadDe(String identificador) {
        // Al reconectar, podemos recuperar la elección ya confirmada.
        return habilidadesElegidas.get(identificador);
    }

    private void avanzarSiTodosEligieron() {
    if (fase != Fase.ELECCION_DE_HABILIDAD) {
        return;
    }

    boolean quedaAlguien = jugadoresQueAbandonaron.size() < roles.size();

    // Los jugadores que abandonaron ya no tienen que elegir.
    boolean todosEligieron = roles.keySet().stream().allMatch(identificador ->
            jugadoresQueAbandonaron.contains(identificador)|| habilidadesElegidas.containsKey(identificador));

        if (!quedaAlguien || !todosEligieron) {
            return;
        }

        // Antes de preparar los turnos, comprobamos que los abandonos
        // no hayan determinado ya el final de la partida.
        if (evaluarResultado() != ResultadoPartida.EN_CURSO) {
            return;
        }

        // El sorteo ocurre después de la última elección.
        // Así nadie conoce su posición al elegir la habilidad.
        sortearOrdenDeTurnos();

        // Papá Noel ocupa la posición cero del orden sorteado.
        // La partida comienza después de la última elección de habilidad.
        posicionDeTurno = 0;
        numeroDeTurno = 1;
        fase = Fase.EN_JUEGO;

        // El minuto comienza al iniciar el primer turno.
        iniciarRelojDeTurno();
    }

    private void sortearOrdenDeTurnos() {
    // Evitamos repetir el sorteo si ya existe un orden.
    if (!ordenDeTurnos.isEmpty()) {
        return;
    }

    // Solo participan quienes no abandonaron.
    // Los desconectados conservan su lugar y su posición.
    List<String> participantes = new ArrayList<>(
            roles.keySet().stream()
                    .filter(identificador ->
                            !jugadoresQueAbandonaron.contains(identificador))
                    .toList());

    // La partida sigue en curso, por lo que Papá Noel debe participar.
    String papaNoel = participantes.stream().filter(identificador -> roles.get(identificador) == Rol.PAPA_NOEL).findFirst().orElseThrow(() ->
            new IllegalStateException("No se puede iniciar sin Papa Noel"));

        // Sorteamos únicamente a los demás jugadores.
        participantes.remove(papaNoel);
        Collections.shuffle(participantes);

        // Papá Noel ocupa siempre la primera posición.
        participantes.add(0, papaNoel);

        // Guardamos una lista que no pueda modificarse desde afuera.
        ordenDeTurnos = List.copyOf(participantes);
    }

    public synchronized List<String> getOrdenDeTurnos() {
        // Antes del sorteo devuelve una lista vacía.
        // Después permite consultar el orden ya establecido.
        return ordenDeTurnos;
    }

    public synchronized int getNumeroDeTurno() {
        return numeroDeTurno;
    }

    public synchronized String getJugadorDeTurno() {
        // Fuera de la etapa de juego no hay nadie autorizado a jugar.
        if (fase != Fase.EN_JUEGO) {
            return null;
        }

        return ordenDeTurnos.get(posicionDeTurno);
    }

    public synchronized boolean terminarTurno(String identificador) {
        if (fase != Fase.EN_JUEGO
                || !ordenDeTurnos.get(posicionDeTurno).equals(identificador)
                || jugadoresQueAbandonaron.contains(identificador)
                || jugadoresEliminados.contains(identificador)) {
            return false;
        }

        // Una solicitud que llega después del límite no evita
        // que el servidor registre el vencimiento.
        if (System.nanoTime() - vencimientoDelTurno >= 0) {
            return false;
        }

        if (evaluarResultado() != ResultadoPartida.EN_CURSO) {
            return false;
        }

        // Finalizar normalmente corta la sucesión de turnos perdidos.
        turnosPerdidos.remove(identificador);
        avanzarTurno();
        return true;
    }

    private void avanzarTurno() {
        // Después de completar el turno 24, Papá Noel gana si la
        // partida continúa en curso. Los resultados anteriores tienen prioridad.
        if (evaluarResultado() != ResultadoPartida.EN_CURSO) {
            return;
        }

        if (numeroDeTurno >= 24) {
            resultado = ResultadoPartida.VICTORIA_PAPA_NOEL;
            fase = Fase.TERMINADA;
            return;
        }

        // Recorremos el orden fijo y saltamos a quienes abandonaron.
        // Un desconectado conserva su turno: luego agregaremos el temporizador.
        do {
            posicionDeTurno = (posicionDeTurno + 1) % ordenDeTurnos.size();
        // Saltamos tanto a quienes abandonaron como a los eliminados.
        } while (jugadoresQueAbandonaron.contains(
                ordenDeTurnos.get(posicionDeTurno))
                || jugadoresEliminados.contains(
                        ordenDeTurnos.get(posicionDeTurno)));

        // Cada jugador que comienza a jugar consume un turno individual,
        // aunque todavía no se haya completado una vuelta de la mesa.
        numeroDeTurno++;
        // Cada nuevo turno recibe su prupio minuto.
        iniciarRelojDeTurno();
    }

    private void iniciarRelojDeTurno() {
        vencimientoDelTurno = System.nanoTime() + DURACION_TURNO_NANOS;
    }

    public synchronized int getSegundosRestantesDelTurno() {
        if (fase != Fase.EN_JUEGO) {
            return 0;
        }

        long restantes = vencimientoDelTurno - System.nanoTime();

        if (restantes <= 0) {
            return 0;
        }

        // Redondeamos hacia arriba para mostrar 01:00 al comenzar,
        // en vez de bajar inmediatamente a 00:59.
        long segundoEnNanos = Duration.ofSeconds(1).toNanos();
        return (int) ((restantes + segundoEnNanos - 1) / segundoEnNanos);
    }

    public synchronized boolean finalizarTurnoSiVencio() {
        if (fase != Fase.EN_JUEGO) {
            return false;
        }

        if (System.nanoTime() - vencimientoDelTurno < 0) {
            return false;
        }

        String identificador = ordenDeTurnos.get(posicionDeTurno);

        // Sumamos únicamente cuando vence un turno propio.
        int perdidos = turnosPerdidos.getOrDefault(identificador, 0) + 1;
        turnosPerdidos.put(identificador, perdidos);


        if (perdidos >= 2) {
            jugadoresEliminados.add(identificador);
            // La eliminación puede decidir la victoria inmediatamente,
            // por ejemplo, si el eliminado es Papá Noel.
            evaluarResultado();
        }

        if (fase == Fase.EN_JUEGO) {
            avanzarTurno();
        }

        return true;
    }

    public synchronized ResultadoPartida evaluarResultado() {
        // Una vez decidido, el resultado queda guardado.
        if (resultado != ResultadoPartida.EN_CURSO) {
            return resultado;
        }

        // Los desconectados conservan su lugar. Excluimos a quienes abandonaron.
        List<Rol> rolesParticipantes = roles.entrySet().stream()
                // Solo los participantes que siguen en juego determinan el resultado.
                .filter(entrada ->!jugadoresQueAbandonaron.contains(entrada.getKey())&& !jugadoresEliminados.contains(entrada.getKey()))
                .map(Map.Entry::getValue)
                .toList();

        if (rolesParticipantes.isEmpty()) {
            resultado = ResultadoPartida.SIN_PARTICIPANTES;
        } else if (rolesParticipantes.size() == 1
                && rolesParticipantes.get(0) == Rol.KRAMPUS) {
            // Esta victoria tiene prioridad sobre la ausencia de Papá Noel.
            resultado = ResultadoPartida.VICTORIA_KRAMPUS;
        } else if (!rolesParticipantes.contains(Rol.PAPA_NOEL)) {
            resultado = ResultadoPartida.VICTORIA_LADRONES;
        } else if (!rolesParticipantes.contains(Rol.KRAMPUS)
                && !rolesParticipantes.contains(Rol.LADRON)) {
            resultado = ResultadoPartida.VICTORIA_PAPA_NOEL;
        }

        if (resultado != ResultadoPartida.EN_CURSO) {
            fase = Fase.TERMINADA;
        }

        return resultado;
    }

    public synchronized boolean estaEliminado(String identificador) {
        // Permite informar la eliminación sin borrar el asiento
        // ni impedir que el jugador siga mirando la partida.
        return jugadoresEliminados.contains(identificador);
    }

    public synchronized ResultadoPartida getResultado() {
        return resultado;
    }

    public synchronized Fase getFase() {
        return fase;
    }
}