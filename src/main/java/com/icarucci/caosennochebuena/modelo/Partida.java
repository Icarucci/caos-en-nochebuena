package com.icarucci.caosennochebuena.modelo;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.icarucci.caosennochebuena.servicio.RepartidorDeHabilidades;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.time.Duration;
import java.util.UUID;

public class Partida {

    // Cada turno dura un minuto. Medimos el tiempo transcurrido
    // con nanoTime para no depender de cambios en la hora del servidor.
    private static final long DURACION_TURNO_NANOS = Duration.ofMinutes(1).toNanos();

    // La respuesta tiene su propio minuto, separado del tiempo del turno.
    private static final long DURACION_RESPUESTA_NANOS = Duration.ofMinutes(1).toNanos();    

    // Tiempo para que otros intervengan cuando el objetivo no puede defenderse.
    private static final long DURACION_AYUDA_NANOS = Duration.ofSeconds(5).toNanos();

    public enum Fase {
        REPARTO_DE_ROLES,
        ELECCION_DE_HABILIDAD,
        PREPARACION_COMPLETA,
        EN_JUEGO,
        TERMINADA
    }

    private final Map<String, Rol> roles;
    // Las vidas actuales pueden cambiar; el rol y sus vidas iniciales no.
    private final Map<String, Integer> vidasPorJugador = new HashMap<>();
    // Las opciones se sortean una sola vez y se conservan durante la partida.
    private final Map<String, List<Habilidad>> opcionesPorJugador;

    // Registramos la habilidad que elige cada participante.
    private final Map<String, Habilidad> habilidadesElegidas = new HashMap<>();
    private final Set<String> jugadoresQueConfirmaron = new HashSet<>();
    // Conservamos su rol, pero dejamos de esperar su confirmación.
    private final Set<String> jugadoresQueAbandonaron = new HashSet<>();
    // Cuenta los turnos propios que vencieron consecutivamente.
    private final Map<String, Integer> turnosPerdidos = new HashMap<>();
    // La eliminación no borra el rol ni la habilidad del jugador.
    private final Set<String> jugadoresEliminados = new HashSet<>();
    private Fase fase = Fase.REPARTO_DE_ROLES;
    private ResultadoPartida resultado = ResultadoPartida.EN_CURSO;
    // Permanece vacío mientras se eligen las habilidades.
    // Una vez sorteado, conservamos el mismo orden durante toda la partida.
    private List<String> ordenDeTurnos = List.of();
    // Durante la preparación todavía no hay un turno activo.
    private int numeroDeTurno = 0;

    // Posición del jugador actual dentro del orden sorteado.
    private int posicionDeTurno = 0;
    // Instante límite del turno actual, según el reloj monotónico del servidor.
    private long vencimientoDelTurno;
    // Mientras sea null, no hay una bola esperando respuesta.
    private AtaquePendiente ataquePendiente;

    // Tiempo que conservamos del turno mientras se resuelve el ataque.
    private long tiempoDeTurnoPausado;

    // Límite básico de una Bola de nieve por turno.
    // Usar Devolver no consume este permiso.
    private boolean bolaDeNieveUsada;
    // Cada partida tiene su propio mazo y descarte.
    private final Mazo mazo = new Mazo();

    // Cada identificador tiene una mano independiente.
    // Solo Partida modifica estas listas.
    private final Map<String, List<Carta>> manosPorJugador = new HashMap<>();

    // Acumulamos sucesos públicos hasta que WebSocket los publique.
    // No guardamos aquí el contenido de las manos.
    private final List<EventoActividad> eventosPendientes = new ArrayList<>();

    public Partida(Map<String, Rol> roles) {
        this.roles = Map.copyOf(roles);
        // Inicializamos una sola vez las vidas de cada participante.
        // Reconectarse no vuelve a ejecutar este constructor.
        this.roles.forEach((identificador, rol) ->
                vidasPorJugador.put(identificador, rol.getVidasIniciales()));

        // Usamos los mismos identificadores del reparto de roles.
        // Las opciones quedan guardadas hasta que llegue la etapa de mostrarlas.
        RepartidorDeHabilidades repartidor = new RepartidorDeHabilidades();
        this.opcionesPorJugador = repartidor.sortear(List.copyOf(roles.keySet()));
    }

