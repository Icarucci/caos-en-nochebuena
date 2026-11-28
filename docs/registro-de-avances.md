# Registro de avances

## 16 de noviembre de 2026

### Desarrollo realizado

- Creación del proyecto con Java 21, Spring Boot y Maven.
- Comunicación entre navegadores mediante WebSocket.
- Creación de salas privadas con código y máximo de ocho jugadores.
- Incorporación de nombres, contador de conexiones y lugares reservados.
- Chat de texto en la sala de espera.
- Inicio de partida autorizado al anfitrión, con cuatro jugadores conectados como mínimo.
- Recuperación de sesión mediante Reconectar y salida mediante Abandonar.
- Recuperación del historial de actividad al reconectar.
- Expulsión de invitados desde la sala de espera.

### Control de versiones

- Creación de las ramas main, develop y feature/salas-privadas.
- Publicación del repositorio en GitHub.
- Integración de feature/salas-privadas en develop mediante el pull request #1.

## 17 de noviembre de 2026

### Desarrollo realizado

- Creación de la rama feature/reparto-de-roles.
- Definición de roles, vidas iniciales y objetivos en Rol.
- Sorteo de roles según la cantidad de jugadores mediante RepartidorDeRoles.
- Almacenamiento del reparto en el servidor.
- Entrega privada del rol y recuperación del mismo al reconectar.
- Identificación pública de Papá Noel.
- Incorporación de Partida para gestionar las fases de preparación.
- Confirmación individual del rol mediante Continuar.
- Avance a la elección de habilidades cuando todos los participantes confirmaron.
- Corrección de la espera cuando un participante abandona.
- Comprobación de salas guardadas después de reiniciar el servidor.

### Verificación manual

- Visualización de los roles y de Papá Noel.
- Confirmación de roles y avance de etapa.
- Abandono de un jugador pendiente de confirmar sin bloquear al resto.
- Limpieza del acceso guardado cuando la sala ya no existe.

### Próximos pasos

- Documentar las reglas y la arquitectura.
- Implementar el sorteo de dos habilidades distintas por jugador.
- Permitir elegir una habilidad y conservarla al reconectar.
- Completar las confirmaciones antes del primer turno.

### Preparación de la partida

- Confirmación del rol antes de avanzar.
- Catálogo de 16 habilidades.
- Reparto de dos opciones por jugador, sin repeticiones.
- Elección definitiva de una habilidad y recuperación al reconectar.
- Espera hasta que todos los participantes elijan.
- Evaluación de victoria al abandonar y al completar la preparación.
- Aviso privado de victoria o derrota.
- Verificación manual con varias pestañas.

Los efectos de las habilidades, el reparto de cartas y los turnos
están pendientes de implementar.

### Salas públicas y privadas

- Incorporamos la elección del tipo de sala al crearla.
- Las salas privadas permiten ingresar mediante un código.
- Las salas públicas aparecen en un listado con su ocupación y estado.
- El botón de ingreso se deshabilita cuando la sala está llena o la partida comenzó.
- Al ingresar a otra sala, se abandona la anterior.
- Las salas sin jugadores se eliminan inmediatamente.
- Si todos sus jugadores permanecen desconectados durante 5 minutos,
  la sala se elimina automáticamente.
- Una reconexión antes del vencimiento cancela la eliminación.

#### Verificación manual

- Creación e ingreso a salas públicas y privadas.
- Control del máximo de 8 jugadores.
- Cambio de sala sin mantener el asiento anterior.
- Eliminación de salas vacías.
- Eliminación por desconexión y cancelación al reconectarse.

#### Detalles técnicos

- Habilitamos tareas programadas con ConfiguracionDeTareas.
- La limpieza se ejecuta cada 10 segundos mediante @Scheduled.
- Las operaciones sobre los jugadores y la limpieza comparten
  el bloqueo synchronized para evitar modificaciones simultáneas.

### Sistema de turnos

