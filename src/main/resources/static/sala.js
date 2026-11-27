const porId = (id) => document.getElementById(id);
const acceso = porId("acceso");
const formularioIngreso = porId("formulario-ingreso");
const botonCrear = porId("crear-sala");
const campoNombre = porId("nombre");
const campoCodigo = porId("codigo");
const estado = porId("estado");
const panelRecuperar = porId("recuperar-partida");
const mesa = porId("mesa");
const lobby = porId("lobby");
const vistaPartida = porId("partida");
const botonIniciar = porId("iniciar-partida");
const formularioChat = porId("formulario-chat");
const campoChat = porId("mensaje-chat");
const chat = porId("chat");

const claveActual = "caos:sesion-actual";
const codigoEnUrl = new URLSearchParams(location.search).get("sala")?.trim().toUpperCase();
const nombreEnPestana = sessionStorage.getItem("nombreJugador") ?? "";
const claveJugador = (codigo, nombre) => `caos:jugador:${codigo}:${nombre.toLowerCase()}`;

let socket;
let jugador;
let iniciada = false;
let soyAnfitrion = false;
let nombrePapaNoel = "";
// El servidor informa el orden después de la elección de habilidades.
let ordenDeTurnos = [];
// Solo contiene cantidades públicas, nunca las cartas ajenas.
let cantidadesDeCartas = new Map();
// Conservamos la última lista para poder dibujarla al recibir el orden.
let ultimoTextoJugadores = "";
// Lista pública informada por el servidor.
let jugadoresEliminados = [];
// Solo contiene los roles que el servidor ya hizo públicos.
let rolesRevelados = new Map();
// Datos recibidos del servidor para dibujar los controles de combate.
let manoActual = [];
// El servidor informa cuáles de nuestras comidas podemos usar.
let comidasDisponibles = new Set();
let vidasPorNombre = new Map();
let ataqueActual = null;
let permisoParaAtacar = false;
let faseActual = "";
// Distingue el cierre definitivo de una desconexión recuperable.
let partidaFinalizada = false;

// Guardamos una copia por pestaña y otra por sala y nombre dentro del navegador.
// Así podemos probar varios nombres en pestañas del mismo navegador.
function guardarSesion(sesion) {
  const datos = JSON.stringify(sesion);
  sessionStorage.setItem(claveActual, datos);
  localStorage.setItem(claveActual, datos);
  localStorage.setItem(claveJugador(sesion.codigo, sesion.nombre), datos);
}

function leerSesion(almacenamiento, clave) {
  try {
    return JSON.parse(almacenamiento.getItem(clave) ?? "null");
  } catch {
    return null;
  }
}

function borrarAccesosDeSala(codigo) {
  // Quitamos los accesos de esta sala que conoce este navegador.
  // No borramos accesos correspondientes a otras salas.
  for (const almacenamiento of [sessionStorage, localStorage]) {
    for (const clave of Object.keys(almacenamiento)) {
      if (!clave.startsWith("caos:")) {
        continue;
      }

      const datos = leerSesion(almacenamiento, clave);

      if (datos?.codigo === codigo) {
        almacenamiento.removeItem(clave);
      }
    }
  }
}

function mostrarResultadoPartida(resultado, gano) {
  partidaFinalizada = true;
  // Al terminar, ninguna comida debe seguir habilitada.
  comidasDisponibles.clear();
  actualizarControlesComida();
  faseActual = "TERMINADA";
  permisoParaAtacar = false;
  ataqueActual = null;

  actualizarControlesCombate();

  porId("confirmar-rol").hidden = true;
  porId("seleccion-habilidad").hidden = true;
  porId("controles-turno").hidden = true;
  porId("terminar-turno").disabled = true;
  panelRecuperar.hidden = true;

  porId("mano-propia")
    .querySelectorAll("input[data-descarte]")
    .forEach((casilla) => {
      casilla.disabled = true;
    });

  if (jugador?.codigo) {
    borrarAccesosDeSala(jugador.codigo);
  }

  // La URL deja de apuntar a una sala que ya terminó.
  history.replaceState(null, "", location.pathname);

  const explicaciones = {
    VICTORIA_PAPA_NOEL: "Ganó el bando de Papá Noel.",
    VICTORIA_LADRONES: "Ganaron los Ladrones de la Navidad.",
    VICTORIA_KRAMPUS: "Ganó Krampus.",
    SIN_PARTICIPANTES: "La partida terminó sin participantes."
  };

  const resumen = explicaciones[resultado]
    ?? "La partida terminó y la sala ya no está disponible.";

  porId("resumen-resultado").textContent = resumen;
  porId("resultado-personal").textContent =
    resultado === "SIN_PARTICIPANTES" || gano == null
      ? ""
      : gano === "true" ? "¡Ganaste!" : "Perdiste.";

  estado.textContent = resumen;

  const cartel = porId("resultado-partida");

  if (!cartel.open) {
    cartel.showModal();
  }
}

function salirDePartidaFinalizada() {
  // Invalidamos el socket antes de cerrarlo para ignorar eventos tardíos.
  const conexion = socket;
  socket = null;

  if (conexion && conexion.readyState < WebSocket.CLOSING) {
    conexion.close(1000, "Salida de la partida finalizada");
  }

  if (jugador?.codigo) {
    borrarAccesosDeSala(jugador.codigo);
  }

  jugador = null;
  iniciada = false;
  faseActual = "";

  porId("resultado-partida").close();
  mesa.hidden = true;
  panelRecuperar.hidden = true;
  acceso.hidden = false;
  porId("salas-publicas").hidden = false;
  campoCodigo.value = "";

  history.replaceState(null, "", location.pathname);
  estado.textContent = "Creá una sala o ingresá un código.";

  actualizarSalasPublicas();
}

