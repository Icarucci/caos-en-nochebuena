package com.icarucci.caosennochebuena;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RepartidorDeRoles {

    public List<Rol> sortear(int cantidadJugadores) {
        if (cantidadJugadores < 4 || cantidadJugadores > 8) {
            throw new IllegalArgumentException("La partida requiere de 4 a 8 jugadores.");
        }

        // Estos cuatro roles están presentes en todas las partidas.
        List<Rol> roles = new ArrayList<>(List.of(
                Rol.PAPA_NOEL, Rol.RODOLFO, Rol.KRAMPUS, Rol.LADRON
        ));

        // Cada jugador adicional suma un rol al reparto.
        if (cantidadJugadores >= 5) roles.add(Rol.ARREPENTIDO);
        if (cantidadJugadores >= 6) roles.add(Rol.LADRON);
        if (cantidadJugadores >= 7) roles.add(Rol.ELFO);
        if (cantidadJugadores == 8) roles.add(Rol.ELFO);

        // Mezclamos los roles antes de asignarlos.
        Collections.shuffle(roles);
        return List.copyOf(roles);
    }
}