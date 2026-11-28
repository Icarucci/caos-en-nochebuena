package com.icarucci.caosennochebuena.modelo.efecto;

public final class EfectoLoRecalentadoDeAnoche
        implements EfectoAnteDanioLetal {

        @Override
    public boolean aplicar(
            ContextoEfectoCarta contexto,
            String jugador) {

        // El daño letal todavía no fue descontado.
        // La partida nos permite intervenir antes de la eliminación.
        if (!contexto.evitarEliminacionConUnaVida(jugador)) {
            return false;
        }

        contexto.registrarEvento(
                jugador,
                "activa Lo recalentado de anoche y queda con una vida.",
                null);

        // Primero se descarta el equipo y después toda la mano.
        contexto.desequipar(jugador);
        contexto.descartarMano(jugador);

        // Robamos del mazo, utilizando su reciclado habitual.
        // La última carta descartada permanece en el descarte.
        int robadas = contexto.robarCartas(jugador, 1);

        if (robadas == 0) {
            // Puede ocurrir si solo queda una carta en el descarte
            // y no existe ninguna disponible para formar el mazo.
            contexto.registrarEvento(
                    jugador,
                    "no puede robar: no hay cartas disponibles en el mazo.",
                    null);
        }

        return true;
    }
}