function borrarSesion(sesion) {
  const entradas = [
    [sessionStorage, claveActual],
    [localStorage, claveActual],
    [localStorage, claveJugador(sesion.codigo, sesion.nombre)],
  ];
  for (const [almacenamiento, clave] of entradas) {
    if (leerSesion(almacenamiento, clave)?.identificador === sesion.identificador) {
      almacenamiento.removeItem(clave);
    }
  }
}

async function mostrarRecuperacion(sesion) {
  jugador = sesion;
  mesa.hidden = true;
  panelRecuperar.hidden = true;
  estado.textContent = "Comprobando la sala guardada…";

  try {
    // Consultamos al servidor antes de ofrecer recuperar la partida.
    const respuesta = await fetch(
      `/api/salas/${encodeURIComponent(sesion.codigo)}/existe`,
      { cache: "no-store" }
    );

    if (!respuesta.ok) {
      throw new Error(`El servidor respondió con ${respuesta.status}`);
    }

    const existe = await respuesta.json();

    if (!existe) {
      // El servidor perdió la sala, por ejemplo, después de un reinicio.
      // Quitamos también su acceso guardado en este navegador.
      // También limpia accesos antiguos de una partida finalizada.
      borrarAccesosDeSala(sesion.codigo);
      jugador = null;
      acceso.hidden = false;
      campoCodigo.value = "";
      history.replaceState(null, "", location.pathname);
      estado.textContent = "La sala anterior ya no existe. Podés crear otra.";
      return;
    }

    // Solo mostramos los botones después de confirmar que la sala existe.
    panelRecuperar.hidden = false;
    porId("titulo-recuperacion").textContent = sesion.iniciada
      ? "Tenés una partida en curso"
      : "Tenés una sala de espera guardada";

    porId("detalle-partida").textContent =
      `${sesion.nombre} · Sala ${sesion.codigo}`;

    estado.textContent = "Tu lugar está guardado. Podés reconectar o abandonar.";
  } catch (error) {
    // Si falla la conexión, conservamos los datos para poder intentar después.
    console.error(error);
    acceso.hidden = false;
    estado.textContent =
      "No se pudo comprobar la sala. Revisá que el servidor esté encendido y recargá.";
  }
}

function marcarUrl(codigo) {
  history.replaceState(null, "", `?${new URLSearchParams({ sala: codigo })}`);
}

function pedirIngreso(codigo, nombre) {
  // Si volvés a ingresar el mismo código y nombre desde este navegador,
  // ofrecemos recuperar el lugar original.
  const conocida = leerSesion(localStorage, claveJugador(codigo, nombre));

  if (conocida) {
    mostrarRecuperacion(conocida);
    estado.textContent = "Podés recuperar tu lugar en esta sala.";
    return;
  }

  conectar({ codigo, nombre });
}

botonCrear.addEventListener("click", async () => {
  const nombre = campoNombre.value.trim();
  if (!campoNombre.reportValidity() || !nombre) {
    estado.textContent = "Escribí tu nombre para crear una sala.";
    return;
  }

  botonCrear.disabled = true;
  try {
    // Enviamos a Java el tipo seleccionado antes de crear la sala.
    const parametros = new URLSearchParams({
      tipo: porId("tipo-sala").value,
    });

    const respuesta = await fetch(`/api/salas?${parametros}`, {
      method: "POST",
    });
    if (!respuesta.ok) {
      throw new Error(`El servidor respondió con ${respuesta.status}`);
    }
    const { codigo } = await respuesta.json();
    conectar({ codigo, nombre });
  } catch (error) {
    console.error(error);
    estado.textContent = "No se pudo crear la sala. Probá de nuevo.";
  } finally {
    botonCrear.disabled = false;
  }
});

formularioIngreso.addEventListener("submit", (evento) => {
  evento.preventDefault();
  const nombre = campoNombre.value.trim();
  if (!nombre) {
    estado.textContent = "Escribí tu nombre para entrar.";
    campoNombre.focus();
    return;
  }

  pedirIngreso(campoCodigo.value.trim().toUpperCase(), nombre);
});

porId("reconectar").addEventListener("click", () => conectar(jugador, "reconectar"));
porId("abandonar").addEventListener("click", () => conectar(jugador, "abandonar"));

