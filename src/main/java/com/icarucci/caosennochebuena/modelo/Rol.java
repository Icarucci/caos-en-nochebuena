package com.icarucci.caosennochebuena.modelo;

// Cada rol define lo que verá el jugador cuando se le asigne.
public enum Rol {
    PAPA_NOEL("Papá Noel", 4, "Sobreviví hasta el final del turno 24."),
    RODOLFO("Rodolfo", 3, "Ayudá a Papá Noel a ganar."),
    KRAMPUS("Krampus", 3, "Quedá como el último jugador en pie."),
    LADRON("Ladrón de la Navidad", 3, "Eliminá a Papá Noel."),
    ARREPENTIDO("Arrepentido", 3,
            "Del turno 1 al 12 ganás si cae Papá Noel. "
            + "Del 13 al 24 ganás si gana Papá Noel."),
    ELFO("Elfo", 3, "Ayudá a Papá Noel a ganar.");

    private final String nombre;
    private final int vidasIniciales;
    private final String objetivo;

    Rol(String nombre, int vidasIniciales, String objetivo) {
        this.nombre = nombre;
        this.vidasIniciales = vidasIniciales;
        this.objetivo = objetivo;
    }

    public String getNombre() {
        return nombre;
    }

    public int getVidasIniciales() {
        return vidasIniciales;
    }

    public String getObjetivo() {
        return objetivo;
    }
}