package com.icarucci.caosennochebuena.modelo.efecto;

import java.util.List;
import java.util.Objects;

public final class EfectoCuracion implements EfectoCarta {

    // Expresamos el alcance con nombres claros en lugar de un boolean.
    public enum Alcance {
        PROPIO,
        TODOS
    }

    private final int cantidad;
    private final Alcance alcance;
    private final String nombreCarta;

    public EfectoCuracion(
            int cantidad,
            Alcance alcance,
            String nombreCarta) {

        // Validamos la configuración al construir el efecto.
        if (cantidad <= 0) {
            throw new IllegalArgumentException(
                    "La cantidad de curación debe ser positiva");
        }

        this.cantidad = cantidad;
        this.alcance = Objects.requireNonNull(alcance);
        this.nombreCarta = Objects.requireNonNull(nombreCarta);
    }

    @Override
    public void aplicar(
            ContextoEfectoCarta contexto,
            String jugador,
            String objetivo) {

        // La comida individual afecta a quien la juega.
        // La grupal incluye a todos los participantes activos.
        List<String> destinatarios = alcance == Alcance.TODOS
                ? contexto.getParticipantesActivos()
                : List.of(jugador);

        // Registramos primero qué carta se utilizó.
        // Después aparecerán las recuperaciones de vidas efectivas.
        contexto.registrarEvento(
                jugador,
                "usa " + nombreCarta + ".",
                null);

        for (String destinatario : destinatarios) {
            // Partida aplica el límite de vidas y registra la curación.
            contexto.recuperarVidas(destinatario, cantidad);
        }
    }
}