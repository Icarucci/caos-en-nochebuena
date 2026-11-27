package com.icarucci.caosennochebuena.modelo.efecto;

// Define las operaciones que un efecto puede solicitar a la partida.
// No expone mapas de jugadores, manos ni otros datos modificables.
public interface ContextoEfectoCarta {

    // Abre un ataque con un identificador y un plazo de respuesta nuevos.
    // También se utiliza cuando una bola cambia de dirección.
    void abrirAtaque(String atacante, String objetivo);

    // Finaliza el ataque pendiente y recupera el tiempo pausado del turno.
    void cerrarAtaque();

    // Registra un suceso público sin incluir información privada.
    // El objetivo puede ser null cuando el mensaje no involucra a otro jugador.
    void registrarEvento(
            String jugador,
            String descripcion,
            String objetivo);
}