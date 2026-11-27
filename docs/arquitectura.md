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

Carta representa un ejemplar identificado por su ID y su tipo.
Los comportamientos se separan mediante la interfaz EfectoCarta.

Actualmente existen tres implementaciones:

- EfectoBolaDeNieve.
- EfectoEsquivar.
- EfectoDevolver.

Partida valida la solicitud y consume la carta antes de ejecutar su efecto.
Un adaptador privado implementa ContextoEfectoCarta y permite a los
efectos solicitar operaciones sin exponer las colecciones internas.

Esta organización utiliza composición y el patrón Strategy:
cada comportamiento implementa un contrato común.

Combate conserva el ataque pendiente, crea los plazos de respuesta
y permite consultar su vencimiento y los segundos restantes.

Partida conserva la coordinación de turnos, manos, vidas, daño,
eliminaciones y resultado. Sus métodos sincronizados protegen también
las operaciones que delega en Combate.

### Efectos de curación

EfectoCuracion implementa EfectoCarta y se configura con cantidad,
alcance y nombre de la carta. Las comidas reutilizan esta implementación.

Partida valida la jugada antes de consumir la carta. El contexto permite
consultar participantes activos y recuperar vidas sin exponer los mapas
internos. Partida aplica el límite de vidas y registra la recuperación.

El frontend recibe los identificadores de las comidas propias utilizables.
Estos permisos sirven para presentar los controles; el servidor vuelve
a comprobarlos al recibir cada solicitud.