package com.icarucci.caosennochebuena.modelo;

// Representa un mensaje público del historial de una sala.
// Al ser un record, sus datos no cambian después de crearlo.
public record MensajeSala(Tipo tipo, String texto) {

    // El servidor distingue las conversaciones de los eventos del juego.
    public enum Tipo {
        CHAT,
        SISTEMA
    }
}