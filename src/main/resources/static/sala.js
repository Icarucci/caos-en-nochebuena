const botonCrear = document.querySelector("#crear-sala");
const formularioIngreso = document.querySelector("#formulario-ingreso");
const campoCodigo = document.querySelector("#codigo");
const estado = document.querySelector("#estado");
const mesa = document.querySelector("#mesa");
const tituloSala = document.querySelector("#titulo-sala");
const cantidadJugadores = document.querySelector("#cantidad-jugadores");
const botonTirar = document.querySelector("#tirar");
const actividad = document.querySelector("#actividad");

// Al crear una sala o ingresar un código, abrimos la página con ese código en la URL.
function abrirSala(codigo) {
  const parametros = new URLSearchParams({ sala: codigo });
  location.search = parametros.toString();
}

botonCrear.addEventListener("click", async () => {
  botonCrear.disabled = true;
  estado.textContent = "Creando sala…";

  try {
    // Java genera el código y registra la sala antes de responder.
    const respuesta = await fetch("/api/salas", { method: "POST" });

    if (!respuesta.ok) {
      throw new Error(`El servidor respondió con ${respuesta.status}`);
    }

    const sala = await respuesta.json();
    abrirSala(sala.codigo);
  } catch (error) {
    console.error(error);
    estado.textContent = "No se pudo crear la sala. Probá de nuevo.";
    botonCrear.disabled = false;
  }
});

formularioIngreso.addEventListener("submit", (evento) => {
  evento.preventDefault();
  abrirSala(campoCodigo.value.trim().toUpperCase());
});

const parametros = new URLSearchParams(location.search);
const codigoSala = parametros.get("sala")?.trim().toUpperCase();

if (codigoSala) {
  campoCodigo.value = codigoSala;
  estado.textContent = `Validando el código ${codigoSala}…`;

  // Si la URL tiene un código, se lo enviamos a Java al abrir el WebSocket.
  const protocolo = location.protocol === "https:" ? "wss:" : "ws:";
  const consulta = new URLSearchParams({ sala: codigoSala });
  const socket = new WebSocket(`${protocolo}//${location.host}/ws?${consulta}`);

  socket.addEventListener("message", (evento) => {
    // Java confirma el ingreso antes de que mostremos la mesa.
    if (evento.data === "sala-confirmada") {
      estado.textContent = `Conectado a la sala ${codigoSala}.`;
      tituloSala.textContent = `Sala ${codigoSala}`;
      mesa.hidden = false;
      botonTirar.disabled = false;
      return;
    }

    // Si Java manda, por ejemplo, "jugadores:2", actualizamos el contador.
    if (evento.data.startsWith("jugadores:")) {
      const cantidad = evento.data.slice("jugadores:".length);
      cantidadJugadores.textContent = `Jugadores conectados: ${cantidad}/8`;
      return;
    }

    // Los mensajes de jugadas se agregan al registro de actividad.
    const jugada = document.createElement("li");
    jugada.textContent = evento.data;
    actividad.append(jugada);
  });

  socket.addEventListener("close", (evento) => {
    botonTirar.disabled = true;

    // Java usa el código 4008 para avisar que la sala ya tiene ocho jugadores.
    if (evento.code === 4008) {
      estado.textContent = "La sala está llena. El máximo es de 8 jugadores.";
      return;
    }

    estado.textContent = evento.code === 1008
      ? "El código de sala no es válido o la sala ya no existe."
      : "Se cerró la conexión con el servidor.";
  });

  botonTirar.addEventListener("click", () => {
    if (socket.readyState === WebSocket.OPEN) {
      socket.send("tirar-bola");
    }
  });
}