package com.icarucci.caosennochebuena.modelo;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

// Conserva la zona de equipo de los jugadores de una partida.
//
// Partida coordinará estas operaciones dentro de sus métodos synchronized.
// Esta clase no administra manos, descarte, turnos ni conexiones.
public final class Equipamiento {

    // Cada jugador puede tener una única carta equipada.
    private final Map<String, Carta> equiposPorJugador = new HashMap<>();

    public Carta getEquipoDe(String jugador) {
        // Devuelve null cuando el jugador no tiene equipo.
        // Carta es inmutable: consultarla no permite modificarla.
        return equiposPorJugador.get(jugador);
    }

    public Carta retirarEquipoDe(String jugador) {
        // Libera la zona y devuelve la carta que estaba equipada.
        //
        // No decide el destino:
        // desequipar la enviará al descarte;
        // robar podrá transferirla a otro jugador.
        return equiposPorJugador.remove(jugador);
    }

    public void colocarEquipo(String jugador, Carta carta) {
        Objects.requireNonNull(jugador, "El jugador es obligatorio");
        Objects.requireNonNull(carta, "La carta es obligatoria");

        // Protegemos la zona para que no reciba cartas de otra categoría.
        if (carta.tipo().getCategoria() != CategoriaCarta.EQUIPO) {
            throw new IllegalArgumentException(
                    "Solo se pueden colocar cartas de equipo");
        }

        // Colocar no reemplaza silenciosamente un equipo.
        // La acción equipar debe desequipar el anterior primero.
        if (equiposPorJugador.containsKey(jugador)) {
            throw new IllegalStateException(
                    "Primero debe retirarse el equipo anterior");
        }

        equiposPorJugador.put(jugador, carta);
    }
}