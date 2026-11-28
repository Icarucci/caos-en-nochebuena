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
import java.util.EnumMap;

import com.icarucci.caosennochebuena.modelo.efecto.ContextoEfectoCarta;
import com.icarucci.caosennochebuena.modelo.efecto.EfectoCarta;
import com.icarucci.caosennochebuena.modelo.efecto.EfectoBolaDeNieve;
import com.icarucci.caosennochebuena.modelo.efecto.EfectoEsquivar;
import com.icarucci.caosennochebuena.modelo.efecto.EfectoDevolver;
import com.icarucci.caosennochebuena.modelo.efecto.EfectoCuracion;
import com.icarucci.caosennochebuena.modelo.efecto.EfectoInicioTurno;
import com.icarucci.caosennochebuena.modelo.efecto.EfectoArbolNavideno;


public class Partida {

    // Cada turno dura un minuto. Medimos el tiempo transcurrido
    // con nanoTime para no depender de cambios en la hora del servidor.
    private static final long DURACION_TURNO_NANOS = Duration.ofMinutes(1).toNanos();

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
    // Cada partida administra sus ataques mediante su propio Combate.
    private final Combate combate = new Combate();

    // Tiempo que conservamos del turno mientras se resuelve el ataque.
    private long tiempoDeTurnoPausado;

    // Límite básico de una Bola de nieve por turno.
    // Usar Devolver no consume este permiso.
    private boolean bolaDeNieveUsada;
    // Cada partida tiene su propio mazo y descarte.
    private final Mazo mazo = new Mazo();

    // Conserva el equipo activo de cada jugador, separado de su mano.
    private final Equipamiento equipamiento = new Equipamiento();

    // Cada identificador tiene una mano independiente.
    // Solo Partida modifica estas listas.
    private final Map<String, List<Carta>> manosPorJugador = new HashMap<>();

    // Acumulamos sucesos públicos hasta que WebSocket los publique.
    // No guardamos aquí el contenido de las manos.
    private final List<EventoActividad> eventosPendientes = new ArrayList<>();

    // Asociamos los tipos implementados con sus comportamientos.
    // Los efectos no guardan información particular de un jugador.
    // Construimos una sola vez la asociación entre cartas y efectos.
    private final Map<TipoCarta, EfectoCarta> efectosPorTipo = crearEfectosDeCartas();

    // Los beneficios de inicio de turno tienen su propio registro.
    // Equipar una carta no ejecuta automáticamente estos efectos.
    private final Map<TipoCarta, EfectoInicioTurno> efectosDeInicioTurno =
            Map.of(
                    TipoCarta.ARBOL_NAVIDENO,
                    new EfectoArbolNavideno());

    // Adaptador privado entre los efectos y el estado de esta partida.
    // No se entrega al navegador ni se expone mediante un getter.
    private final ContextoEfectoCarta contextoEfectos = new ContextoEfectoCarta() {

                @Override
                public void abrirAtaque(String atacante, String objetivo) {
                    // Partida conoce la mano del objetivo y consulta
                    // si dispone de alguna defensa implementada.
                    //
                    // Combate crea el ataque y determina su plazo.
                    combate.abrirAtaque(
                            atacante,
                            objetivo,
                            tieneDefensaDisponible(objetivo));
                }

                @Override
                public void cerrarAtaque() {
                    // Recupera el tiempo restante del turno original.
                    cerrarAtaquePendiente();
                }

                @Override
                public void registrarEvento(
                        String jugador,
                        String descripcion,
                        String objetivo) {

                    // Delegamos en el registro público que ya utiliza el chat.
                    Partida.this.registrarEvento(
                            jugador, descripcion, objetivo);
                }

                @Override
                public List<String> getParticipantesActivos() {
                    // Devolvemos una lista sin exponer la colección interna.
                    // sigueParticipando excluye abandonados y eliminados,
                    // pero conserva a quienes están reconectando.
                    return roles.keySet().stream()
                            .filter(Partida.this::sigueParticipando)
                            .toList();
                }

                @Override
                public int recuperarVidas(String jugador, int cantidad) {
                    // Curar no puede revivir a un eliminado
                    // ni afectar a alguien que abandonó.
                    if (fase != Fase.EN_JUEGO
                            || cantidad <= 0
                            || !sigueParticipando(jugador)) {
                        return 0;
                    }

                    int vidasActuales = vidasPorJugador.get(jugador);
                    int vidasMaximas = roles.get(jugador).getVidasIniciales();

                    // Calculamos el espacio disponible antes de sumar.
                    // Así nunca superamos las vidas máximas del rol.
                    int recuperadas = Math.min(
                            cantidad,
                            Math.max(0, vidasMaximas - vidasActuales));

                    if (recuperadas > 0) {
                        vidasPorJugador.put(
                                jugador,
                                vidasActuales + recuperadas);

                        registrarEvento(
                                jugador,
                                "recupera " + recuperadas
                                        + " vida(s). Ahora tiene "
                                        + (vidasActuales + recuperadas) + ".",
                                null);
                    }

                    return recuperadas;
                }

                                @Override
                public int robarCartas(String jugador, int cantidad) {
                    // Los efectos no pueden entregar cartas a alguien
                    // eliminado o que haya abandonado.
                    if (fase != Fase.EN_JUEGO
                            || cantidad <= 0
                            || !sigueParticipando(jugador)) {
                        return 0;
                    }

                    List<Carta> mano = manosPorJugador.get(jugador);

                    if (mano == null) {
                        return 0;
                    }

                    // Mazo administra también el reciclado del descarte.
                    // Puede devolver menos cartas si no quedan suficientes.
                    List<Carta> robadas = mazo.robar(cantidad);
                    mano.addAll(robadas);

                    // La cantidad es pública; los tipos y los identificadores
                    // de las cartas robadas siguen siendo privados.
                    if (!robadas.isEmpty()) {
                        registrarEvento(
                                jugador,
                                "roba " + robadas.size() + " carta(s).",
                                null);
                    }

                    return robadas.size();
                }
    };


