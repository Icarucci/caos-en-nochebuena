package com.icarucci.caosennochebuena;

import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;



@Service
public class RegistroSalas {

    // Guardamos las salas en memoria mientras el servidor está encendido.
    // Asociamos cada código con el tipo de sala.
    private final Map<String, TipoSala> tiposPorSala = new ConcurrentHashMap<>();
    // Registramos cuáles de las salas creadas ya iniciaron la partida.
    private final RepartidorDeRoles repartidor = new RepartidorDeRoles();
    private final Map<String, Partida> partidasPorSala = new ConcurrentHashMap<>();

    public String crear() {
        // Conservamos el ingreso actual mientras agregamos el selector en pantalla.
        return crear(TipoSala.PRIVADA);
    }

    public String crear(TipoSala tipo) {
        if (tipo == null) {
            throw new IllegalArgumentException("El tipo de sala es obligatorio.");
        }

        String codigo;

        do {
            codigo = UUID.randomUUID()
                    .toString()
                    .substring(0, 6)
                    .toUpperCase(Locale.ROOT);

            // Reservamos el código junto con el tipo.
            // Si ya existe, generamos otro.
        } while (tiposPorSala.putIfAbsent(codigo, tipo) != null);

        return codigo;
    }

    public boolean existe(String codigo) {
        return tiposPorSala.containsKey(codigo);
    }

    public TipoSala tipoDe(String codigo) {
        return tiposPorSala.get(codigo);
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

    public List<String> codigosPublicos() {
        // Devolvemos únicamente los códigos de las salas públicas.
        return tiposPorSala.entrySet().stream()
                .filter(entrada -> entrada.getValue() == TipoSala.PUBLICA)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    public void eliminar(String codigo) {
        // Quitamos la sala y cualquier partida asociada.
        tiposPorSala.remove(codigo);
        partidasPorSala.remove(codigo);
    }
}