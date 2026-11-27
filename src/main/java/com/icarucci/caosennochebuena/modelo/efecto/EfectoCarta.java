package com.icarucci.caosennochebuena.modelo.efecto;

// Contrato común para los comportamientos de las cartas.
// Cada implementación tendrá su propia clase y su propio archivo.
public interface EfectoCarta {

    // Se ejecuta después de que Partida valida la solicitud
    // y consume la carta correspondiente.
    //
    // jugador: quien está usando la carta.
    // objetivo: destinatario del efecto, si corresponde; puede ser null.
    void aplicar(
            ContextoEfectoCarta contexto,
            String jugador,
            String objetivo);
}