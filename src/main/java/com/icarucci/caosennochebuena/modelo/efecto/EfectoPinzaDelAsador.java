package com.icarucci.caosennochebuena.modelo.efecto;

public final class EfectoPinzaDelAsador implements EfectoAlCausarDanio {

    @Override
    public void aplicar(
            ContextoEfectoCarta contexto,
            String jugador,
            String objetivo) {

        contexto.registrarEvento(
                jugador,
                "activa Pinza del asador contra",
                objetivo);

        // Si hay varias comidas, el contexto sortea un ejemplar.
        // Si no hay ninguna, no transfiere cartas.
        contexto.robarRecuperacionAlAzar(jugador, objetivo);

        // La Pinza permanece equipada.
    }
}