function conectar(sesion, accion) {
  // Recordamos el asiento anterior antes de reemplazar al jugador actual.
  const sesionAnterior = jugador;
  jugador = sesion;
  // No conservamos permisos de una conexión anterior.
  comidasDisponibles.clear();
  actualizarControlesComida();
  partidaFinalizada = false;
  iniciada = false;
  soyAnfitrion = false;
  // Limpiamos el nombre al abrir una conexión.
  nombrePapaNoel = "";
  // Una conexión nueva debe recuperar sus permisos del servidor.
  manoActual = [];
  vidasPorNombre = new Map();
  ataqueActual = null;
  permisoParaAtacar = false;
  faseActual = "";

  porId("controles-ataque").hidden = true;
  porId("ataque-pendiente").hidden = true;
  porId("tirar").disabled = true;
  porId("usar-defensa").disabled = true;
  porId("recibir-danio").disabled = true;
  // Evitamos mostrar las cartas o cantidades de una sala anterior.
  cantidadesDeCartas = new Map();
  porId("zona-cartas").hidden = true;
  porId("mano-propia").replaceChildren();
  porId("estado-mazo").textContent = "";
  // Limpiamos los datos de participación de la conexión anterior.
  ordenDeTurnos = [];
  ultimoTextoJugadores = "";
  jugadoresEliminados = [];
  porId("estado-participacion").hidden = true;
  porId("estado-participacion").textContent = "";
  // Esperamos el estado del servidor antes de habilitar controles.
  porId("controles-turno").hidden = true;
  porId("terminar-turno").disabled = true;
  porId("detalle-turno").textContent = "";
  porId("tiempo-turno").textContent = "01:00";
  panelRecuperar.hidden = true;
  acceso.hidden = true;
  porId("salas-publicas").hidden = true;
  // Ocultamos el rol y las habilidades mientras se establece la conexión.
  porId("rol-privado").hidden = true;
  porId("seleccion-habilidad").hidden = true;
  porId("opciones-habilidad").replaceChildren();
  porId("habilidad-confirmada").textContent = "";
  mesa.hidden = true;
  botonIniciar.disabled = true;
  estado.textContent = accion === "abandonar" ? "Abandonando…" : "Conectando…";

  const protocolo = location.protocol === "https:" ? "wss:" : "ws:";
  const consulta = new URLSearchParams({ sala: sesion.codigo, nombre: sesion.nombre });
  // Si cambiamos de sala, Java recibe el acceso del asiento que debe liberar.
  // La salida se procesa después de aceptar el ingreso a la nueva sala.
  if (sesionAnterior?.identificador && sesionAnterior.codigo !== sesion.codigo) {
    consulta.set("salaAnterior", sesionAnterior.codigo);
    consulta.set("identificadorAnterior", sesionAnterior.identificador);
  }
  if (accion) {
    consulta.set("accion", accion);
    consulta.set("identificador", sesion.identificador);
  }

  // Cada intento reemplaza el anterior. Sus eventos tardíos se ignoran.
  const anterior = socket;
  const conexion = new WebSocket(`${protocolo}//${location.host}/ws?${consulta}`);
  socket = conexion;
  if (anterior && anterior.readyState < WebSocket.CLOSING) {
    anterior.close();
  }

  conexion.addEventListener("message", (evento) => {
    if (socket !== conexion) {
      return;
    }

    const mensaje = evento.data;
    if (mensaje.startsWith("roles-revelados:")) {
      const texto = mensaje.slice("roles-revelados:".length);
      const datos = texto === "" ? [] : texto.split("\n");

      rolesRevelados = new Map();

      for (let posicion = 0; posicion < datos.length; posicion += 2) {
        rolesRevelados.set(datos[posicion], datos[posicion + 1]);
      }

      mostrarJugadores(ultimoTextoJugadores);
      return;
    }
    if (mensaje.startsWith("vidas:")) {
      const texto = mensaje.slice("vidas:".length);
      const datos = texto === "" ? [] : texto.split("\n");

      vidasPorNombre = new Map();

      for (let posicion = 0; posicion < datos.length; posicion += 2) {
        vidasPorNombre.set(datos[posicion], Number(datos[posicion + 1]));
      }

      if (vidasPorNombre.has(jugador.nombre)) {
        porId("rol-vidas").textContent =
          `Vidas: ${vidasPorNombre.get(jugador.nombre)}`;
      }

      mostrarJugadores(ultimoTextoJugadores);
      return;
    }

    if (mensaje.startsWith("ataque:")) {
      if (mensaje === "ataque:ninguno") {
        ataqueActual = null;
      } else {
        const [
          identificador,
          atacante,
          objetivo,
          tiempo,
          puedeResponder,
          sinDefensaPropia
        ] = mensaje.slice("ataque:".length).split("\n");

        ataqueActual = {
          identificador,
          atacante,
          objetivo,
          segundos: Number(tiempo),
          puedeResponder: puedeResponder === "true",
          sinDefensaPropia: sinDefensaPropia === "true"
        };
      }

      actualizarControlesCombate();
      return;
    }
    if (mensaje.startsWith("comidas-disponibles:")) {
      const texto = mensaje.slice("comidas-disponibles:".length);

      // Los identificadores corresponden únicamente a nuestras cartas.
      comidasDisponibles = new Set(
        texto === "" ? [] : texto.split("\n")
      );

      actualizarControlesComida();
      return;
    }
    if (mensaje.startsWith("mano:")) {
      const texto = mensaje.slice("mano:".length);
      const datos = texto === "" ? [] : texto.split("\n");
      const elementos = [];
      // Conservamos las cartas propias para los selectores de ataque y defensa.
      manoActual = [];

      // Cada grupo describe una copia individual de carta.
      for (let posicion = 0; posicion < datos.length; posicion += 4) {
        const identificador = datos[posicion];
        const tipo = datos[posicion + 1];
        const nombre = datos[posicion + 2];
        const categoria = datos[posicion + 3];
        manoActual.push({ identificador, tipo, nombre, categoria });

        const elemento = crearLinea(`${nombre} · ${categoria}`);

        // Conservamos la identidad para seleccionar esa copia cuando
        // implementemos jugar y descartar cartas.
        elemento.dataset.identificador = identificador;
        elemento.dataset.tipo = tipo;

        if (categoria === "COMIDA") {
          const botonComida = document.createElement("button");

          botonComida.type = "button";
          botonComida.textContent = "Usar comida";
          botonComida.dataset.comida = identificador;
          botonComida.disabled = true;

          botonComida.addEventListener("click", () => {
            if (botonComida.disabled || partidaFinalizada) {
              return;
            }

            // Enviamos el identificador del ejemplar, sin indicar
            // cantidades de curación ni destinatarios desde el navegador.
            if (enviar(`usar-comida:${identificador}`)) {
              // Evitamos repetir el clic mientras llega la actualización.
              botonComida.disabled = true;
            }
          });

          elemento.append(" ", botonComida);
        }

        // Marcar una carta no la descarta inmediatamente.
        // La selección se envía recién al tocar Terminar turno.
        const etiqueta = document.createElement("label");
        const casilla = document.createElement("input");

        casilla.type = "checkbox";
        casilla.dataset.descarte = identificador;

        // El siguiente mensaje de turno habilita las casillas si corresponde.
        casilla.disabled = true;

        etiqueta.append(casilla, " Descartar al terminar");
        elemento.append(" ", etiqueta);
        elementos.push(elemento);
      }

      porId("mano-propia").replaceChildren(
        ...elementos.length > 0
          ? elementos
          : [crearLinea("No tenés cartas en la mano.")]
      );

      const exceso = Math.max(0, elementos.length - 4);

      porId("aviso-descarte").textContent = exceso > 0
        ? `Para terminar tu turno, marcá ${exceso} carta(s) para descartar.`
        : "No necesitás descartar para terminar tu turno.";

      porId("zona-cartas").hidden = false;
      // Los botones recién creados recuperan los permisos del servidor.
      actualizarControlesComida();
      actualizarControlesCombate();
      return;
    }

    if (mensaje.startsWith("cantidades-cartas:")) {
      const texto = mensaje.slice("cantidades-cartas:".length);
      const datos = texto === "" ? [] : texto.split("\n");
      cantidadesDeCartas = new Map();

      // Cada jugador llega como nombre y cantidad, en dos líneas.
      for (let posicion = 0; posicion < datos.length; posicion += 2) {
        cantidadesDeCartas.set(datos[posicion], Number(datos[posicion + 1]));
      }

      mostrarJugadores(ultimoTextoJugadores);
      return;
    }

    if (mensaje.startsWith("mazo:")) {
      const [cantidadMazo, cantidadDescarte] =
        mensaje.slice("mazo:".length).split("\n");

      porId("estado-mazo").textContent =
        `Mazo: ${cantidadMazo} cartas · Descarte: ${cantidadDescarte} cartas`;

      return;
    }
    if (mensaje.startsWith("jugadores-eliminados:")) {
      const texto = mensaje.slice("jugadores-eliminados:".length);
      jugadoresEliminados = texto === "" ? [] : texto.split("\n");

      const estoyEliminado = jugadoresEliminados.includes(jugador.nombre);
      const aviso = porId("estado-participacion");

      aviso.hidden = !estoyEliminado;
      aviso.textContent = estoyEliminado
        ? "Quedaste eliminado. Podés seguir mirando y compartir la victoria de tu bando."
        : "";

      if (estoyEliminado) {
        porId("terminar-turno").disabled = true;
      }

      // Actualizamos las marcas sin cambiar el orden sorteado.
      mostrarJugadores(ultimoTextoJugadores);
      return;
    }
    if (mensaje.startsWith("turno:")) {
      const [numero, nombre, puedeFinalizar, tiempo, puedeAtacar, pausado] =
        mensaje.slice("turno:".length).split("\n");

      const segundos = Number(tiempo);
      permisoParaAtacar = puedeAtacar === "true";

      porId("controles-turno").hidden = false;
      porId("detalle-turno").textContent =
        `Turno ${numero}/24 · Juega ${nombre}`
        + (pausado === "true" ? " · Esperando respuesta" : "");

      // El tiempo queda fijo mientras se resuelve el ataque.
      porId("tiempo-turno").textContent = formatearTiempo(segundos);

      porId("terminar-turno").disabled =
        puedeFinalizar !== "true"
        || segundos <= 0
        || jugadoresEliminados.includes(jugador.nombre);

      // Conservamos las marcas de descarte al actualizar el reloj.
      const puedeSeleccionar = !porId("terminar-turno").disabled;

      porId("mano-propia")
        .querySelectorAll("input[data-descarte]")
        .forEach((casilla) => {
          casilla.disabled = !puedeSeleccionar;
        });

      actualizarControlesCombate();
      return;
    }
    if (mensaje.startsWith("orden-turnos:")) {
      const texto = mensaje.slice("orden-turnos:".length);

      // Guardamos el orden enviado por Java. El navegador no lo sortea.
      ordenDeTurnos = texto === "" ? [] : texto.split("\n");

      // Actualizamos la lista aunque todavía no haya llegado
      // otro mensaje de conexión o desconexión.
      mostrarJugadores(ultimoTextoJugadores);
      return;
    }
    if (mensaje.startsWith("partida-terminada:")) {
      const [resultado, gano] =
        mensaje.slice("partida-terminada:".length).split("\n");

      mostrarResultadoPartida(resultado, gano);
      return;
    }
    if (mensaje.startsWith("opciones-habilidad:")) {
      const datos = mensaje.slice("opciones-habilidad:".length).split("\n");
      const contenedor = porId("opciones-habilidad");
      contenedor.replaceChildren();

      // Cada opción llega como identificador, nombre y descripción.
      for (let posicion = 0; posicion < datos.length; posicion += 3) {
        const identificador = datos[posicion];
        const nombre = datos[posicion + 1];
        const descripcion = datos[posicion + 2];

        const opcion = document.createElement("div");

        const titulo = document.createElement("h4");
        titulo.textContent = nombre;

        const explicacion = document.createElement("p");
        explicacion.textContent = descripcion;

        const boton = document.createElement("button");
        boton.type = "button";
        boton.textContent = `Elegir ${nombre}`;

        // Mandamos el identificador. Java comprueba que sea una opción asignada.
        boton.addEventListener("click", () => {
          enviar(`elegir-habilidad:${identificador}`);
        });

        opcion.append(titulo, explicacion, boton);
        contenedor.append(opcion);
      }

      porId("habilidad-confirmada").textContent = "";
      porId("seleccion-habilidad").hidden = false;
      return;
    }

    if (mensaje.startsWith("habilidad-elegida:")) {
      const [, nombre, descripcion] =
        mensaje.slice("habilidad-elegida:".length).split("\n");

      // Solo reemplazamos las opciones después de la confirmación del servidor.
      porId("opciones-habilidad").replaceChildren();
      porId("habilidad-confirmada").textContent =
        `Elegiste ${nombre}. ${descripcion}`;

      porId("seleccion-habilidad").hidden = false;
      return;
    }
    if (mensaje.startsWith("rol-confirmado:")) {
      const confirmado = mensaje === "rol-confirmado:true";

      porId("confirmar-rol").disabled = confirmado;
      porId("estado-confirmacion").textContent = confirmado
        ? "Confirmaste tu rol. Esperando a los demás…"
        : "Leé tu objetivo y tocá Continuar.";
      return;
    }

    if (mensaje.startsWith("fase:")) {
      const fase = mensaje.slice("fase:".length);

      // El servidor controla cuándo termina cada etapa.
      faseActual = fase;
      actualizarControlesCombate();
      porId("confirmar-rol").hidden = fase !== "REPARTO_DE_ROLES";

      if (fase === "ELECCION_DE_HABILIDAD") {
        porId("estado-confirmacion").textContent =
          "Elegí tu habilidad. Esperamos a que todos confirmen su elección.";
      } else if (fase === "EN_JUEGO") {
        porId("estado-confirmacion").textContent =
          "¡Todos eligieron! La partida está en juego.";
      }
      return;
    }
    if (mensaje.startsWith("rol-propio:")) {
      // Java envía el nombre, las vidas iniciales y el objetivo en ese orden.
      const [nombreRol, vidas, objetivo] = mensaje.slice("rol-propio:".length).split("\n");

      porId("rol-nombre").textContent = `Tu rol: ${nombreRol}`;
      porId("rol-objetivo").textContent = objetivo;
      // Al reconectarse, priorizamos las vidas actuales si ya llegaron.
      porId("rol-vidas").textContent = `Vidas: ${vidasPorNombre.get(jugador.nombre) ?? vidas}`;
      porId("rol-privado").hidden = false;
      return;
    }
    if (mensaje.startsWith("identificador:")) {
      jugador.identificador = mensaje.slice("identificador:".length);
    } else if (mensaje === "sala-abandonada") {
      borrarSesion(jugador);
      socket = null;
      history.replaceState(null, "", location.pathname);
      acceso.hidden = false;
      estado.textContent = "Abandonaste la sala.";
    } else if (mensaje === "sala-confirmada") {
      // Guardamos el acceso en el mismo momento de entrar al lobby.
      guardarSesion(jugador);
      sessionStorage.setItem("nombreJugador", jugador.nombre);
      marcarUrl(jugador.codigo);
      porId("titulo-sala").textContent = `Sala ${jugador.codigo}`;
      campoCodigo.value = jugador.codigo;
      // Limpiamos la vista antes de recuperar el historial de esta sala.
      chat.replaceChildren();
      mesa.hidden = false;
      lobby.hidden = false;
      vistaPartida.hidden = true;
      estado.textContent = `Conectado como ${jugador.nombre}.`;
    } else if (mensaje === "partida-iniciada") {
      iniciada = true;
      soyAnfitrion = false;
      jugador.iniciada = true;
      guardarSesion(jugador);
      lobby.hidden = true;
      vistaPartida.hidden = false;
      estado.textContent = "¡Partida iniciada!";
    } else if (mensaje.startsWith("anfitrion:")) {
      // Java nos informa el permiso de esta pestaña, incluso si cambia el anfitrión.
      soyAnfitrion = mensaje === "anfitrion:true";
    } else if (mensaje.startsWith("puede-iniciar:")) {
      botonIniciar.disabled = mensaje !== "puede-iniciar:true";
    } else if (mensaje.startsWith("papa-noel:")) {
      nombrePapaNoel = mensaje.slice("papa-noel:".length);
    } else if (mensaje.startsWith("jugadores:")) {
      mostrarJugadores(mensaje.slice("jugadores:".length));
    } else if (mensaje.startsWith("historial-sala:")) {
      // El servidor entrega el historial completo en su orden original.
      chat.replaceChildren();
      agregarMensajesDeSala(mensaje.slice("historial-sala:".length));
    } else if (mensaje.startsWith("mensaje-sala:")) {
      // Los mensajes nuevos se agregan después de los anteriores.
      agregarMensajesDeSala(mensaje.slice("mensaje-sala:".length));
    }
  });

  conexion.addEventListener("close", (evento) => {
    if (socket !== conexion) {
      return;
    }

    socket = null;
    // El cierre posterior al resultado no debe ofrecer reconexión
    // ni ocultar el cartel que el jugador todavía está leyendo.
    if (partidaFinalizada) {
      return;
    }

    // Respaldo si se recibió el cierre definitivo sin el mensaje de resultado.
    if (evento.code === 4014) {
      mostrarResultadoPartida(null, null);
      return;
    }
    mesa.hidden = true;
    acceso.hidden = false;
    porId("salas-publicas").hidden = false;

    const mensajes = {
      1008: "La sala o el acceso guardado ya no están disponibles.",
      4008: "La sala está llena. El máximo es de 8 jugadores.",
      4009: "Ingresá un nombre válido de hasta 20 caracteres.",
      4010: "La partida ya empezó.",
      4011: "El jugador continuó en otra pestaña o abandonó.",
      4012: "Ese nombre ya pertenece a un jugador de esta sala.",
    };
    estado.textContent = mensajes[evento.code] ?? "Se cerró la conexión con el servidor.";

    if (evento.code === 4013) {
      // El acceso quedó invalidado. Volvemos al inicio y quitamos Reconectar.
      borrarSesion(jugador);
      jugador = null;
      panelRecuperar.hidden = true;
      campoCodigo.value = "";
      history.replaceState(null, "", location.pathname);
      estado.textContent = "El anfitrión te expulsó de la sala.";
      return;
    }

    if (accion && evento.code === 1008) {
      borrarSesion(jugador);
      return;
    }

    if (jugador.identificador && (evento.code === 1006 || evento.code === 4008
      || evento.code === 1011 || evento.code === 1001)) {
      mostrarRecuperacion(jugador);
    }
  });
}