    public Rol rolDe(String identificador) {
        return roles.get(identificador);
    }

    public synchronized boolean confirmarRol(String identificador) {
        if (fase != Fase.REPARTO_DE_ROLES
                || !roles.containsKey(identificador)
                || jugadoresQueAbandonaron.contains(identificador)) {
            return false;
        }

        if (!jugadoresQueConfirmaron.add(identificador)) {
            return false;
        }

        avanzarSiTodosConfirmaron();
        return true;
    }

    public synchronized void registrarAbandono(String identificador) {
        if (identificador == null
                || !roles.containsKey(identificador)
                || jugadoresQueAbandonaron.contains(identificador)) {
            return;
        }

        // Guardamos estas condiciones antes de modificar los participantes.
        boolean abandonoElJugadorDeTurno =
                fase == Fase.EN_JUEGO
                && identificador.equals(getJugadorDeTurno());

        boolean cancelaAtaque =
                ataquePendiente != null
                && (identificador.equals(ataquePendiente.atacante())
                    || identificador.equals(ataquePendiente.objetivo())
                    || abandonoElJugadorDeTurno);

        jugadoresQueAbandonaron.add(identificador);
        descartarManoDe(identificador);

        if (cancelaAtaque) {
            // Las cartas utilizadas siguen en el descarte.
            // Cerramos la espera y recuperamos el tiempo pausado.
            cerrarAtaquePendiente();
        }

        // Durante la preparación dejamos de esperar sus confirmaciones.
        avanzarSiTodosConfirmaron();
        avanzarSiTodosEligieron();

        // El abandono puede determinar una victoria.
        evaluarResultado();

        if (fase == Fase.TERMINADA) {
            // También cerramos cualquier espera si el abandono de otro
            // participante terminó la partida, por ejemplo, Papá Noel.
            if (ataquePendiente != null) {
                cerrarAtaquePendiente();
            }

            return;
        }

        if (fase == Fase.EN_JUEGO && abandonoElJugadorDeTurno) {
            // El siguiente jugador recibe sus cartas y su propio minuto.
            avanzarTurno();
        }
    }

    private void avanzarSiTodosConfirmaron() {
        if (fase != Fase.REPARTO_DE_ROLES) {
            return;
        }

        // Necesitamos que quede alguien participando para avanzar.
        boolean quedaAlguien = jugadoresQueAbandonaron.size() < roles.size();

        // Cada participante debe haber confirmado o abandonado explícitamente.
        boolean todosListos = roles.keySet().stream()
                .allMatch(identificador ->
                        jugadoresQueConfirmaron.contains(identificador)
                        || jugadoresQueAbandonaron.contains(identificador));

        if (quedaAlguien && todosListos) {
            fase = Fase.ELECCION_DE_HABILIDAD;
        }
    }

    // Permite recuperar la confirmación del jugador después de reconectar.
    public synchronized boolean confirmoRol(String identificador) {
        return jugadoresQueConfirmaron.contains(identificador);
    }

    public synchronized List<Habilidad> opcionesDe(String identificador) {
        // Cada jugador puede recibir únicamente sus propias opciones.
        return opcionesPorJugador.getOrDefault(identificador, List.of());
    }

    public synchronized boolean elegirHabilidad(String identificador, Habilidad habilidad) {

        // Solo se puede elegir durante esta etapa y mientras se siga participando.
        if (fase != Fase.ELECCION_DE_HABILIDAD
                || jugadoresQueAbandonaron.contains(identificador)) {
            return false;
        }

        // La elección es definitiva y tiene que ser una de las dos opciones asignadas.
        if (habilidadesElegidas.containsKey(identificador)
                || !opcionesDe(identificador).contains(habilidad)) {
            return false;
        }

        habilidadesElegidas.put(identificador, habilidad);
        avanzarSiTodosEligieron();
        return true;
    }

