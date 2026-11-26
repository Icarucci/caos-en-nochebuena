package com.icarucci.caosennochebuena.controlador;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.icarucci.caosennochebuena.modelo.TipoSala;
import com.icarucci.caosennochebuena.servicio.RegistroSalas;
import com.icarucci.caosennochebuena.websocket.JuegoWebSocket;
import com.icarucci.caosennochebuena.websocket.JuegoWebSocket.SalaPublica;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
public class SalasController {

    private final RegistroSalas registroSalas;
    private final JuegoWebSocket juegoWebSocket;

    public SalasController(RegistroSalas registroSalas,JuegoWebSocket juegoWebSocket) {
        this.registroSalas = registroSalas;
        this.juegoWebSocket = juegoWebSocket;
    }

    // Recibimos el tipo elegido. Si no se envía, usamos PRIVADA.
    @PostMapping("/api/salas")
    @ResponseStatus(HttpStatus.CREATED)
    public SalaCreada crearSala(
            @RequestParam(name = "tipo", defaultValue = "PRIVADA") TipoSala tipo) {
        return new SalaCreada(registroSalas.crear(tipo));
    }

    // Spring convierte este resultado en un JSON con el campo "codigo".
    public record SalaCreada(String codigo) {}

    // Permite comprobar si una sala guardada en el navegador sigue existiendo.
    @GetMapping("/api/salas/{codigo}/existe")
    public boolean existeSala(@PathVariable("codigo") String codigo) {
        return registroSalas.existe(codigo);
    }

    // La página consulta este endpoint para mostrar las salas públicas.
    @GetMapping("/api/salas/publicas")
    public java.util.List<JuegoWebSocket.SalaPublica> listarPublicas() {
        return juegoWebSocket.listarSalasPublicas();
    }
}