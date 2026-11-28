# Arquitectura

El proyecto utiliza Java y Spring Boot en el backend, con HTTP
para gestionar salas y WebSocket para la comunicación durante la partida.

La interfaz está desarrollada con HTML y JavaScript y se encuentra
en src/main/resources/static.

## Tecnologías utilizadas

| Tecnología | Uso en el proyecto |
|---|---|
| Java 21 | Lenguaje del backend y de las reglas del juego. |
| Spring Boot | Configuración y ejecución de la aplicación. |
| Spring Web / Spring MVC | Endpoints HTTP para crear y consultar salas. |
| Spring WebSocket | Comunicación en tiempo real entre el servidor y los jugadores. |
| Spring Scheduling | Revisión automática de turnos y limpieza de salas sin conexiones. |
| Maven y Maven Wrapper | Gestión de dependencias, compilación y ejecución de pruebas. |
| HTML5 | Estructura de las pantallas. |
| JavaScript | Interacción de la interfaz y comunicación con el backend. |
| Web Storage | Conservación de los datos de acceso para recuperar una sesión. |
| Git y GitHub | Control de versiones, ramas y revisión mediante pull requests. |

## Organización del backend

Los paquetes están dentro de com.icarucci.caosennochebuena:

| Paquete | Responsabilidad |
|---|---|
| modelo | Estado de la partida, reglas, cartas, roles y habilidades. |
| servicio | Registro de salas y reparto aleatorio de roles y habilidades. |
| controlador | Endpoints HTTP para crear y consultar salas. |
| websocket | Conexiones, recepción de mensajes y envío de estados. |
| configuracion | Configuración de WebSocket y tareas programadas. |

La clase CaosEnNochebuenaApplication permanece en el paquete raíz.

## Separación de responsabilidades

Partida valida las reglas del juego y administra su estado.
JuegoWebSocket recibe las solicitudes de los jugadores, delega
las decisiones del juego y comunica sus resultados.

El servidor mantiene el estado autoritativo. El navegador muestra
la información recibida y envía las acciones del usuario.

La separación entre modelo, vistas y controladores sigue el criterio
de MVC, con un manejador específico para la comunicación WebSocket.

## Concurrencia

Las operaciones compartidas de JuegoWebSocket utilizan synchronized.
Partida también protege sus operaciones de estado con synchronized.

Las tareas programadas revisan el vencimiento de los turnos
y eliminan las salas que permanecen sin conexiones.

## Mejoras pendientes

- Separar progresivamente de JuegoWebSocket la gestión de conexiones
  y la construcción de mensajes.
- Mantener documentadas las decisiones a medida que crezca el juego.

## Chat e historial de mensajes

### Implementación actual

El servidor conserva en memoria un historial por sala que reúne
las conversaciones y los eventos públicos, en orden cronológico.

Los mensajes se representan mediante MensajeSala y se distinguen
por su tipo:

- CHAT: mensajes escritos por los jugadores.
- SISTEMA: ingresos, reconexiones, abandonos, expulsiones y acciones
  públicas del juego.

El frontend muestra ambos tipos en un mismo panel, disponible
durante el lobby y la partida. Los eventos se muestran en cursiva.

Al reconectarse, el jugador recupera el historial disponible.
Actualmente, este historial se elimina al eliminar la sala y se
pierde si se reinicia el servidor.

### Diseño previsto: canales independientes

Compartir un panel visual no significa compartir el almacenamiento
ni los destinatarios de los mensajes.

Se prevén tres canales:

| Canal | Contenido | Destinatarios |
| --- | --- | --- |
| Sala | Conversaciones y eventos del lobby | Integrantes de la sala |
| Partida | Conversaciones y acciones durante el juego | Participantes de la partida |
| Privado | Conversación entre dos cuentas | Las dos cuentas involucradas |

Al comenzar una partida, la interfaz podrá conservar visibles los
mensajes del lobby, aunque estos mantendrán su asociación con la sala.