    public synchronized Habilidad habilidadDe(String identificador) {
        // Al reconectar, podemos recuperar la elección ya confirmada.
        return habilidadesElegidas.get(identificador);
    }

    private void avanzarSiTodosEligieron() {
    if (fase != Fase.ELECCION_DE_HABILIDAD) {
        return;
    }

    boolean quedaAlguien = jugadoresQueAbandonaron.size() < roles.size();

    // Los jugadores que abandonaron ya no tienen que elegir.
    boolean todosEligieron = roles.keySet().stream().allMatch(identificador ->
            jugadoresQueAbandonaron.contains(identificador)|| habilidadesElegidas.containsKey(identificador));

        if (!quedaAlguien || !todosEligieron) {
            return;
        }

        // Antes de preparar los turnos, comprobamos que los abandonos
        // no hayan determinado ya el final de la partida.
        if (evaluarResultado() != ResultadoPartida.EN_CURSO) {
            return;
        }

        // El sorteo ocurre después de la última elección.
        // Así nadie conoce su posición al elegir la habilidad.
        sortearOrdenDeTurnos();

        // Repartimos después de elegir las habilidades y antes del primer turno.
        repartirManosIniciales();

        // Papá Noel ocupa la posición cero del orden sorteado.
        // La partida comienza después de la última elección de habilidad.
        posicionDeTurno = 0;
        numeroDeTurno = 1;
        fase = Fase.EN_JUEGO;

        // Papá Noel recibe las dos cartas de su primer turno
        // después de que todos recibieron las cuatro iniciales.
        iniciarTurnoActual();
    }

    private void sortearOrdenDeTurnos() {
    // Evitamos repetir el sorteo si ya existe un orden.
    if (!ordenDeTurnos.isEmpty()) {
        return;
    }

    // Solo participan quienes no abandonaron.
    // Los desconectados conservan su lugar y su posición.
    List<String> participantes = new ArrayList<>(
            roles.keySet().stream()
                    .filter(identificador ->
                            !jugadoresQueAbandonaron.contains(identificador))
                    .toList());

    // La partida sigue en curso, por lo que Papá Noel debe participar.
    String papaNoel = participantes.stream().filter(identificador -> roles.get(identificador) == Rol.PAPA_NOEL).findFirst().orElseThrow(() ->
            new IllegalStateException("No se puede iniciar sin Papa Noel"));

        // Sorteamos únicamente a los demás jugadores.
        participantes.remove(papaNoel);
        Collections.shuffle(participantes);

        // Papá Noel ocupa siempre la primera posición.
        participantes.add(0, papaNoel);

        // Guardamos una lista que no pueda modificarse desde afuera.
        ordenDeTurnos = List.copyOf(participantes);
    }

    public synchronized List<String> getOrdenDeTurnos() {
        // Antes del sorteo devuelve una lista vacía.
        // Después permite consultar el orden ya establecido.
        return ordenDeTurnos;
    }

    public synchronized int getNumeroDeTurno() {
        return numeroDeTurno;
    }

    public synchronized String getJugadorDeTurno() {
        // Fuera de la etapa de juego no hay nadie autorizado a jugar.
        if (fase != Fase.EN_JUEGO) {
            return null;
        }

        return ordenDeTurnos.get(posicionDeTurno);
    }

    public synchronized boolean terminarTurno(String identificador) {
        // Conservamos la llamada existente para una mano sin exceso.
        return terminarTurno(identificador, List.of());
    }

