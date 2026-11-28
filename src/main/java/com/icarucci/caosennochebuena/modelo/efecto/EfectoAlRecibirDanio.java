package com.icarucci.caosennochebuena.modelo.efecto;

// Comportamientos activados después del daño de una Bola de nieve.
// El daño directo de una represalia no ejecuta este contrato.
public interface EfectoAlRecibirDanio {

    void aplicar(
            ContextoEfectoCarta contexto,
            String jugador,
            String atacante);
}