Los mensajes privados podrán mostrarse en el panel de una partida,
pero seguirán perteneciendo a su conversación privada. No se
incorporarán al historial ni a las exportaciones de esa partida.

### Cuentas, amigos y mensajes privados

La mensajería privada estará disponible para usuarios autenticados,
tanto desde el inicio del juego como durante una partida.

Su conexión será independiente de la pertenencia a una sala.
Entrar o salir de una partida no deberá interrumpir la conversación.

Se prevén los siguientes comandos:

- /w, /whisper y /msg: enviar un mensaje privado.
- /r y /reply: responder a la última persona que escribió por privado.

También se podrá seleccionar un amigo desde la interfaz para
facilitar el uso desde el celular.

Los mensajes privados se distinguirán mediante un color y las
etiquetas “Privado de” y “Privado para”. La interfaz indicará
claramente a quién se enviará cada respuesta.

Dentro de una sala o partida, los mensajes sin comando se enviarán
al canal público correspondiente.

### Persistencia y privacidad

Cuando se implemente la base de datos, cada mensaje tendrá un
identificador, fecha y hora, tipo, autor cuando corresponda y una
referencia a su sala, partida o conversación privada.

El servidor validará la identidad del remitente y el acceso al canal.
Los mensajes privados se entregarán únicamente a las cuentas
involucradas; no se enviarán al resto de los jugadores para ocultarlos
después en el frontend.

La interfaz podrá combinar los canales autorizados en orden
cronológico, sin modificar su pertenencia ni sus permisos.

La persistencia de mensajes, los canales separados y la mensajería
privada son funcionalidades planificadas, todavía no implementadas.

## Efectos de cartas y combate

Carta representa un ejemplar inmutable identificado por su ID y su tipo.
Los comportamientos se organizan en el paquete modelo.efecto.

La implementación utiliza composición y el patrón Strategy:
Partida selecciona el comportamiento correspondiente mediante registros
que relacionan tipos de carta con implementaciones de interfaces.

### Cartas jugadas desde la mano

EfectoCarta define el contrato para ejecutar estas cartas.
Actualmente lo implementan:

- EfectoBolaDeNieve.
- EfectoEsquivar.
- EfectoDevolver.
- EfectoCuracion.
- EfectoGrinch.
- EfectoEnTarlipes.

Partida valida la solicitud antes de consumir la carta y ejecutar su efecto.

Un adaptador privado implementa ContextoEfectoCarta. Permite solicitar
operaciones de ataque, curación, robo, descarte y equipamiento sin exponer
las colecciones internas de la partida.

### Efectos automáticos de los equipos

Los beneficios se separan según el momento de activación:

| Contrato | Implementación | Momento |
| --- | --- | --- |
| EfectoInicioTurno | EfectoArbolNavideno | Al comenzar el turno del portador. |
| EfectoAlRecibirDanio | EfectoBullyNavideno | Después del daño de una Bola de nieve. |
| EfectoAnteDanioLetal | EfectoLoRecalentadoDeAnoche | Antes de aplicar un daño letal. |
| EfectoAlCausarDanio | EfectoPinzaDelAsador | Después del daño de una bola y antes de eliminar al objetivo. |
| EfectoAlFinalizarBola | EfectoDobleEntrega | Cuando termina toda la cadena de una bola. |

Las clases de efectos contienen sus comportamientos y mensajes específicos.
Partida coordina su ejecución y conserva el control del estado.

Consultar un permiso no debe consumir cartas, activar beneficios ni
generar mensajes de actividad.

### Responsabilidades del combate

Combate conserva el ataque pendiente, genera sus identificadores y plazos
de respuesta, y permite consultar su vencimiento y tiempo restante.

Partida administra turnos, manos, vidas, daño, eliminaciones y resultado.
Sus métodos sincronizados protegen también las operaciones que delega
en Combate y Equipamiento.