    public synchronized boolean terminarTurno(String identificador, List<String> cartasADescartar) {

        if (fase != Fase.EN_JUEGO
                || ataquePendiente != null
                || !ordenDeTurnos.get(posicionDeTurno).equals(identificador)
                || jugadoresQueAbandonaron.contains(identificador)
                || jugadoresEliminados.contains(identificador)) {
            return false;
        }

        if (System.nanoTime() - vencimientoDelTurno >= 0) {
            return false;
        }

        if (evaluarResultado() != ResultadoPartida.EN_CURSO) {
            return false;
        }

        List<Carta> mano = manosPorJugador.get(identificador);
        int exceso = Math.max(0, mano.size() - 4);

        // Debe elegir exactamente el exceso, sin repetir identificadores.
        if (cartasADescartar == null || cartasADescartar.size() != exceso) {
            return false;
        }

        Set<String> seleccionadas = new HashSet<>(cartasADescartar);

        if (seleccionadas.size() != exceso) {
            return false;
        }

        // Comprobamos que todas las cartas seleccionadas sean propias.
        // Todavía no modificamos la mano.
        List<Carta> descarteElegido = mano.stream()
                .filter(carta -> seleccionadas.contains(carta.identificador()))
                .toList();

        if (descarteElegido.size() != exceso) {
            return false;
        }

        // Solo después de validar toda la selección movemos las cartas.
        for (Carta carta : descarteElegido) {
            mano.remove(carta);
            mazo.descartar(carta);
        }

        // Finalizar normalmente reinicia los vencimientos consecutivos.
        turnosPerdidos.remove(identificador);

        registrarEvento(identificador,"termina su turno y descarta " + descarteElegido.size() + " carta(s).",null);

        avanzarTurno();
        return true;
    }

    private void avanzarTurno() {
        // Después de completar el turno 24, Papá Noel gana si la
        // partida continúa en curso. Los resultados anteriores tienen prioridad.
        if (evaluarResultado() != ResultadoPartida.EN_CURSO) {
            return;
        }

        if (numeroDeTurno >= 24) {
            resultado = ResultadoPartida.VICTORIA_PAPA_NOEL;
            fase = Fase.TERMINADA;
            return;
        }

        // Recorremos el orden fijo y saltamos a quienes abandonaron.
        // Un desconectado conserva su turno: luego agregaremos el temporizador.
        do {
            posicionDeTurno = (posicionDeTurno + 1) % ordenDeTurnos.size();
        // Saltamos tanto a quienes abandonaron como a los eliminados.
        } while (jugadoresQueAbandonaron.contains(
                ordenDeTurnos.get(posicionDeTurno))
                || jugadoresEliminados.contains(
                        ordenDeTurnos.get(posicionDeTurno)));

        // Cada jugador que comienza a jugar consume un turno individual,
        // aunque todavía no se haya completado una vuelta de la mesa.
        numeroDeTurno++;
        // El siguiente jugador roba y comienza su minuto.
        iniciarTurnoActual();
    }


    private void iniciarTurnoActual() {
        // El nuevo turno recupera el permiso para lanzar una bola.
        bolaDeNieveUsada = false;
        String identificador = ordenDeTurnos.get(posicionDeTurno);
        List<Carta> mano = manosPorJugador.get(identificador);

        // El robo ocurre una sola vez al comenzar el turno.
        // Reconectarse o consultar el estado no vuelve a entregar cartas.
        mano.addAll(mazo.robar(2));

        // El minuto empieza después del robo.
        iniciarRelojDeTurno();
        registrarEvento(identificador,"comienza el turno " + numeroDeTurno + "/24.",null);
    }

    private boolean sigueParticipando(String identificador) {
    // Estar desconectado no elimina al jugador de la partida.
    return identificador != null
            && roles.containsKey(identificador)
            && !jugadoresQueAbandonaron.contains(identificador)
            && !jugadoresEliminados.contains(identificador)
            && vidasPorJugador.getOrDefault(identificador, 0) > 0;
    }

    private boolean tieneDefensaDisponible(String identificador) {
        // Por ahora comprobamos las defensas que ya podemos ejecutar.
        // Aquí incorporaremos las habilidades, equipos y otras cartas
        // cuando implementemos sus condiciones y efectos.
        return manosPorJugador.getOrDefault(identificador, List.of()).stream()
                .anyMatch(carta ->
                        carta.tipo() == TipoCarta.ESQUIVAR
                        || carta.tipo() == TipoCarta.DEVOLVER);
    }

