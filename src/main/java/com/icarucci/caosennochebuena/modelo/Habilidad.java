package com.icarucci.caosennochebuena.modelo;

// Cada opción guarda el nombre y la explicación que verá el jugador al elegir.
public enum Habilidad {

    A_FONDO(
            "A fondo",
            "Una vez en tu turno, descartá una carta adicional para lanzar "
                    + "una segunda Bola de nieve. Podés atacar al mismo jugador."),

    BOLA_PESADA(
            "Bola pesada",
            "Al lanzar una Bola de nieve, podés exigir que el objetivo "
                    + "descarte una carta adicional para usar Devolver."),

    GOLPE_AL_EQUIPO(
            "Golpe al equipo",
            "Si tu Bola de nieve le quita una vida a otro jugador, "
                    + "podés mandar su equipo al descarte."),

    ULTIMA_BARRERA(
            "Última barrera",
            "Cuando una Bola de nieve esté por quitarte una vida, "
                    + "podés descartar tu equipo para bloquearla."),

    REFLEJOS(
            "Reflejos",
            "Cuando te ataquen con una Bola de nieve, robá una carta. "
                    + "Si es Esquive o Devolver, podés usarla para defenderte. "
                    + "Si sale otra carta, descartala."),

    ME_METO_YO(
            "Me meto yo",
            "Cuando una Bola de nieve esté por quitarle una vida "
                    + "a otro jugador, podés perder esa vida en su lugar."),

    AGARRATE_FUERTE(
            "Agarrate fuerte",
            "Si te juegan Grinch o En tarlipes, podés guardar tu equipo "
                    + "en tu mano antes de que se lo lleven o lo descarten."),

    OJO_AL_MAZO(
            "Ojo al mazo",
            "Al comenzar tu turno, mirá cuatro cartas del mazo y quedate "
                    + "con dos. Las otras vuelven al mazo. "
                    + "Árbol navideño conserva su robo extra."),

    SEGUNDA_MANO(
            "Segunda mano",
            "Al comenzar tu turno, podés tomar la carta superior del descarte "
                    + "en lugar de la primera que robarías del mazo."),

    MANO_NUEVA(
            "Mano nueva",
            "Una vez en tu turno, descartá toda tu mano, con un mínimo "
                    + "de una carta, y robá la misma cantidad más una."),

    PASE_DE_EMERGENCIA(
            "Pase de emergencia",
            "Cuando ataquen a otro jugador, podés darle un Esquive "
                    + "o un Devolver de tu mano. Él decide si lo usa o lo guarda."),

    SOBREMESA(
            "Sobremesa",
            "Al finalizar tu turno, podés descartar cualquier carta de tu mano "
                    + "para recuperar una vida, hasta tu máximo."),

    TRATO_SECRETO(
            "Trato secreto",
            "Una vez en tu turno, elegí a otro jugador con cartas en la mano. "
                    + "Cada uno elige una carta propia y se la da al otro. "
                    + "El intercambio es obligatorio y ambos deben tener cartas."),

    ULTIMO_REGALO(
            "Último regalo",
            "Si te eliminan y la partida sigue, lanzá una última Bola de nieve "
                    + "a quien quieras, sin gastar una carta. "
                    + "El objetivo no puede defenderse."),

    MILAGRO_DE_LA_NAVIDAD(
            "Milagro de la Navidad",
            "Si al finalizar tu turno no tenés cartas en la mano, "
                    + "robá una carta del mazo."),

    TURBOMAN(
            "Turboman",
            "Al comenzar tu turno, podés robar una carta del mazo "
                    + "y otra al azar de la mano de otro jugador. "
                    + "Árbol navideño conserva su robo extra.");

    private final String nombre;
    private final String descripcion;

    Habilidad(String nombre, String descripcion) {
        this.nombre = nombre;
        this.descripcion = descripcion;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }
}