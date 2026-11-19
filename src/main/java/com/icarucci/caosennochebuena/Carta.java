package com.icarucci.caosennochebuena;

import java.util.Objects;

// Cada copia tiene su propio identificador.
// Dos bolas de nieve comparten el tipo, pero no el identificador.
public record Carta(String identificador, TipoCarta tipo) {

    public Carta {
        Objects.requireNonNull(identificador, "La carta necesita un identificador");
        Objects.requireNonNull(tipo, "La carta necesita un tipo");

        if (identificador.isBlank()) {
            throw new IllegalArgumentException(
                    "El identificador de la carta no puede estar vacío");
        }
    }
}