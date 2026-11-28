package com.icarucci.caosennochebuena.modelo.efecto;

// Efectos activados cuando una Bola de nieve causa daño.
// Se ejecutan antes de descartar la mano por eliminación.
public interface EfectoAlCausarDanio {

    void aplicar(
            ContextoEfectoCarta contexto,
            String jugador,
            String objetivo);
}