- Papá Noel comienza la partida.
- El orden de los demás jugadores se sortea después de que todos
  elijan su habilidad y se conserva durante la partida.
- Cada turno individual dura un minuto.
- Solo el jugador de turno puede finalizarlo manualmente.
- Al vencer el tiempo, el servidor avanza al siguiente jugador.
- La reconexión recupera el orden y el tiempo restante.
- Al completar el turno 24, si la partida sigue en curso,
  gana el bando de Papá Noel.
- Ajustamos la victoria del Arrepentido según la mitad de la partida.

#### Pendiente

- Eliminación por perder dos turnos propios consecutivos. (IMPLEMENTADO)
- Pausa del reloj durante las respuestas a cartas.
- Implementación de cartas e intervenciones.

### Mazo y manos

- Creamos el catálogo de 100 cartas con sus categorías y cantidades.
- Cada copia tiene un identificador único.
- Cada jugador recibe 4 cartas iniciales y roba 2 al comenzar su turno.
- La mano propia es privada; los demás ven solamente su cantidad.
- Al finalizar el turno, se elige el descarte necesario para conservar
  un máximo de 4 cartas.
- Al vencer el tiempo, el exceso se descarta al azar.
- Por abandono o eliminación, toda la mano pasa al descarte.
- El mazo reutiliza el descarte mezclado cuando se agota.
- La reconexión recupera la mano sin repartir ni robar nuevamente.

#### Verificación manual

- Reparto inicial, robo por turno y contadores.
- Selección y descarte del exceso de cartas.
- Descarte automático por vencimiento.
- Recuperación de la mano al reconectarse.

#### Pendiente de verificar

- Reciclado del descarte al agotar el mazo.

### Organización del backend

- Separamos las clases en paquetes por responsabilidad.
- Actualizamos los package e imports mediante la refactorización de Java.
- Documentamos la estructura en docs/arquitectura.md.

### Ataques, defensas y cierre de partidas

- Incorporamos vidas actuales y eliminación por daño.
- Agregamos Bola de nieve, Esquivar y Devolver.
- El reloj del turno se pausa mientras se resuelve un ataque.
- Sin defensa propia, se abre una espera de ayuda de 5 segundos;
  al vencer, el daño se aplica automáticamente.
- Incorporamos eventos al registro de actividad y revelación
  del rol de los eliminados.
- Agregamos el cartel de resultado y el botón Salir.
- Implementamos la eliminación de la sala al finalizar.
- Reorganizamos el backend en paquetes y documentamos la arquitectura.


### Pendiente: chat y actividad unificados

- El chat estará disponible desde el ingreso a la sala y durante la partida.
- En la sala de espera mostrará mensajes de los jugadores y avisos
  de ingreso, desconexión, reconexión, abandono y expulsión.
- Durante la partida, el mismo chat incorporará las acciones del juego:
  turnos, ataques, defensas, daño, eliminaciones y resultado.
- Los mensajes y eventos aparecerán en orden cronológico,
  diferenciando visualmente los mensajes del sistema.
- Al comenzar la partida se conservarán los mensajes anteriores.
- La reconexión recuperará el historial disponible de la sala.
- Nunca se publicarán manos ni roles que continúen ocultos.

### Verificación del cierre de partida — 26/11/2026

- Los conectados reciben el cartel con el resultado.
- Salir vuelve al inicio sin ofrecer reconexión.
- La sala finalizada desaparece del listado.
- Los jugadores desconectados no pueden recuperar una sala finalizada.

### Decisión de arquitectura: canales de chat — 26/11/2026

- Se definió que el panel de chat podrá mostrar mensajes de distintos
  canales sin mezclar sus historiales ni sus destinatarios.
- A futuro se separarán los canales de sala, partida y conversación
  privada.
- Los mensajes privados podrán leerse y responderse desde el inicio
  o durante una partida, pero no formarán parte del historial de esta.
- Se documentaron los comandos previstos y las validaciones del servidor.
- La persistencia y los canales independientes quedan pendientes.

