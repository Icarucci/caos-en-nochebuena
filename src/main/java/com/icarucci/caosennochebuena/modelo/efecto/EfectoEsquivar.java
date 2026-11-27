package com.icarucci.caosennochebuena.modelo.efecto;

public final class EfectoEsquivar implements EfectoCarta {

    @Override
    public void aplicar(
            ContextoEfectoCarta contexto,
            String jugador,
            String objetivo) {

        // La defensa bloquea la bola sin causar daño.
        // Cerrar el ataque permite continuar el turno que estaba pausado.
        contexto.cerrarAtaque();

        // Este efecto no necesita un objetivo adicional:
        // quien usa Esquivar es quien estaba recibiendo el ataque.
        contexto.registrarEvento(
                jugador,
                "usa Esquivar y bloquea la bola.",
                null);
    }
}