function actualizarControlesComida() {
  document.querySelectorAll("button[data-comida]").forEach((boton) => {
    // La interfaz refleja el permiso recibido.
    // Java vuelve a validarlo cuando intentamos jugar la carta.
    boton.disabled = partidaFinalizada
      || !comidasDisponibles.has(boton.dataset.comida);
  });
}

function crearLinea(texto) {
  // textContent muestra los mensajes como texto, incluso si escriben etiquetas HTML.
  const linea = document.createElement("li");
  linea.textContent = texto;
  return linea;
}

function agregarMensajesDeSala(texto) {
  if (texto === "") {
    return;
  }

  // Cada mensaje ocupa dos líneas: tipo y contenido.
  // Java enviará CHAT para conversaciones y SISTEMA para eventos del juego.
  const campos = texto.split("\n");

  for (let indice = 0; indice + 1 < campos.length; indice += 2) {
    const tipo = campos[indice];
    const contenido = campos[indice + 1];

    // Ignoramos tipos que nuestra interfaz todavía no reconoce.
    if (tipo !== "CHAT" && tipo !== "SISTEMA") {
      continue;
    }

    // Reutilizamos crearLinea, que usa textContent para mostrar texto seguro.
    // El estilo distingue los eventos sin agregar un prefijo al texto.
    const linea = crearLinea(contenido);

    linea.classList.add(
      tipo === "SISTEMA" ? "mensaje-sistema" : "mensaje-jugador"
    );

    // Diferenciación provisoria hasta desarrollar el diseño de la interfaz.
    if (tipo === "SISTEMA") {
      linea.style.fontStyle = "italic";
    }

    chat.append(linea);
  }
}

