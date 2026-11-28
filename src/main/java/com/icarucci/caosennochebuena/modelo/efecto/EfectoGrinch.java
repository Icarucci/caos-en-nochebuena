package com.icarucci.caosennochebuena.modelo.efecto;

public final class EfectoGrinch implements EfectoCarta {

    @Override
    public void aplicar(
            ContextoEfectoCarta contexto,
            String jugador,
            String objetivo) {

        // Partida ya comprobó que el objetivo tiene un equipo
        // y que la carta Grinch pertenece a quien la juega.
        contexto.registrarEvento(
                jugador,
                "usa Grinch contra",
                objetivo);

        // El equipo robado pasa a la mano, conservando su identificador.
        // No reemplaza el equipo activo de quien juega Grinch.
        contexto.robarEquipo(jugador, objetivo);
    }
}