package com.icarucci.caosennochebuena.modelo.efecto;

import java.util.List;

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

    // Devuelve los identificadores de quienes siguen participando.
    // Un jugador desconectado conserva su participación.
    List<String> getParticipantesActivos();

    // Recupera vidas sin superar el máximo del rol.
    // Devuelve cuántas vidas se recuperaron realmente.
    int recuperarVidas(String jugador, int cantidad);

    // Incorpora cartas del mazo a la mano del jugador.
    // Devuelve la cantidad realmente robada si el mazo no alcanza.
    int robarCartas(String jugador, int cantidad);

    // Aplica daño sin abrir una respuesta ni activar efectos de represalia.
    // Devuelve la cantidad de daño realmente aplicada.
    int causarDanioDirecto(String objetivo, int cantidad);
}