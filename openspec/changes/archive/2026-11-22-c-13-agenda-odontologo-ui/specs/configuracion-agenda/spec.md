# Spec Delta — `configuracion-agenda` (C-13)

## ADDED Requirements

### Requirement: Horarios y bloqueos visibles en la agenda

La interfaz de agenda MUST consultar `GET /api/horarios` y `GET /api/bloqueos` con la misma sesión que usa para los turnos, y MUST combinarlos con `GET /api/turnos` para dibujar franjas por día de semana, huecos libres, fuera de horario y bloqueos. Cada bloqueo MUST mostrar su `motivo` y la lista de `turnosAfectados` devuelta por C-05 (id, inicio, fin, estado, nombre y apellido del paciente y nombre de la prestación). La existencia de un bloqueo MUST NO cancelar ni cambiar el estado de esos turnos: la acción de cancelar, si corresponde, MUST usar el endpoint de cancelación de C-12.

#### Scenario: Bloqueo con turnos adentro

- **WHEN** `GET /api/bloqueos` devuelve un bloqueo con un `turnosAfectados`
- **THEN** la agenda muestra el bloqueo, el aviso "El bloqueo no lo cancela: decidí vos" y el turno afectado con su acción de cancelación, sin haberlo cancelado

#### Scenario: Bloqueo sin turnos afectados

- **WHEN** un bloqueo del rango tiene `turnosAfectados` vacío
- **THEN** se dibuja el bloqueo con su motivo y no se muestra el aviso ni la lista de afectados

#### Scenario: Sin franjas cargadas

- **WHEN** `GET /api/horarios` devuelve una lista vacía
- **THEN** la agenda muestra el estado "Todavía no cargaste tus horarios" en lugar de una grilla vacía

### Requirement: Actualización de los datos de configuración después de cancelar

Después de cancelar desde la agenda un turno que estaba en `turnosAfectados`, la interfaz MUST volver a pedir turnos y bloqueos del rango visible para que el aviso y la lista de afectados dejen de incluirlo. La interfaz MUST NO manipular bloqueos ni horarios: C-13 es solo lectura de esos recursos.

#### Scenario: Cancelación desde el aviso del bloqueo

- **WHEN** se cancela un turno afectado y el POST responde 204
- **THEN** se recargan turnos y bloqueos, el aviso se actualiza y el bloqueo sigue existiendo con el mismo motivo

#### Scenario: Recarga sin cambios en la configuración

- **WHEN** se navega a otra fecha o se cambia entre día y semana
- **THEN** se vuelven a consultar horarios y bloqueos para el rango correspondiente, sin escribirlos
