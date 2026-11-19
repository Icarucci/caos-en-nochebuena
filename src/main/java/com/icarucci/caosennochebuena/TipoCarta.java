package com.icarucci.caosennochebuena;

public enum TipoCarta {

    // Ataques y defensas.
    BOLA_DE_NIEVE("Bola de nieve", CategoriaCarta.ATAQUE, 30),
    ESQUIVAR("Esquivar", CategoriaCarta.DEFENSA, 12),
    DEVOLVER("Devolver", CategoriaCarta.DEFENSA, 8),

    // Hay una copia de cada comida navideña.
    VITEL_TONE("Vitel toné", CategoriaCarta.COMIDA, 1),
    PIONONO("Pionono", CategoriaCarta.COMIDA, 1),
    ASADO("Asado", CategoriaCarta.COMIDA, 1),
    LECHON_CON_ENSALADA_RUSA(
            "Lechón con ensalada rusa", CategoriaCarta.COMIDA, 1),
    HUEVOS_RELLENOS("Huevos rellenos", CategoriaCarta.COMIDA, 1),
    PAN_DULCE("Pan dulce", CategoriaCarta.COMIDA, 1),
    TURRON("Turrón", CategoriaCarta.COMIDA, 1),
    GARRAPINADA("Garrapiñada", CategoriaCarta.COMIDA, 1),
    MANTECOL("Mantecol", CategoriaCarta.COMIDA, 1),
    ENSALADA_DE_FRUTAS_CON_HELADO(
            "Ensalada de frutas con helado", CategoriaCarta.COMIDA, 1),

    // Cartas de acción: 20 copias en total.
    AMIGO_INVISIBLE("Amigo invisible", CategoriaCarta.ACCION, 3),
    INTERCAMBIO_DE_REGALOS(
            "Intercambio de regalos", CategoriaCarta.ACCION, 3),
    CARBON("Carbón", CategoriaCarta.ACCION, 3),
    GUERRA_DE_NIEVE("Guerra de nieve", CategoriaCarta.ACCION, 3),
    TE_GUARDE_UN_PLATO("¡Te guardé un plato!", CategoriaCarta.ACCION, 2),
    CARTA_AL_POLO_NORTE("Carta al Polo Norte", CategoriaCarta.ACCION, 2),
    REGALO_EQUIVOCADO("Regalo equivocado", CategoriaCarta.ACCION, 2),
    APAGON_NAVIDENO("Apagón navideño", CategoriaCarta.ACCION, 2),

    // Equipos: 12 copias en total.
    BULLY_NAVIDENO("Bully navideño", CategoriaCarta.EQUIPO, 3),
    ARBOL_NAVIDENO("Árbol navideño", CategoriaCarta.EQUIPO, 3),
    LO_RECALENTADO_DE_ANOCHE(
            "Lo recalentado de anoche", CategoriaCarta.EQUIPO, 2),
    PINZA_DEL_ASADOR("Pinza del asador", CategoriaCarta.EQUIPO, 2),
    DOBLE_ENTREGA("Doble entrega", CategoriaCarta.EQUIPO, 2),

    // Cartas para quitar equipos: 8 copias en total.
    GRINCH("Grinch", CategoriaCarta.DESEQUIPAR, 4),
    EN_TARLIPES("En tarlipes", CategoriaCarta.DESEQUIPAR, 4);

    private final String nombre;
    private final CategoriaCarta categoria;
    private final int copiasEnMazo;

    TipoCarta(String nombre, CategoriaCarta categoria, int copiasEnMazo) {
        this.nombre = nombre;
        this.categoria = categoria;
        this.copiasEnMazo = copiasEnMazo;
    }

    public String getNombre() {
        return nombre;
    }

    public CategoriaCarta getCategoria() {
        return categoria;
    }

    public int getCopiasEnMazo() {
        return copiasEnMazo;
    }
}