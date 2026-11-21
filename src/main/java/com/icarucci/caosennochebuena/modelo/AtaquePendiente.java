package com.icarucci.caosennochebuena.modelo;

// Representa una bola que espera defensa o una posible ayuda.
public record AtaquePendiente(
        String identificador,
        String atacante,
        String objetivo,
        long vencimientoRespuesta,

        // Si no tiene defensa propia, se abre la espera breve de ayuda.
        boolean sinDefensaPropia) {
}