    private Map<TipoCarta, EfectoCarta> crearEfectosDeCartas() {
        // EnumMap utiliza los valores de TipoCarta como claves.
        Map<TipoCarta, EfectoCarta> efectos =
                new EnumMap<>(TipoCarta.class);

        efectos.put(TipoCarta.BOLA_DE_NIEVE, new EfectoBolaDeNieve());
        efectos.put(TipoCarta.ESQUIVAR, new EfectoEsquivar());
        efectos.put(TipoCarta.DEVOLVER, new EfectoDevolver());

        // Todas las comidas actuales recuperan una vida.
        // Compartimos la implementación y configuramos su alcance y nombre.
        for (TipoCarta tipo : TipoCarta.values()) {
            if (tipo.getCategoria() != CategoriaCarta.COMIDA) {
                continue;
            }

            EfectoCuracion.Alcance alcance = esComidaGrupal(tipo)
                    ? EfectoCuracion.Alcance.TODOS
                    : EfectoCuracion.Alcance.PROPIO;

            efectos.put(
                    tipo,
                    new EfectoCuracion(1, alcance, tipo.getNombre()));
        }

        // El registro queda protegido contra modificaciones posteriores.
        return Map.copyOf(efectos);
    }

    private boolean esComidaGrupal(TipoCarta tipo) {
        return tipo == TipoCarta.ASADO
                || tipo == TipoCarta.LECHON_CON_ENSALADA_RUSA;
    }

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
                combate.getAtaquePendiente() != null
                && (identificador.equals(combate.getAtaquePendiente().atacante())
                    || identificador.equals(combate.getAtaquePendiente().objetivo())
                    || abandonoElJugadorDeTurno);

        jugadoresQueAbandonaron.add(identificador);
        descartarManoDe(identificador);

