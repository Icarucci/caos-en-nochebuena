package com.icarucci.caosennochebuena;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.List;

public class Partida {

    public enum Fase {
        REPARTO_DE_ROLES,
        ELECCION_DE_HABILIDAD,
        PREPARACION_COMPLETA,
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
    private Fase fase = Fase.REPARTO_DE_ROLES;
    private ResultadoPartida resultado = ResultadoPartida.EN_CURSO;

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
        if (!roles.containsKey(identificador)) {
            return;
        }

        jugadoresQueAbandonaron.add(identificador);
        avanzarSiTodosConfirmaron();
        // Si abandonó durante la selección, dejamos de esperar su elección.
        avanzarSiTodosEligieron();
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

        // Solo esperamos a quienes siguen participando.
        boolean todosEligieron = roles.keySet().stream()
                .allMatch(identificador ->
                        jugadoresQueAbandonaron.contains(identificador)
                        || habilidadesElegidas.containsKey(identificador));

        if (quedaAlguien && todosEligieron) {
            fase = Fase.PREPARACION_COMPLETA;
        }
    }

    public synchronized ResultadoPartida evaluarResultado() {
        // Una vez decidido, el resultado queda guardado.
        if (resultado != ResultadoPartida.EN_CURSO) {
            return resultado;
        }

        // Los desconectados conservan su lugar. Excluimos a quienes abandonaron.
        List<Rol> rolesParticipantes = roles.entrySet().stream()
                .filter(entrada ->
                        !jugadoresQueAbandonaron.contains(entrada.getKey()))
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

    public synchronized ResultadoPartida getResultado() {
        return resultado;
    }

    public synchronized Fase getFase() {
        return fase;
    }
}