    private AtaquePendiente crearAtaquePendiente(
            String atacante, String objetivo) {

        boolean sinDefensaPropia = !tieneDefensaDisponible(objetivo);

        // Con defensa propia dispone del minuto habitual.
        // Sin defensa, solo esperamos los cinco segundos de posible ayuda.
        long duracion = sinDefensaPropia
                ? DURACION_AYUDA_NANOS
                : DURACION_RESPUESTA_NANOS;

        return new AtaquePendiente(
                UUID.randomUUID().toString(),
                atacante,
                objetivo,
                System.nanoTime() + duracion,
                sinDefensaPropia);
    }

    public synchronized boolean lanzarBolaDeNieve(String atacante, String identificadorCarta, String objetivo) {

        // Solo puede lanzar el jugador de turno, mientras no haya
        // otro ataque pendiente y conserve su permiso para lanzar.
        if (fase != Fase.EN_JUEGO
                || ataquePendiente != null
                || bolaDeNieveUsada
                || !sigueParticipando(atacante)
                || !atacante.equals(getJugadorDeTurno())) {
            return false;
        }

        // El objetivo debe seguir participando y ser otro jugador.
        if (!sigueParticipando(objetivo) || atacante.equals(objetivo)) {
            return false;
        }

        List<Carta> mano = manosPorJugador.get(atacante);

        // Buscamos la copia exacta dentro de la mano del atacante.
        // No alcanza con que el navegador diga que tiene una bola.
        Carta bola = mano.stream()
                .filter(carta ->
                        carta.identificador().equals(identificadorCarta)
                        && carta.tipo() == TipoCarta.BOLA_DE_NIEVE)
                .findFirst()
                .orElse(null);

        if (bola == null) {
            return false;
        }

        // Comprobamos el límite de tiempo antes de modificar el estado.
        if (System.nanoTime() - vencimientoDelTurno >= 0) {
            return false;
        }

        // Todas las validaciones pasaron: guardamos el tiempo restante
        // y consumimos la carta y el permiso de ataque.
        pausarRelojDeTurno();
        mano.remove(bola);
        mazo.descartar(bola);
        bolaDeNieveUsada = true;

        // Abrimos una espera nueva, con su propio identificador y plazo.
        // Las vidas todavía no cambian: primero puede haber una defensa.
        ataquePendiente = crearAtaquePendiente(atacante, objetivo);
        registrarEvento(atacante, "lanza una Bola de nieve a", objetivo);

        return true;
    }

    public synchronized boolean responderAtaque(String identificador,String identificadorAtaque,String identificadorCarta) {

        // Solo puede responder el objetivo del ataque actual.
        // El identificador del ataque evita aceptar respuestas atrasadas.
        if (fase != Fase.EN_JUEGO
                || ataquePendiente == null
                || ataquePendiente.sinDefensaPropia()
                || !ataquePendiente.identificador().equals(identificadorAtaque)
                || !ataquePendiente.objetivo().equals(identificador)
                || !sigueParticipando(identificador)
                || identificadorCarta == null) {
            return false;
        }

        // Si venció la respuesta, su resultado lo procesa el servidor.
        if (System.nanoTime() - ataquePendiente.vencimientoRespuesta() >= 0) {
            return false;
        }

        // Una selección vacía significa aceptar el daño sin gastar defensa.
        if (identificadorCarta.isEmpty()) {
            resolverDanioPendiente();
            return true;
        }

        List<Carta> mano = manosPorJugador.get(identificador);

        // La defensa debe existir en la mano del jugador que responde.
        Carta defensa = mano.stream()
                .filter(carta ->
                        carta.identificador().equals(identificadorCarta)
                        && (carta.tipo() == TipoCarta.ESQUIVAR
                            || carta.tipo() == TipoCarta.DEVOLVER))
                .findFirst()
                .orElse(null);

        if (defensa == null) {
            return false;
        }

        // Para devolver, el otro participante debe seguir en juego.
        if (defensa.tipo() == TipoCarta.DEVOLVER
                && !sigueParticipando(ataquePendiente.atacante())) {
            return false;
        }

        // Validamos todo antes de consumir la carta.
        mano.remove(defensa);
        mazo.descartar(defensa);

        if (defensa.tipo() == TipoCarta.ESQUIVAR) {
            registrarEvento(identificador, "usa Esquivar y bloquea la bola.", null);
            // La bola queda bloqueada y continúa el mismo turno.
            cerrarAtaquePendiente();
        } else {
            // La bola regresa a quien la lanzó o devolvió anteriormente.
            // No descontamos otra Bola de nieve ni usamos otro turno.
            String nuevoObjetivo = ataquePendiente.atacante();

            // Comprobamos también las defensas del nuevo objetivo tras cada rebote.
            ataquePendiente = crearAtaquePendiente(identificador, nuevoObjetivo);

            registrarEvento(identificador, "devuelve la bola a", nuevoObjetivo);
            // Conservamos pausado el reloj original.
            // Cada rebote abre un nuevo minuto de respuesta.
        }

        return true;
    }

