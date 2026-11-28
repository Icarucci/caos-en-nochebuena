package com.icarucci.caosennochebuena.modelo.efecto;

public final class EfectoArbolNavideno implements EfectoInicioTurno {

    @Override
    public void aplicar(ContextoEfectoCarta contexto, String jugador) {
        // El equipo permanece colocado después de utilizar su beneficio.
        contexto.registrarEvento(
                jugador,
                "activa Árbol navideño y roba una carta adicional al inicio del turno.",
                null);

        // El contexto incorpora la carta y registra la cantidad robada.
        contexto.robarCartas(jugador, 1);
    }
}