package com.icarucci.caosennochebuena.modelo.efecto;

public final class EfectoEnTarlipes implements EfectoCarta {

    @Override
    public void aplicar(
            ContextoEfectoCarta contexto,
            String jugador,
            String objetivo) {

        contexto.registrarEvento(
                jugador,
                "usa En tarlipes contra",
                objetivo);

        // A diferencia de Grinch, el equipo termina en el descarte.
        contexto.desequipar(objetivo);
    }
}