function mostrarJugadores(texto) {
  ultimoTextoJugadores = texto;

  const filas = texto === "" ? [] : texto.split("\n");
  const conectados = filas.filter((fila) => fila.startsWith("1:")).length;

  porId("cantidad-jugadores").textContent =
    `Conectados: ${conectados} · Lugares ocupados: ${filas.length}/8`;

  if (iniciada && ordenDeTurnos.length > 0) {
    // Relacionamos cada nombre con su posición en el orden definitivo.
    const posiciones = new Map(
      ordenDeTurnos.map((nombre, posicion) => [nombre, posicion])
    );

    // Ordenamos las filas conservando su indicador de conexión.
    // Si alguien abandonó, simplemente deja de aparecer en la lista.
    filas.sort((primera, segunda) => {
      const posicionPrimera =
        posiciones.get(primera.slice(2)) ?? Number.MAX_SAFE_INTEGER;
      const posicionSegunda =
        posiciones.get(segunda.slice(2)) ?? Number.MAX_SAFE_INTEGER;

      return posicionPrimera - posicionSegunda;
    });
  }

  const elementos = filas.map((fila, posicion) => {
    const nombre = fila.slice(2);
    const elemento = crearLinea(nombre);
    // Las vidas actuales son públicas; los roles siguen siendo privados.
    if (iniciada && vidasPorNombre.has(nombre)) {
      elemento.append(` — ${vidasPorNombre.get(nombre)} vidas`);
    }
    // Mostramos solamente la cantidad de cartas de cada participante.
    if (iniciada && cantidadesDeCartas.has(nombre)) {
      elemento.append(` — ${cantidadesDeCartas.get(nombre)} cartas`);
    }
    // La eliminación es independiente de estar conectado o desconectado.
    if (jugadoresEliminados.includes(nombre)) {
      elemento.append(" — Eliminado");

      // Papá Noel ya tiene su etiqueta pública más abajo.
      if (rolesRevelados.has(nombre) && nombre !== nombrePapaNoel) {
        elemento.append(` — ${rolesRevelados.get(nombre)}`);
      }
    }

    // Papá Noel sigue siendo el único rol identificado públicamente.
    if (iniciada && nombre === nombrePapaNoel) {
      elemento.append(" — Papá Noel");
    }

    if (fila.startsWith("0:")) {
      elemento.append(" — Reconectando...");
    }

    // En el lobby conservamos el orden de ingreso y sus permisos.
    if (!iniciada && posicion === 0) {
      elemento.append(" — Anfitrión");
    }

    if (!iniciada && soyAnfitrion && posicion > 0) {
      const boton = document.createElement("button");
      boton.type = "button";
      boton.textContent = "Expulsar";
      boton.setAttribute("aria-label", `Expulsar a ${nombre}`);

      boton.addEventListener("click", () => enviar(`expulsar:${nombre}`));
      elemento.append(" ", boton);
    }

    return elemento;
  });

  porId("lista-jugadores").replaceChildren(...elementos);
  // Actualizamos los objetivos disponibles si alguien sale o es eliminado.
  actualizarControlesCombate();
}