    public synchronized boolean resolverAtaqueSiVencio() {
        if (fase != Fase.EN_JUEGO || ataquePendiente == null) {
            return false;
        }

        if (System.nanoTime() - ataquePendiente.vencimientoRespuesta() < 0) {
            return false;
        }

        // No responder dentro del plazo equivale a recibir el daño.
        resolverDanioPendiente();
        return true;
    }

    private void resolverDanioPendiente() {
        String objetivo = ataquePendiente.objetivo();

        // Guardamos quién tenía el turno antes de aplicar el daño.
        // Una bola devuelta puede eliminar al jugador de turno.
        boolean objetivoTieneElTurno = objetivo.equals(getJugadorDeTurno());

        if (sigueParticipando(objetivo)) {
            int vidasRestantes = Math.max(0, vidasPorJugador.get(objetivo) - 1);
            vidasPorJugador.put(objetivo, vidasRestantes);
            registrarEvento(objetivo,"recibe 1 de daño. Le quedan " + vidasRestantes + " vidas.",null);

            if (vidasRestantes == 0) {
                // Descarta su mano y comprueba si hay un ganador.
                eliminarJugador(objetivo);
            }
        }

        // Cerramos la espera y recuperamos el tiempo pausado,
        // salvo que la partida haya terminado.
        cerrarAtaquePendiente();

        if (fase == Fase.EN_JUEGO
                && objetivoTieneElTurno
                && !sigueParticipando(objetivo)) {
            // Si una devolución eliminó al jugador de turno,
            // comenzamos el siguiente en lugar de esperar al eliminado.
            avanzarTurno();
        }
    }

    private void pausarRelojDeTurno() {
        // Guardamos lo que quedaba, sin regalar otro minuto.
        tiempoDeTurnoPausado = Math.max(
                0, vencimientoDelTurno - System.nanoTime());
    }

    private void cerrarAtaquePendiente() {
        ataquePendiente = null;

        // Solo reanudamos si la partida sigue en juego.
        if (fase == Fase.EN_JUEGO) {
            vencimientoDelTurno = System.nanoTime() + tiempoDeTurnoPausado;
        }

        tiempoDeTurnoPausado = 0;
    }

    public synchronized boolean puedeLanzarBolaDeNieve(String identificador) {
        // Este permiso sirve para dibujar los controles.
        // Al recibir la acción, lanzarBolaDeNieve vuelve a validarla.
        return fase == Fase.EN_JUEGO
                && ataquePendiente == null
                && !bolaDeNieveUsada
                && sigueParticipando(identificador)
                && identificador.equals(getJugadorDeTurno())
                && System.nanoTime() - vencimientoDelTurno < 0
                && manosPorJugador.getOrDefault(identificador, List.of()).stream()
                        .anyMatch(carta -> carta.tipo() == TipoCarta.BOLA_DE_NIEVE);
    }


    public synchronized AtaquePendiente getAtaquePendiente() {
        // El record es inmutable: consultarlo no permite modificar el ataque.
        return ataquePendiente;
    }

