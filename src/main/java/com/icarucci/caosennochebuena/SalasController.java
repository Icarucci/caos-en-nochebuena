package com.icarucci.caosennochebuena;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
public class SalasController {

    private final RegistroSalas registroSalas;

    public SalasController(RegistroSalas registroSalas) {
        this.registroSalas = registroSalas;
    }

    // Este endpoint será el que use el botón «Crear sala».
    @PostMapping("/api/salas")
    @ResponseStatus(HttpStatus.CREATED)
    public SalaCreada crearSala() {
        return new SalaCreada(registroSalas.crear());
    }

    // Spring convierte este resultado en un JSON con el campo "codigo".
    public record SalaCreada(String codigo) {}

    // Permite comprobar si una sala guardada en el navegador sigue existiendo.
    @GetMapping("/api/salas/{codigo}/existe")
    public boolean existeSala(@PathVariable("codigo") String codigo) {
        return registroSalas.existe(codigo);
    }
}