function formatearTiempo(segundos) {
  const minutos = String(Math.floor(segundos / 60)).padStart(2, "0");
  const resto = String(segundos % 60).padStart(2, "0");

  return `${minutos}:${resto}`;
}

function actualizarOpciones(selector, opciones) {
  // No reconstruimos el selector cuando solo cambia el reloj.
  // Así conservamos la selección y el foco del usuario.
  const actuales = [...selector.options]
    .map((opcion) => [opcion.value, opcion.textContent]);

  if (JSON.stringify(actuales) === JSON.stringify(opciones)) {
    return;
  }

  const seleccionAnterior = selector.value;

  const elementos = opciones.map(([valor, nombre]) => {
    const opcion = document.createElement("option");
    opcion.value = valor;
    opcion.textContent = nombre;
    return opcion;
  });

  selector.replaceChildren(...elementos);

  if (opciones.some(([valor]) => valor === seleccionAnterior)) {
    selector.value = seleccionAnterior;
  }
}

function actualizarControlesCombate() {
  const conectado = socket?.readyState === WebSocket.OPEN;
  const enJuego = conectado && faseActual === "EN_JUEGO";

  const bolas = manoActual
    .filter((carta) => carta.tipo === "BOLA_DE_NIEVE")
    .map((carta, posicion) => [
      carta.identificador,
      `${carta.nombre} ${posicion + 1}`
    ]);

  // Los desconectados siguen siendo objetivos válidos.
  // Excluimos al propio jugador y a quienes ya fueron eliminados.
  const nombres = ultimoTextoJugadores === ""
    ? []
    : ultimoTextoJugadores.split("\n").map((fila) => fila.slice(2));

  const objetivos = nombres
    .filter((nombre) =>
      nombre !== jugador?.nombre
      && !jugadoresEliminados.includes(nombre)
      && vidasPorNombre.get(nombre) !== 0)
    .map((nombre) => [nombre, nombre]);

  actualizarOpciones(porId("bola-elegida"), bolas);
  actualizarOpciones(porId("objetivo-ataque"), objetivos);

  const puedeAtacar =
    enJuego
    && permisoParaAtacar
    && ataqueActual === null
    && !jugadoresEliminados.includes(jugador?.nombre);

  porId("controles-ataque").hidden = !enJuego;
  porId("tirar").hidden = !enJuego;
  porId("bola-elegida").disabled = !puedeAtacar;
  porId("objetivo-ataque").disabled = !puedeAtacar;
  porId("tirar").disabled =
    !puedeAtacar || bolas.length === 0 || objetivos.length === 0;

  const hayAtaque = enJuego && ataqueActual !== null;
  porId("ataque-pendiente").hidden = !hayAtaque;

  const defensas = manoActual
    .filter((carta) =>
      carta.tipo === "ESQUIVAR" || carta.tipo === "DEVOLVER")
    .map((carta) => [carta.identificador, carta.nombre]);

  actualizarOpciones(porId("defensa-elegida"), defensas);

  const puedeResponder =
    hayAtaque
    && ataqueActual.puedeResponder
    && ataqueActual.segundos > 0
    && !jugadoresEliminados.includes(jugador?.nombre);

  porId("opciones-respuesta").hidden = !puedeResponder;
  porId("defensa-elegida").disabled = !puedeResponder;
  porId("usar-defensa").disabled =
    !puedeResponder || defensas.length === 0;
  porId("recibir-danio").disabled = !puedeResponder;

  if (hayAtaque) {
    if (ataqueActual.sinDefensaPropia) {
      // Todos ven el pedido de ayuda durante la espera breve.
      porId("detalle-ataque").textContent =
        `${ataqueActual.objetivo} no tiene cómo defenderse. `
        + "¿Alguien va a ayudarlo?";

      porId("tiempo-respuesta").textContent =
        `${ataqueActual.segundos} segundos para ayudar`;
    } else {
      // Si tiene defensa propia, conserva el minuto habitual.
      porId("detalle-ataque").textContent =
        `${ataqueActual.atacante} le tira una bola a ${ataqueActual.objetivo}.`;

      porId("tiempo-respuesta").textContent =
        formatearTiempo(ataqueActual.segundos);
    }
  }
}