    public synchronized int getSegundosRestantesDeRespuesta() {
        if (fase != Fase.EN_JUEGO || ataquePendiente == null) {
            return 0;
        }

        long restantes = ataquePendiente.vencimientoRespuesta() - System.nanoTime();

        if (restantes <= 0) {
            return 0;
        }

        // Redondeamos hacia arriba, igual que en el reloj del turno.
        long segundoEnNanos = Duration.ofSeconds(1).toNanos();
        return (int) ((restantes + segundoEnNanos - 1) / segundoEnNanos);
    }


    private void iniciarRelojDeTurno() {
        vencimientoDelTurno = System.nanoTime() + DURACION_TURNO_NANOS;
    }

    public synchronized int getSegundosRestantesDelTurno() {
        if (fase != Fase.EN_JUEGO) {
            return 0;
        }

        // Mientras se responde, el reloj del turno muestra el tiempo pausado.
        long restantes = ataquePendiente != null
                ? tiempoDeTurnoPausado
                : vencimientoDelTurno - System.nanoTime();

        if (restantes <= 0) {
            return 0;
        }

        // Redondeamos hacia arriba para mostrar 01:00 al comenzar,
        // en vez de bajar inmediatamente a 00:59.
        long segundoEnNanos = Duration.ofSeconds(1).toNanos();
        return (int) ((restantes + segundoEnNanos - 1) / segundoEnNanos);
    }

    public synchronized boolean finalizarTurnoSiVencio() {
        // No finalizamos el turno mientras hay una respuesta pendiente.
        if (fase != Fase.EN_JUEGO || ataquePendiente != null) {
            return false;
        }

        if (System.nanoTime() - vencimientoDelTurno < 0) {
            return false;
        }

        String identificador = ordenDeTurnos.get(posicionDeTurno);

        // Sumamos únicamente cuando vence un turno propio.
        int perdidos = turnosPerdidos.getOrDefault(identificador, 0) + 1;
        turnosPerdidos.put(identificador, perdidos);
        registrarEvento(identificador,"pierde su turno por tiempo. Vencimientos consecutivos: " + perdidos + ".",null);


        if (perdidos >= 2) {
            // La inactividad utiliza la misma eliminación que usará el daño.
            eliminarJugador(identificador);
        } else {
            // Si venció su tiempo, no esperamos una selección:
            // descartamos el exceso al azar antes de avanzar.
            descartarExcesoAlAzar(identificador);
        }

        if (fase == Fase.EN_JUEGO) {
            avanzarTurno();
        }

        return true;
    }

    private void repartirManosIniciales() {
        // El orden ya contiene únicamente a quienes siguen participando.
        for (String identificador : ordenDeTurnos) {
            manosPorJugador.put(
                    identificador,
                    new ArrayList<>(mazo.robar(4)));
        }
    }

    public synchronized List<Carta> getManoDe(String identificador) {
        // Devolvemos una copia para impedir modificaciones desde afuera.
        // JuegoWebSocket deberá enviarla únicamente a su dueño.
        return List.copyOf(
                manosPorJugador.getOrDefault(identificador, List.of()));
    }

    public synchronized int getCantidadCartasDe(String identificador) {
        // Esta cantidad sí podrá mostrarse a los demás jugadores.
        return manosPorJugador.getOrDefault(identificador, List.of()).size();
    }

    public synchronized int getCantidadCartasEnMazo() {
        return mazo.getCantidadCartas();
    }

    public synchronized int getCantidadCartasEnDescarte() {
        return mazo.getCantidadDescarte();
    }

    private void descartarExcesoAlAzar(String identificador) {
        List<Carta> mano = manosPorJugador.get(identificador);
        int exceso = Math.max(0, mano.size() - 4);

        if (exceso == 0) {
            return;
        }

        // Mezclamos una copia para seleccionar el descarte al azar
        // sin alterar el orden de las cartas que conserva el jugador.
        List<Carta> candidatas = new ArrayList<>(mano);
        Collections.shuffle(candidatas);

        for (int posicion = 0; posicion < exceso; posicion++) {
            Carta carta = candidatas.get(posicion);
            mano.remove(carta);
            mazo.descartar(carta);
        }
    }

