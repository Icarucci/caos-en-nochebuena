package com.icarucci.caosennochebuena;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class RegistroSalas {

    // Guardamos las salas en memoria mientras el servidor está encendido.
    // El conjunto permite crear y consultar salas desde varias conexiones.
    private final Set<String> codigos = ConcurrentHashMap.newKeySet();
    // Registramos cuáles de las salas creadas ya iniciaron la partida.
    private final Set<String> salasIniciadas = ConcurrentHashMap.newKeySet();

    public String crear() {
        String codigo;

        // Generamos un código corto para compartir.
        // Si ya existe, probamos otro hasta poder reservar uno nuevo.
        do {
            codigo = UUID.randomUUID()
                    .toString()
                    .substring(0, 6)
                    .toUpperCase(Locale.ROOT);
        } while (!codigos.add(codigo));

        return codigo;
    }

    public boolean existe(String codigo) {
        return codigos.contains(codigo);
    }

    public boolean estaIniciada(String codigo) {
        return salasIniciadas.contains(codigo);
    }

    public boolean iniciar(String codigo) {
        // add devuelve false si la sala ya se había iniciado.
        return existe(codigo) && salasIniciadas.add(codigo);
    }
}