function enviarRespuestaAtaque(identificadorCarta) {
  if (!ataqueActual
      || !ataqueActual.puedeResponder
      || ataqueActual.segundos <= 0) {
    return;
  }

  // Incluimos el ataque al que estamos respondiendo.
  // Una respuesta vieja no podrá resolver una bola nueva.
  const mensaje =
    `responder-ataque:${ataqueActual.identificador}\n${identificadorCarta}`;

  if (enviar(mensaje)) {
    porId("usar-defensa").disabled = true;
    porId("recibir-danio").disabled = true;
  }
}

function enviar(mensaje) {
  if (socket?.readyState === WebSocket.OPEN) {
    socket.send(mensaje);
    return true;
  }
  return false;
}

// El botón solicita la confirmación. Java comprueba quién la envía.
porId("confirmar-rol").addEventListener("click", () => {
  enviar("confirmar-rol");
});

porId("terminar-turno").addEventListener("click", () => {
  if (porId("terminar-turno").disabled) {
    return;
  }

  const casillas = [
    ...porId("mano-propia").querySelectorAll("input[data-descarte]")
  ];

  const seleccionadas = casillas
    .filter((casilla) => casilla.checked)
    .map((casilla) => casilla.dataset.descarte);

  const exceso = Math.max(0, casillas.length - 4);

  // Avisamos antes de enviar. El servidor igualmente vuelve a validar.
  if (seleccionadas.length !== exceso) {
    porId("aviso-descarte").textContent =
      `Tenés que marcar exactamente ${exceso} carta(s) para terminar.`;
    return;
  }

  if (enviar(`terminar-turno:${seleccionadas.join(",")}`)) {
    porId("terminar-turno").disabled = true;
    casillas.forEach((casilla) => {
      casilla.disabled = true;
    });
  }
});
// Salida desde el cartel de resultado.
porId("salir-partida").addEventListener(
  "click", salirDePartidaFinalizada);

