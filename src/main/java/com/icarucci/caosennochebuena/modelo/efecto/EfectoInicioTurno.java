package com.icarucci.caosennochebuena.modelo.efecto;

// Contrato para comportamientos que se ejecutan al comenzar un turno.
// Es distinto de jugar una carta desde la mano.
public interface EfectoInicioTurno {

    void aplicar(ContextoEfectoCarta contexto, String jugador);
}