    private void descartarManoDe(String identificador) {
        List<Carta> mano = manosPorJugador.get(identificador);

        // Durante la preparación puede abandonar alguien que todavía
        // no recibió cartas.
        if (mano == null) {
            return;
        }

        // Las cartas vuelven al descarte y podrán reciclarse.
        for (Carta carta : mano) {
            mazo.descartar(carta);
        }

        mano.clear();
    }

    public synchronized ResultadoPartida evaluarResultado() {
        // Una vez decidido, el resultado queda guardado.
        if (resultado != ResultadoPartida.EN_CURSO) {
            return resultado;
        }

        // Los desconectados conservan su lugar. Excluimos a quienes abandonaron.
        List<Rol> rolesParticipantes = roles.entrySet().stream()
                // Solo los participantes que siguen en juego determinan el resultado.
                .filter(entrada ->!jugadoresQueAbandonaron.contains(entrada.getKey())&& !jugadoresEliminados.contains(entrada.getKey()))
                .map(Map.Entry::getValue)
                .toList();

        if (rolesParticipantes.isEmpty()) {
            resultado = ResultadoPartida.SIN_PARTICIPANTES;
        } else if (rolesParticipantes.size() == 1
                && rolesParticipantes.get(0) == Rol.KRAMPUS) {
            // Esta victoria tiene prioridad sobre la ausencia de Papá Noel.
            resultado = ResultadoPartida.VICTORIA_KRAMPUS;
        } else if (!rolesParticipantes.contains(Rol.PAPA_NOEL)) {
            resultado = ResultadoPartida.VICTORIA_LADRONES;
        } else if (!rolesParticipantes.contains(Rol.KRAMPUS)
                && !rolesParticipantes.contains(Rol.LADRON)) {
            resultado = ResultadoPartida.VICTORIA_PAPA_NOEL;
        }

        if (resultado != ResultadoPartida.EN_CURSO) {
            fase = Fase.TERMINADA;
        }

        return resultado;
    }

    public synchronized int getVidasDe(String identificador) {
        // Permite consultar las vidas sin modificar el mapa desde afuera.
        return vidasPorJugador.getOrDefault(identificador, 0);
    }

    private void eliminarJugador(String identificador) {
        // Evitamos procesar dos veces la eliminación del mismo jugador.
        if (!roles.containsKey(identificador)
                || !jugadoresEliminados.add(identificador)) {
            return;
        }

        // Conservamos el rol y la habilidad para recuperar su información
        // y determinar si comparte la victoria al terminar la partida.
        vidasPorJugador.put(identificador, 0);
        descartarManoDe(identificador);
        // El rol solo se hace público cuando el jugador queda eliminado.
        registrarEvento(identificador,"queda eliminado. Su rol era " + roles.get(identificador).getNombre() + ".",null);

        // Una eliminación puede decidir inmediatamente el resultado.
        evaluarResultado();
    }

    public synchronized boolean estaEliminado(String identificador) {
        // Permite informar la eliminación sin borrar el asiento
        // ni impedir que el jugador siga mirando la partida.
        return jugadoresEliminados.contains(identificador);
    }

    // Un evento puede involucrar a un jugador y, opcionalmente, a otro.
    // Conservamos identificadores internos; WebSocket resolverá sus nombres.
    public record EventoActividad(
            String jugador,
            String descripcion,
            String objetivo) {
    }

    private void registrarEvento(String jugador, String descripcion, String objetivo) {
        eventosPendientes.add(
                new EventoActividad(jugador, descripcion, objetivo));
    }

    public synchronized List<EventoActividad> retirarEventosPendientes() {
        // Cada evento se publica una sola vez.
        List<EventoActividad> eventos = List.copyOf(eventosPendientes);
        eventosPendientes.clear();
        return eventos;
    }

    public synchronized ResultadoPartida getResultado() {
        return resultado;
    }

    public synchronized Fase getFase() {
        return fase;
    }
}