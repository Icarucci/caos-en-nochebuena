package com.icarucci.caosennochebuena.modelo.efecto;

import com.icarucci.caosennochebuena.modelo.ResultadoBolaDeNieve;

public final class EfectoDobleEntrega implements EfectoAlFinalizarBola {

    @Override
    public boolean permiteSegundaBola(
            ResultadoBolaDeNieve resultado,
            int bolasLanzadas) {

        // Solo concede una oportunidad después de la primera bola.
        // La segunda nunca puede habilitar una tercera.
        return bolasLanzadas == 1
                && resultado == ResultadoBolaDeNieve.BLOQUEADA;
    }

    @Override
    public void activar(
            ContextoEfectoCarta contexto,
            String jugador) {

        // El mensaje particular de la carta pertenece a su efecto.
        contexto.registrarEvento(
                jugador,
                "activa Doble entrega: puede lanzar una segunda Bola de nieve si la primera falla.",
                null);
    }
}