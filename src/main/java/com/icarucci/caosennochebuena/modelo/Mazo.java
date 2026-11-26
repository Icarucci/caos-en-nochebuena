package com.icarucci.caosennochebuena.modelo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class Mazo {

    // Robamos desde el principio de esta cola.
    private final Deque<Carta> cartas = new ArrayDeque<>();

    // El último elemento es la carta visible sobre el descarte.
    private final List<Carta> descarte = new ArrayList<>();

    public Mazo() {
        List<Carta> cartasIniciales = new ArrayList<>();

        // Creamos la cantidad de copias definida en el catálogo.
        for (TipoCarta tipo : TipoCarta.values()) {
            for (int copia = 0; copia < tipo.getCopiasEnMazo(); copia++) {
                cartasIniciales.add(
                        new Carta(UUID.randomUUID().toString(), tipo));
            }
        }

        Collections.shuffle(cartasIniciales);
        cartas.addAll(cartasIniciales);
    }

    public Optional<Carta> robar() {
        // Si se agotó el mazo principal, reciclamos todo el descarte.
        if (cartas.isEmpty()) {
            reciclarDescarte();
        }

        // Puede no quedar ninguna carta si todas están en manos
        // o equipadas. En ese caso devolvemos Optional.empty().
        return Optional.ofNullable(cartas.pollFirst());
    }

    public List<Carta> robar(int cantidad) {
        if (cantidad < 0) {
            throw new IllegalArgumentException(
                    "La cantidad de cartas no puede ser negativa");
        }

        List<Carta> robadas = new ArrayList<>();

        for (int posicion = 0; posicion < cantidad; posicion++) {
            Optional<Carta> carta = robar();

            if (carta.isEmpty()) {
                break;
            }

            robadas.add(carta.get());
        }

        // Entregamos una lista que no permite modificar su contenido.
        return List.copyOf(robadas);
    }

    public void descartar(Carta carta) {
        // Partida será responsable de retirar primero la carta
        // de la mano o del equipo de su dueño.
        descarte.add(Objects.requireNonNull(carta));
    }

    private void reciclarDescarte() {
        if (descarte.isEmpty()) {
            return;
        }

        // Mezclamos y reutilizamos las mismas cartas:
        // sus identificadores se conservan.
        Collections.shuffle(descarte);
        cartas.addAll(descarte);
        descarte.clear();
    }

    public int getCantidadCartas() {
        return cartas.size();
    }

    public int getCantidadDescarte() {
        return descarte.size();
    }

    public Optional<Carta> getUltimaDescartada() {
        if (descarte.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(descarte.get(descarte.size() - 1));
    }
}