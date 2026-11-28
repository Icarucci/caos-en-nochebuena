package com.icarucci.caosennochebuena.modelo.efecto;

import com.icarucci.caosennochebuena.modelo.ResultadoBolaDeNieve;

// Beneficios que dependen del resultado completo de una bola.
public interface EfectoAlFinalizarBola {

    boolean permiteSegundaBola(
            ResultadoBolaDeNieve resultado,
            int bolasLanzadas);

    // Ejecuta la activación después de que Partida concede el beneficio.
    // La consulta permiteSegundaBola no debe generar mensajes.
    void activar(ContextoEfectoCarta contexto, String jugador);
}