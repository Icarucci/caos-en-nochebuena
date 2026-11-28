package com.icarucci.caosennochebuena.modelo;

// Describe cómo terminó la bola completa, incluidas sus devoluciones.
public enum ResultadoBolaDeNieve {

    // La bola terminó bloqueada, por ejemplo, mediante Esquivar.
    BLOQUEADA,

    // La bola alcanzó a un jugador.
    // Incluye el impacto que activa Lo recalentado de anoche.
    IMPACTO,

    // La resolución se interrumpió, por ejemplo, por un abandono.
    // Cancelar no equivale a bloquear y no habilita Doble entrega.
    CANCELADA
}