### Chat unificado de sala — 26/11/2026

- Se unificaron las conversaciones y los eventos públicos en un historial
  por sala mediante MensajeSala, con tipos CHAT y SISTEMA.
- El mismo panel está disponible en el lobby y durante la partida.
- Los eventos se muestran en cursiva, sin el prefijo “Sistema”.
- Se registran ingresos, reconexiones, pérdidas de conexión, abandonos,
  cambios de sala y expulsiones.
- Al iniciar la partida se conserva el historial del lobby.
- Se verificó manualmente el envío de mensajes, la actividad del juego
  y la recuperación del historial en orden y sin duplicados al reconectar.

  ### Separación inicial de efectos de cartas — 27/11/2026

- Se creó la interfaz EfectoCarta y el contrato ContextoEfectoCarta.
- Bola de nieve, Esquivar y Devolver tienen clases de efecto propias.
- Partida valida las solicitudes y delega la ejecución del efecto.
- El contexto permite operar sin exponer las colecciones internas.
- Se verificó que el combate mantiene su comportamiento anterior.
- Queda pendiente separar la gestión del combate de Partida.

### Extracción del estado de combate — 27/11/2026

- Se trasladaron a Combate el ataque pendiente y sus plazos de respuesta.
- Partida delega la apertura, el cierre y las consultas del ataque.
- Se conservaron el minuto de respuesta y los cinco segundos de ayuda
  cuando el objetivo no tiene defensa.
- Las pruebas Maven finalizaron correctamente.
- Se verificaron manualmente defensas, devoluciones, daño automático,
  abandonos durante ataques y eliminaciones.

  ### Cartas de comida — 27/11/2026

- Se implementó EfectoCuracion, configurable por cantidad y alcance.
- Las comidas individuales recuperan una vida a quien las juega.
- Asado y Lechón con ensalada rusa recuperan una vida a todos los
  participantes activos, incluidos los enemigos.
- La curación no supera las vidas iniciales del rol ni revive eliminados.
- Solo pueden usarse durante el turno propio, sin ataques pendientes.
- Si nadie puede recuperar vidas, la carta no se consume.
- Se permite usar varias comidas en el mismo turno.
- El servidor valida la propiedad de la carta y todas las condiciones.
- Las comidas utilizadas pasan al descarte y se registran en la actividad.
- Se verificó manualmente el funcionamiento individual y grupal,
  los límites de vidas y los permisos de uso.
- Los botones actuales son provisorios. En React se seleccionará la
  carta con clic o toque y se confirmará su uso antes de enviarlo.

  ### Zona de equipo — 27/11/2026

- Se creó Equipamiento para administrar un equipo activo por jugador.
- Equipar valida la carta y el turno antes de modificar el estado.
- Si existe un equipo anterior, se desequipa y se envía al descarte
  antes de colocar el nuevo.
- La operación interna de retirar devuelve la carta sin descartarla,
  permitiendo reutilizarla en un futuro robo de equipo.
- Equipar está permitido durante el turno propio, sin ataques pendientes.
- Los equipos son públicos y se conservan al reconectar.
- Al abandonar o quedar eliminado, el equipo pasa al descarte.
- Se verificaron manualmente estos comportamientos.
- Los beneficios particulares y las cartas que quitan o roban equipos
  siguen pendientes.

  ### Beneficio de Árbol navideño — 27/11/2026

- Se creó EfectoInicioTurno para comportamientos ejecutados al comenzar
  un turno y EfectoArbolNavideno como primera implementación.
- Árbol navideño permite robar una carta adicional mientras esté equipado.
- Equiparlo o reconectarse no provoca robos adicionales.
- Reemplazarlo elimina su beneficio para los siguientes turnos.
- El límite al finalizar el turno continúa siendo de cuatro cartas.
- Se incorporó al contexto una operación reutilizable de robo de cartas.
- Las pruebas Maven finalizaron correctamente y se verificó manualmente
  el funcionamiento del equipo.