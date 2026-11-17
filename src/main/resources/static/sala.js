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
const actividad = porId("actividad");

const claveActual = "caos:sesion-actual";
const codigoEnUrl = new URLSearchParams(location.search).get("sala")?.trim().toUpperCase();
const nombreEnPestana = sessionStorage.getItem("nombreJugador") ?? "";
const claveJugador = (codigo, nombre) => `caos:jugador:${codigo}:${nombre.toLowerCase()}`;

let socket;
let jugador;
let iniciada = false;
let soyAnfitrion = false;
let nombrePapaNoel = "";

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
      borrarSesion(sesion);
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
    const respuesta = await fetch("/api/salas", { method: "POST" });
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
  jugador = sesion;
  iniciada = false;
  soyAnfitrion = false;
  // Limpiamos el nombre al abrir una conexión.
  nombrePapaNoel = "";
  panelRecuperar.hidden = true;
  acceso.hidden = true;
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
    if (mensaje.startsWith("partida-terminada:")) {
      const [resultado, gano] =
        mensaje.slice("partida-terminada:".length).split("\n");

      const explicaciones = {
        VICTORIA_PAPA_NOEL: "Ganó el bando de Papá Noel.",
        VICTORIA_LADRONES: "Ganaron los Ladrones y el Arrepentido.",
        VICTORIA_KRAMPUS: "Ganó Krampus.",
        SIN_PARTICIPANTES: "La partida terminó sin participantes.",
      };

      // Cerramos los controles de preparación y mostramos el resultado privado.
      porId("confirmar-rol").hidden = true;
      porId("seleccion-habilidad").hidden = true;
      porId("tirar").hidden = true;

      estado.textContent = explicaciones[resultado];
      porId("estado-confirmacion").textContent =
        resultado === "SIN_PARTICIPANTES"
          ? "La sala quedó vacía."
          : gano === "true" ? "¡Ganaste!" : "Perdiste.";
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
      porId("tirar").hidden = true;
      porId("confirmar-rol").hidden = fase !== "REPARTO_DE_ROLES";

      if (fase === "ELECCION_DE_HABILIDAD") {
        porId("estado-confirmacion").textContent =
          "Elegí tu habilidad. Esperamos a que todos confirmen su elección.";
      } else if (fase === "PREPARACION_COMPLETA") {
        porId("estado-confirmacion").textContent =
          "¡Todos eligieron! La preparación está completa.";
      }
      return;
    }
    if (mensaje.startsWith("rol-propio:")) {
      // Java envía el nombre, las vidas iniciales y el objetivo en ese orden.
      const [nombreRol, vidas, objetivo] = mensaje.slice("rol-propio:".length).split("\n");

      porId("rol-nombre").textContent = `Tu rol: ${nombreRol}`;
      porId("rol-objetivo").textContent = objetivo;
      porId("rol-vidas").textContent = `Vidas iniciales: ${vidas}`;
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
      chat.replaceChildren();
      actividad.replaceChildren();
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
    } else if (mensaje.startsWith("chat-historial:")) {
      mostrarHistorial(chat, mensaje.slice("chat-historial:".length));
    } else if (mensaje.startsWith("historial:")) {
      mostrarHistorial(actividad, mensaje.slice("historial:".length));
    } else if (mensaje.startsWith("chat:")) {
      chat.append(crearLinea(mensaje.slice("chat:".length)));
    } else if (mensaje.startsWith("actividad:")) {
      actividad.append(crearLinea(mensaje.slice("actividad:".length)));
    }
  });

  conexion.addEventListener("close", (evento) => {
    if (socket !== conexion) {
      return;
    }

    socket = null;
    mesa.hidden = true;
    acceso.hidden = false;

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

function crearLinea(texto) {
  // textContent muestra los mensajes como texto, incluso si escriben etiquetas HTML.
  const linea = document.createElement("li");
  linea.textContent = texto;
  return linea;
}

function mostrarHistorial(lista, texto) {
  const lineas = texto === "" ? [] : texto.split("\n");
  lista.replaceChildren(...lineas.map(crearLinea));
}

function mostrarJugadores(texto) {
  const filas = texto === "" ? [] : texto.split("\n");
  const conectados = filas.filter((fila) => fila.startsWith("1:")).length;
  porId("cantidad-jugadores").textContent =
    `Conectados: ${conectados} · Lugares ocupados: ${filas.length}/8`;

  const elementos = filas.map((fila, posicion) => {
    const nombre = fila.slice(2);
    const elemento = crearLinea(nombre);
    // Identificamos a Papá Noel en la lista pública de jugadores.
    if (iniciada && nombre === nombrePapaNoel) {
      elemento.append(" — Papá Noel");
    }
    if (fila.startsWith("0:")) {
      elemento.append(" — Desconectado");
    }

    if (!iniciada && posicion === 0) {
      elemento.append(" — Anfitrión");
    }

    // Solo el anfitrión ve los botones y el servidor vuelve a validar el permiso.
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

botonIniciar.addEventListener("click", () => enviar("iniciar-partida"));
porId("tirar").addEventListener("click", () => {
  if (iniciada) {
    enviar("tirar-bola");
  }
});

formularioChat.addEventListener("submit", (evento) => {
  evento.preventDefault();
  const texto = campoChat.value.trim();
  if (!iniciada && texto && enviar(`chat:${texto}`)) {
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
