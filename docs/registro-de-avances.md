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