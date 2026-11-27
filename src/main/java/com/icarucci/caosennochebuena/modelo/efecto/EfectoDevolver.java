package com.icarucci.caosennochebuena.modelo.efecto;

public final class EfectoDevolver implements EfectoCarta {

    @Override
    public void aplicar(
            ContextoEfectoCarta contexto,
            String jugador,
            String objetivo) {

        // Partida determina el objetivo a partir del ataque vigente:
        // es quien lanzó o devolvió la bola anteriormente.
        //
        // Abrimos una nueva respuesta sin cerrar el ataque anterior,
        // para conservar pausado el tiempo del turno original.
        contexto.abrirAtaque(jugador, objetivo);

        contexto.registrarEvento(
                jugador,
                "devuelve la bola a",
                objetivo);
    }
}