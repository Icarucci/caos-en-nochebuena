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
        // También cubre un mazo que ya estaba vacío antes de este robo.
        if (cartas.isEmpty()) {
            reciclarDescarte();
        }

        Carta robada = cartas.pollFirst();

        // Al retirar la última carta, intentamos formar inmediatamente
        // el siguiente mazo, conservando la carta superior del descarte.
        if (robada != null && cartas.isEmpty()) {
            reciclarDescarte();
        }

        // Si no había cartas reciclables, el robo puede quedar sin carta.
        return Optional.ofNullable(robada);
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
        // La carta superior debe permanecer en el descarte.
        // Con cero o una carta no hay ninguna disponible para reciclar.
        if (descarte.size() <= 1) {
            return;
        }

        // Apartamos la última carta antes de mezclar las demás.
        Carta ultimaDescartada = descarte.remove(descarte.size() - 1);

        // Reutilizamos los mismos ejemplares, conservando sus identificadores.
        Collections.shuffle(descarte);
        cartas.addAll(descarte);

        // El descarte queda únicamente con la carta que estaba visible.
        descarte.clear();
        descarte.add(ultimaDescartada);
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