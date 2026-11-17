package com.icarucci.caosennochebuena;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;



@Service
public class RegistroSalas {

    // Guardamos las salas en memoria mientras el servidor está encendido.
    // El conjunto permite crear y consultar salas desde varias conexiones.
    private final Set<String> codigos = ConcurrentHashMap.newKeySet();
    // Registramos cuáles de las salas creadas ya iniciaron la partida.
    private final RepartidorDeRoles repartidor = new RepartidorDeRoles();
    private final Map<String, Partida> partidasPorSala = new ConcurrentHashMap<>();

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
        return partidasPorSala.containsKey(codigo);
    }

    public boolean iniciar(String codigo, List<String> participantes) {
        // Solo iniciamos una sala que exista y todavía esté esperando.
        if (!existe(codigo) || estaIniciada(codigo)) {
            return false;
        }

        // Sorteamos un rol para cada participante.
        List<Rol> roles = repartidor.sortear(participantes.size());
        Map<String, Rol> reparto = new LinkedHashMap<>();

        for (int i = 0; i < participantes.size(); i++) {
            reparto.put(participantes.get(i), roles.get(i));
        }

        // Una vez completo el reparto, creamos y guardamos la partida.
        return partidasPorSala.putIfAbsent(codigo, new Partida(reparto)) == null;
    }

    public Rol rolDe(String codigo, String identificador) {
        Partida partida = partidasPorSala.get(codigo);
        return partida == null ? null : partida.rolDe(identificador);
    }

    // El servidor consulta la partida para procesar las confirmaciones.
    public Partida partidaDe(String codigo) {
        return partidasPorSala.get(codigo);
    }
}