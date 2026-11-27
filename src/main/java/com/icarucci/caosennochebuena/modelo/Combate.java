package com.icarucci.caosennochebuena.modelo;

import java.time.Duration;
import java.util.UUID;

// Cada partida tendrá su propio combate.
// Esta clase administra la espera de una bola y sus devoluciones.
//
// Partida coordina su uso dentro de sus métodos synchronized:
// no agregamos un segundo bloqueo independiente.
public final class Combate {

    // Con defensa propia, el objetivo dispone de un minuto.
    private static final long DURACION_RESPUESTA_NANOS =
            Duration.ofMinutes(1).toNanos();

    // Sin defensa propia, dejamos cinco segundos para una posible ayuda.
    private static final long DURACION_AYUDA_NANOS =
            Duration.ofSeconds(5).toNanos();

    private AtaquePendiente ataquePendiente;

    public boolean hayAtaquePendiente() {
        return ataquePendiente != null;
    }

    public AtaquePendiente getAtaquePendiente() {
        // AtaquePendiente es un record inmutable:
        // consultarlo no permite cambiar sus datos.
        return ataquePendiente;
    }

    public void abrirAtaque(
            String atacante,
            String objetivo,
            boolean tieneDefensaPropia) {

        long duracion = tieneDefensaPropia
                ? DURACION_RESPUESTA_NANOS
                : DURACION_AYUDA_NANOS;

        // Tanto el lanzamiento como cada devolución generan
        // un identificador y un plazo de respuesta nuevos.
        ataquePendiente = new AtaquePendiente(
                UUID.randomUUID().toString(),
                atacante,
                objetivo,
                System.nanoTime() + duracion,
                !tieneDefensaPropia);
    }

    public void cerrarAtaque() {
        // Recuperar el reloj del turno será responsabilidad de Partida.
        ataquePendiente = null;
    }

    public boolean vencioRespuesta() {
        // Sin ataque no existe una respuesta pendiente que pueda vencer.
        return ataquePendiente != null
                && System.nanoTime()
                        - ataquePendiente.vencimientoRespuesta() >= 0;
    }

    public int getSegundosRestantesDeRespuesta() {
        if (ataquePendiente == null) {
            return 0;
        }

        long restantes =
                ataquePendiente.vencimientoRespuesta() - System.nanoTime();

        if (restantes <= 0) {
            return 0;
        }

        // Redondeamos hacia arriba para mostrar el segundo completo
        // al comenzar y evitar que el contador llegue a cero antes de tiempo.
        long segundoEnNanos = Duration.ofSeconds(1).toNanos();

        return (int) (
                (restantes + segundoEnNanos - 1) / segundoEnNanos);
    }
}