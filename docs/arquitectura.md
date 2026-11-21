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