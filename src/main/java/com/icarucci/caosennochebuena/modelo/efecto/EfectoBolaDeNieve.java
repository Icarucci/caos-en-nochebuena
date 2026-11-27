package com.icarucci.caosennochebuena.modelo.efecto;

public final class EfectoBolaDeNieve implements EfectoCarta {

    @Override
    public void aplicar(
            ContextoEfectoCarta contexto,
            String jugador,
            String objetivo) {

        // Lanzar la bola no produce daño inmediato.
        // Primero abrimos la oportunidad de defensa o intervención.
        contexto.abrirAtaque(jugador, objetivo);

        contexto.registrarEvento(
                jugador,
                "lanza una Bola de nieve a",
                objetivo);
    }
}