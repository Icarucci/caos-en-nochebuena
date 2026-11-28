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

    // Retira el equipo del objetivo y lo envía al descarte.
    boolean desequipar(String objetivo);

    // Transfiere el equipo del objetivo a la mano del jugador.
    // No lo descarta ni lo equipa automáticamente.
    boolean robarEquipo(String jugador, String objetivo);

    // Deja al jugador con una vida antes de aplicar un daño letal.
    // No revive a jugadores que ya fueron eliminados.
    boolean evitarEliminacionConUnaVida(String jugador);

    // Descarta todas las cartas de la mano, sin retirar el equipo.
    void descartarMano(String jugador);

    // Transfiere a la mano del jugador una comida aleatoria del objetivo.
    // Devuelve false si no existe ninguna disponible.
    boolean robarRecuperacionAlAzar(String jugador, String objetivo);
}