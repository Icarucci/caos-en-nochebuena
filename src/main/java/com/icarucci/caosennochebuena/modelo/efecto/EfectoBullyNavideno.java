package com.icarucci.caosennochebuena.modelo.efecto;

public final class EfectoBullyNavideno implements EfectoAlRecibirDanio {

    @Override
    public void aplicar(
            ContextoEfectoCarta contexto,
            String jugador,
            String atacante) {

        contexto.registrarEvento(
                jugador,
                "activa Bully navideño contra",
                atacante);

        // No abre defensas ni activa otro Bully.
        // Tampoco consume el equipo ni el permiso de ataque del turno.
        contexto.causarDanioDirecto(atacante, 1);
    }
}