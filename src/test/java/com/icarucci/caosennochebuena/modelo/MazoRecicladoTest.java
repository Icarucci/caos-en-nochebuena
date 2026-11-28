package com.icarucci.caosennochebuena.modelo;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MazoRecicladoTest {

    @Test
    void reciclaLasAnterioresYConservaLaUltimaDescartada() {
        Mazo mazo = new Mazo();

        // Agotamos el mazo sin depender de su orden aleatorio.
        List<Carta> iniciales = mazo.robar(mazo.getCantidadCartas());

        Carta primera = iniciales.get(0);
        Carta segunda = iniciales.get(1);
        Carta ultima = iniciales.get(2);

        mazo.descartar(primera);
        mazo.descartar(segunda);
        mazo.descartar(ultima);

        // Pedimos tres, pero solo pueden reciclarse las dos anteriores.
        List<Carta> robadas = mazo.robar(3);

        assertEquals(2, robadas.size());

        // Comprobamos los ejemplares sin exigir un orden tras la mezcla.
        assertEquals(
                Set.of(primera, segunda),
                new HashSet<>(robadas));

        assertEquals(0, mazo.getCantidadCartas());
        assertEquals(1, mazo.getCantidadDescarte());
        assertEquals(ultima, mazo.getUltimaDescartada().orElseThrow());
    }

    @Test
    void noRobaLaUnicaCartaDelDescarte() {
        Mazo mazo = new Mazo();
        List<Carta> iniciales = mazo.robar(mazo.getCantidadCartas());

        Carta ultima = iniciales.get(0);
        mazo.descartar(ultima);

        // La única carta descartada permanece disponible para
        // efectos que interactúen específicamente con el descarte.
        assertTrue(mazo.robar().isEmpty());
        assertEquals(1, mazo.getCantidadDescarte());
        assertEquals(ultima, mazo.getUltimaDescartada().orElseThrow());
    }
}