Devolver abre una nueva respuesta sin reanudar el reloj del turno.
La cadena termina cuando se bloquea, impacta o se cancela.

ResultadoBolaDeNieve distingue:

- BLOQUEADA: la bola terminó bloqueada.
- IMPACTO: alcanzó a un jugador, incluida la activación de Lo recalentado.
- CANCELADA: la resolución se interrumpió, por ejemplo, por un abandono.

Esta clasificación permite que Doble entrega distinga un bloqueo de una
salvación, sin depender únicamente de las vidas finales.

### Efectos de curación

EfectoCuracion se configura con cantidad, alcance y nombre de la carta.
Las comidas reutilizan esta implementación.

Las comidas individuales recuperan una vida a quien las juega.
Asado y Lechón con ensalada rusa recuperan una vida a todos los
participantes activos.

La curación no supera el máximo del rol ni revive eliminados.
Si nadie puede recuperar vidas, la carta no se consume.

El frontend recibe los identificadores de las comidas propias utilizables.
Estos permisos sirven para presentar los controles; el servidor vuelve
a comprobarlos al recibir cada solicitud.

### Equipamiento y transferencias

Equipamiento conserva una carta activa por jugador sin administrar
manos, descarte ni conexiones.

Las operaciones se distinguen de esta forma:

- Retirar: libera la zona y devuelve la carta sin decidir su destino.
- Desequipar: retira el equipo y lo envía al descarte.
- Equipar: valida la solicitud, desequipa el anterior y coloca el nuevo.
- Robar equipo: lo retira del objetivo y lo transfiere a la mano del jugador,
  sin descartarlo ni equiparlo automáticamente.

Grinch utiliza el robo de equipo. En tarlipes utiliza desequipar.
Ambas cartas se descartan después de jugarlas.

Los equipos son públicos, se conservan al reconectar y se descartan
al abandonar o quedar eliminado.

### Reglas de los beneficios implementados

- Árbol navideño: roba una carta adicional al comenzar el turno.
  Equiparlo o reconectarse no repite el robo.
- Bully navideño: tras recibir daño de una bola, causa un daño directo
  e indefendible al atacante. Primero se resuelve la posible eliminación
  del objetivo y el resultado; la represalia solo ocurre si la partida
  continúa. No consume el equipo ni activa otra represalia.
- Lo recalentado de anoche: evita un daño letal y deja una vida.
  Descarta primero el equipo, después toda la mano y finalmente intenta
  robar una carta del mazo. Funciona contra bolas y represalias de Bully,
  pero no contra inactividad o abandono.
- Pinza del asador: cuando la bola causa daño, roba una comida aleatoria
  del objetivo, incluidas las grupales. El robo ocurre antes de descartar
  la mano por eliminación y no revela públicamente la carta transferida.
  No consume el equipo.
- Doble entrega: si la primera bola termina bloqueada, habilita un segundo
  lanzamiento contra cualquier objetivo válido, incluido el anterior.
  Espera toda la cadena de devoluciones y exige otra bola en la mano.
  Un impacto, incluida la salvación de Lo recalentado, no habilita el
  beneficio. El máximo es de dos lanzamientos por turno y el equipo debe
  permanecer colocado.

### Mazo y descarte

Mazo crea los ejemplares, mezcla, permite robar y administra el descarte.

Al robar la última carta del mazo se intenta reciclar inmediatamente
el descarte. Se conserva su carta superior y se mezclan las anteriores
para formar el nuevo mazo, manteniendo sus identificadores.

El mismo reciclado se intenta si una solicitud de robo encuentra
el mazo vacío. Si no hay cartas reciclables, se entregan únicamente
las disponibles.

Las cartas en manos y los equipos activos no participan del reciclado.
El robo de Lo recalentado utiliza esta operación normal del mazo.

Las pruebas de MazoRecicladoTest se encuentran en src/test/java y verifican
la conservación de la carta superior y el momento del reciclado.