// Escape realiza la misma salida y limpieza que el botón.
porId("resultado-partida").addEventListener("cancel", (evento) => {
  evento.preventDefault();
  salirDePartidaFinalizada();
});

botonIniciar.addEventListener("click", () => enviar("iniciar-partida"));
porId("tirar").addEventListener("click", () => {
  if (porId("tirar").disabled) {
    return;
  }

  const carta = porId("bola-elegida").value;
  const objetivo = porId("objetivo-ataque").value;

  if (!carta || !objetivo) {
    return;
  }

  if (enviar(`lanzar-bola:${carta}\n${objetivo}`)) {
    // Esperamos la confirmación antes de permitir otra solicitud.
    porId("tirar").disabled = true;
  }
});

porId("usar-defensa").addEventListener("click", () => {
  if (porId("usar-defensa").disabled) {
    return;
  }

  const carta = porId("defensa-elegida").value;

  if (carta) {
    enviarRespuestaAtaque(carta);
  }
});

porId("recibir-danio").addEventListener("click", () => {
  if (!porId("recibir-danio").disabled) {
    // La cadena vacía representa aceptar el daño.
    enviarRespuestaAtaque("");
  }
});

formularioChat.addEventListener("submit", (evento) => {
  evento.preventDefault();

  const texto = campoChat.value.trim();

  // Permitimos conversar tanto en el lobby como durante la partida.
  // Java valida la conexión, la sala y el contenido del mensaje.
  if (!partidaFinalizada && texto && enviar(`chat:${texto}`)) {
    campoChat.value = "";
  }
});

function salirDeLaVista() {
  const conexion = socket;
  if (!conexion) {
    return;
  }

  // Ignoramos los eventos tardíos del socket que está saliendo de la vista.
  // Java recibe su cierre y marca al jugador como desconectado.
  socket = null;
  if (conexion.readyState < WebSocket.CLOSING) {
    conexion.close(1000, "El jugador salio de la vista");
  }

  mesa.hidden = true;
  acceso.hidden = false;
  if (jugador?.identificador) {
    mostrarRecuperacion(jugador);
    estado.textContent = "Tu lugar sigue reservado en la sala.";
  }
}

// También ocurre al ir atrás o cuando el navegador guarda la página en caché.
window.addEventListener("pagehide", salirDeLaVista);

window.addEventListener("popstate", () => {
  const codigoActual = new URLSearchParams(location.search).get("sala");
  if (jugador && codigoActual !== jugador.codigo) {
    salirDeLaVista();
  }
});

campoNombre.value = nombreEnPestana;
campoCodigo.value = codigoEnUrl ?? "";
const sesionPestana = leerSesion(sessionStorage, claveActual);

if (sesionPestana && (!codigoEnUrl || codigoEnUrl === sesionPestana.codigo)) {
  mostrarRecuperacion(sesionPestana);
} else if (codigoEnUrl && nombreEnPestana) {
  pedirIngreso(codigoEnUrl, nombreEnPestana);
} else if (!codigoEnUrl) {
  const ultima = leerSesion(localStorage, claveActual);
  if (ultima) {
    mostrarRecuperacion(ultima);
  }
} else {
  estado.textContent = "Escribí tu nombre y tocá Unirse con código.";
}

async function actualizarSalasPublicas() {
  const botonActualizar = porId("actualizar-salas");
  const lista = porId("lista-salas-publicas");
  const estadoSalas = porId("estado-salas");

  botonActualizar.disabled = true;
  estadoSalas.textContent = "Buscando salas…";

  try {
    const respuesta = await fetch("/api/salas/publicas", {
      cache: "no-store",
    });

    if (!respuesta.ok) {
      throw new Error(`El servidor respondió con ${respuesta.status}`);
    }

    const salas = await respuesta.json();

    const elementos = salas.map((sala) => {
      const elemento = document.createElement("li");
      const enEspera = sala.estado === "EN_ESPERA";

      // Mostramos los lugares ocupados, incluidos los reservados.
      elemento.textContent =
        `Sala de ${sala.anfitrion || "un jugador"} · ${sala.ocupados}/8 · `
        + (enEspera ? "En espera" : "En partida");

      const boton = document.createElement("button");
      boton.type = "button";
      boton.textContent = "Unirse";
      boton.disabled = !enEspera || sala.ocupados >= 8;

      boton.addEventListener("click", () => {
        const nombre = campoNombre.value.trim();

        if (!campoNombre.reportValidity() || !nombre) {
          estadoSalas.textContent = "Escribí tu nombre antes de unirte.";
          campoNombre.focus();
          return;
        }

        // Recuperamos el asiento si este navegador ya conoce al jugador.
        // Para una sala pública, el botón envía el código automáticamente.
        pedirIngreso(sala.codigo, nombre);
      });

      elemento.append(" ", boton);
      return elemento;
    });

    lista.replaceChildren(...elementos);
    estadoSalas.textContent = salas.length === 0
      ? "No hay salas públicas activas."
      : "";
  } catch (error) {
    console.error(error);
    estadoSalas.textContent = "No se pudieron cargar las salas. Tocá Actualizar.";
  } finally {
    botonActualizar.disabled = false;
  }
}

porId("actualizar-salas").addEventListener("click", actualizarSalasPublicas);

// Cargamos el listado cuando la página termina de ejecutar este archivo.
actualizarSalasPublicas();