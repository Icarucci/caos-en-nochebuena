package com.icarucci.caosennochebuena;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RepartidorDeHabilidades {

    public Map<String, List<Habilidad>> sortear(List<String> participantes) {
        // Comprobamos la cantidad y evitamos repartirle dos veces al mismo jugador.
        if (participantes.size() < 4 || participantes.size() > 8
                || participantes.stream().distinct().count() != participantes.size()) {
            throw new IllegalArgumentException(
                    "El reparto requiere entre 4 y 8 jugadores distintos.");
        }

        // Mezclamos las 16 habilidades en una única lista.
        List<Habilidad> habilidades =
                new ArrayList<>(List.of(Habilidad.values()));
        Collections.shuffle(habilidades);

        Map<String, List<Habilidad>> opcionesPorJugador = new LinkedHashMap<>();
        int posicion = 0;

        for (String identificador : participantes) {
            // Tomamos dos opciones y avanzamos para que nadie más las reciba.
            opcionesPorJugador.put(identificador, List.of(
                    habilidades.get(posicion),
                    habilidades.get(posicion + 1)));

            posicion += 2;
        }

        // Devolvemos un reparto que conserva las opciones asignadas.
        return Map.copyOf(opcionesPorJugador);
    }
}