        // Abandonar libera también la zona de equipo.
        desequipar(identificador);

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
            if (combate.getAtaquePendiente() != null) {
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
                || combate.getAtaquePendiente() != null
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

        // Robo normal: dos cartas al comenzar el turno.
        mano.addAll(mazo.robar(2));

        registrarEvento(
                identificador,
                "comienza el turno " + numeroDeTurno + "/24.",
                null);

        // Solo consultamos el equipo que ya estaba colocado.
        Carta equipo = equipamiento.getEquipoDe(identificador);

        if (equipo != null) {
            EfectoInicioTurno efecto =
                    efectosDeInicioTurno.get(equipo.tipo());

            // Los equipos sin un beneficio de inicio de turno
            // no ejecutan ninguna acción en este momento.
            if (efecto != null) {
                efecto.aplicar(contextoEfectos, identificador);
            }
        }

        // El minuto empieza después del robo normal y sus adicionales.
        iniciarRelojDeTurno();
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

    public synchronized Carta getEquipoDe(String jugador) {
        // El equipo activo es información pública.
        // Carta es inmutable: devolverla no permite cambiar su contenido.
        return equipamiento.getEquipoDe(jugador);
    }

    public synchronized boolean puedeEquiparCarta(
            String jugador, String identificadorCarta) {

        // Permite informar al frontend qué cartas puede habilitar.
        return buscarEquipoUtilizable(jugador, identificadorCarta) != null;
    }

    public synchronized boolean equiparCarta(
            String jugador, String identificadorCarta) {

        // Validamos toda la solicitud antes de tocar el equipo anterior.
        Carta nuevoEquipo =
                buscarEquipoUtilizable(jugador, identificadorCarta);

        if (nuevoEquipo == null) {
            return false;
        }

        // Reutilizamos la acción desequipar:
        // retira el equipo anterior y lo envía al descarte.
        // Si no había equipo, no modifica nada.
        desequipar(jugador);

        // La carta nueva sale de la mano y ocupa la zona de equipo.
        // No se descarta al equiparla.
        manosPorJugador.get(jugador).remove(nuevoEquipo);
        equipamiento.colocarEquipo(jugador, nuevoEquipo);

        registrarEvento(
                jugador,
                "equipa " + nuevoEquipo.tipo().getNombre() + ".",
                null);

        return true;
    }

    private Carta buscarEquipoUtilizable(
            String jugador, String identificadorCarta) {

        // Solo permitimos equipar durante el turno propio,
        // sin ataques pendientes y antes de que venza el tiempo.
        if (fase != Fase.EN_JUEGO
                || combate.hayAtaquePendiente()
                || !sigueParticipando(jugador)
                || !jugador.equals(getJugadorDeTurno())
                || identificadorCarta == null
                || System.nanoTime() - vencimientoDelTurno >= 0) {
            return null;
        }

        // Comprobamos el ejemplar exacto y su categoría en la mano real.
        return manosPorJugador.getOrDefault(jugador, List.of()).stream()
                .filter(carta ->
                        carta.identificador().equals(identificadorCarta)
                        && carta.tipo().getCategoria() == CategoriaCarta.EQUIPO)
                .findFirst()
                .orElse(null);
    }

    private boolean desequipar(String jugador) {
        // Retirar y descartar son pasos distintos.
        // Un futuro robo reutilizará retirarEquipoDe sin descartar la carta.
        Carta equipoRetirado = equipamiento.retirarEquipoDe(jugador);

        if (equipoRetirado == null) {
            return false;
        }

        mazo.descartar(equipoRetirado);

        registrarEvento(
                jugador,
                "desequipa " + equipoRetirado.tipo().getNombre()
                        + " y lo envía al descarte.",
                null);

        return true;
    }

    public synchronized boolean puedeUsarComida(
            String jugador, String identificadorCarta) {

        // Sirve para informar al frontend qué cartas puede habilitar.
        // Al jugar una comida volvemos a comprobar todas las condiciones.
        return buscarComidaUtilizable(jugador, identificadorCarta) != null;
    }

    public synchronized boolean usarComida(
            String jugador, String identificadorCarta) {

        Carta comida = buscarComidaUtilizable(jugador, identificadorCarta);

        if (comida == null) {
            return false;
        }

        EfectoCarta efecto = efectosPorTipo.get(comida.tipo());

        // Una carta sin efecto implementado no debe consumirse.
        if (efecto == null) {
            return false;
        }

        // Todas las condiciones fueron comprobadas antes de modificar la mano.
        manosPorJugador.get(jugador).remove(comida);
        mazo.descartar(comida);

        // Comer no consume el permiso de lanzar una Bola de nieve.
        // El reloj del turno continúa corriendo.
        efecto.aplicar(contextoEfectos, jugador, null);

        return true;
    }

    private Carta buscarComidaUtilizable(
            String jugador, String identificadorCarta) {

        // Solo se puede comer durante el turno propio,
        // mientras siga participando y no haya un ataque pendiente.
        if (fase != Fase.EN_JUEGO
                || combate.hayAtaquePendiente()
                || !sigueParticipando(jugador)
                || !jugador.equals(getJugadorDeTurno())
                || identificadorCarta == null
                || System.nanoTime() - vencimientoDelTurno >= 0) {
            return null;
        }

        // Buscamos el ejemplar exacto en la mano real del jugador.
        Carta comida = manosPorJugador.getOrDefault(jugador, List.of())
                .stream()
                .filter(carta ->
                        carta.identificador().equals(identificadorCarta)
                        && carta.tipo().getCategoria() == CategoriaCarta.COMIDA)
                .findFirst()
                .orElse(null);

        if (comida == null) {
            return null;
        }

        // Una comida grupal puede servir aunque quien la juega
        // tenga todas sus vidas: alcanza con que otro pueda recuperarse.
        boolean alguienPuedeRecuperarse = esComidaGrupal(comida.tipo())
                ? roles.keySet().stream()
                        .anyMatch(this::puedeRecuperarVidas)
                : puedeRecuperarVidas(jugador);

        // Si nadie puede beneficiarse, conservamos la carta en la mano.
        return alguienPuedeRecuperarse ? comida : null;
    }

    private boolean puedeRecuperarVidas(String jugador) {
        // Excluye eliminados y abandonados; incluye desconectados.
        return sigueParticipando(jugador)
                && vidasPorJugador.get(jugador)
                        < roles.get(jugador).getVidasIniciales();
    }

    public synchronized boolean lanzarBolaDeNieve(String atacante, String identificadorCarta, String objetivo) {

        // Solo puede lanzar el jugador de turno, mientras no haya
        // otro ataque pendiente y conserve su permiso para lanzar.
        if (fase != Fase.EN_JUEGO
                || combate.getAtaquePendiente() != null
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
        // Ejecutamos el comportamiento asociado a la carta ya validada.
        efectosPorTipo.get(bola.tipo()).aplicar(contextoEfectos, atacante, objetivo);

        return true;
    }

    public synchronized boolean responderAtaque(String identificador,String identificadorAtaque,String identificadorCarta) {

        // Solo puede responder el objetivo del ataque actual.
        // El identificador del ataque evita aceptar respuestas atrasadas.
        if (fase != Fase.EN_JUEGO
                || combate.getAtaquePendiente() == null
                || combate.getAtaquePendiente().sinDefensaPropia()
                || !combate.getAtaquePendiente().identificador().equals(identificadorAtaque)
                || !combate.getAtaquePendiente().objetivo().equals(identificador)
                || !sigueParticipando(identificador)
                || identificadorCarta == null) {
            return false;
        }

        // Si venció la respuesta, su resultado lo procesa el servidor.
        // Combate determina si el plazo ya terminó.
        if (combate.vencioRespuesta()) {
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
                && !sigueParticipando(combate.getAtaquePendiente().atacante())) {
            return false;
        }

        // Resolvemos el destinatario antes de que el efecto cambie
        // o cierre el ataque. Esquivar no utiliza este argumento.
        String objetivoDelEfecto = combate.getAtaquePendiente().atacante();

        // Todas las validaciones anteriores se conservan.
        // Consumimos únicamente la carta que pertenece a quien responde.
        mano.remove(defensa);
        mazo.descartar(defensa);

        // Cada defensa resuelve su comportamiento en su propia clase.
        efectosPorTipo.get(defensa.tipo())
                .aplicar(contextoEfectos, identificador, objetivoDelEfecto);

        return true;
    }

    public synchronized boolean resolverAtaqueSiVencio() {
        if (fase != Fase.EN_JUEGO || !combate.hayAtaquePendiente()) {
            return false;
        }

        if (!combate.vencioRespuesta()) {
            return false;
        }

        // Partida sigue aplicando el daño y comprobando las eliminaciones.
        resolverDanioPendiente();
        return true;
    }

    private void resolverDanioPendiente() {
        String objetivo = combate.getAtaquePendiente().objetivo();

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
        // Combate finaliza la espera; Partida recupera el reloj del turno.
        combate.cerrarAtaque();

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
                && combate.getAtaquePendiente() == null
                && !bolaDeNieveUsada
                && sigueParticipando(identificador)
                && identificador.equals(getJugadorDeTurno())
                && System.nanoTime() - vencimientoDelTurno < 0
                && manosPorJugador.getOrDefault(identificador, List.of()).stream()
                        .anyMatch(carta -> carta.tipo() == TipoCarta.BOLA_DE_NIEVE);
    }


    public synchronized AtaquePendiente getAtaquePendiente() {
        // Consultamos el ataque administrado por Combate.
        // El record es inmutable: no permite modificar sus datos.
        return combate.getAtaquePendiente();
    }

    public synchronized int getSegundosRestantesDeRespuesta() {
        // Fuera del juego no mostramos un plazo de respuesta activo.
        if (fase != Fase.EN_JUEGO) {
            return 0;
        }

        return combate.getSegundosRestantesDeRespuesta();
    }


    private void iniciarRelojDeTurno() {
        vencimientoDelTurno = System.nanoTime() + DURACION_TURNO_NANOS;
    }

    public synchronized int getSegundosRestantesDelTurno() {
        if (fase != Fase.EN_JUEGO) {
            return 0;
        }

        // Mientras se responde, el reloj del turno muestra el tiempo pausado.
        long restantes = combate.getAtaquePendiente() != null
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
        if (fase != Fase.EN_JUEGO || combate.getAtaquePendiente() != null) {
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

        // También se descarta el equipo al eliminarse por daño o inactividad.
        desequipar(identificador);
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