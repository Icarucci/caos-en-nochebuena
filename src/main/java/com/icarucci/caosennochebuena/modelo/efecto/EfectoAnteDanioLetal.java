package com.icarucci.caosennochebuena.modelo.efecto;

// Efectos automáticos que pueden impedir una eliminación por daño.
public interface EfectoAnteDanioLetal {

    // Devuelve true si consiguió salvar al jugador.
    boolean aplicar(ContextoEfectoCarta contexto, String jugador);
}