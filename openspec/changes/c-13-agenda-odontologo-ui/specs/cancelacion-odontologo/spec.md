# Spec Delta — `cancelacion-odontologo` (C-13)

## ADDED Requirements

### Requirement: Cancelación desde la agenda con confirmación

La interfaz MUST pedir confirmación antes de cancelar un turno, mediante un diálogo que muestre paciente, fecha y hora locales, prestación y origen, con el foco inicial en "No, volver". Al confirmar MUST enviar `POST /api/turnos/{id}/cancelar` sin cuerpo, reenvío de CSRF y sin datos del odontólogo en la solicitud. Si el turno está dentro de un bloqueo, el texto sobre el horario que vuelve a quedar libre MUST omitirse. Ante 204, incluida la repetición sobre un turno ya `CANCELADO`, la interfaz MUST cerrar el diálogo, volver a pedir el rango visible y mostrar el aviso "Turno cancelado".

#### Scenario: Confirmación y cancelación efectiva

- **WHEN** el odontólogo toca Cancelar en un turno futuro y confirma
- **THEN** se envía el POST sin cuerpo, se recibe 204, se cierra el diálogo, se recarga la agenda y aparece el aviso "Turno cancelado"

#### Scenario: Turno dentro de un bloqueo

- **WHEN** se cancela un turno marcado como afectado por un bloqueo
- **THEN** el diálogo no afirma que el horario vuelve a quedar libre y, tras el 204, el aviso y las listas de turnos y bloqueos se refrescan

#### Scenario: Repetición idempotente

- **WHEN** el turno ya estaba `CANCELADO` y el POST responde 204 sin cambiarlo
- **THEN** la interfaz trata la respuesta como éxito, cierra el diálogo y recarga el rango sin mostrar un error

### Requirement: Reacciones visibles a las respuestas de cancelación

La interfaz MUST traducir las respuestas del contrato a los estados visuales aprobados sin reintentar el POST automáticamente: 409 `RN-TU-07` MUST mostrar "No se pudo cancelar / Este turno ya empezó" y recargar el rango; 404 `NO_ENCONTRADO` y 409 `RN-TU-01` MUST mostrar "Este turno cambió" y recargar el rango; 500 `ERROR_INTERNO`, `SIN_CONEXION` y cualquier otra falla MUST mostrar "No pudimos cargar la agenda" con Reintentar. Un 401/403 de sesión o CSRF MUST seguir el manejo de sesión vencida del cliente API. Después de cualquier respuesta, la interfaz MUST NO dar por cancelado un turno hasta ver el 204 y la recarga.

#### Scenario: El turno ya empezó

- **WHEN** el POST responde 409 `RN-TU-07`
- **THEN** se muestra el aviso "Este turno ya empezó. Actualizamos la agenda." y se vuelve a pedir el rango visible

#### Scenario: Recurso ajeno, inexistente o lock

- **WHEN** el POST responde 404 `NO_ENCONTRADO` o 409 `RN-TU-01`
- **THEN** se muestra el aviso "Este turno cambió", se recarga el rango y no se reenvía la solicitud

#### Scenario: Error del servidor o sin conexión

- **WHEN** el POST responde 500 `ERROR_INTERNO` o falla la red
- **THEN** se muestra el aviso "No pudimos cargar la agenda" con Reintentar, sin aún reintentar la cancelación

### Requirement: Acciones de cancelar coherentes con el estado

La interfaz MUST NO ofrecer Cancelar para turnos `CANCELADO`, `ATENDIDO` o `AUSENTE`, ni para turnos cuyo inicio ya pasó según el "ahora" visual. La ausencia del botón MUST NO reemplazar la validación del servidor: si el estado cambió y llega un 409, la interfaz MUST mostrar el aviso correspondiente y refrescar la agenda.

#### Scenario: Turno finalizado sin acción

- **WHEN** se renderiza un turno `ATENDIDO` o un `RESERVADO` ya iniciado
- **THEN** no aparece el botón Cancelar en la lista ni en el detalle

#### Scenario: Carrera con otra cancelación

- **WHEN** otra sesión del mismo odontólogo o el paciente cancela el turno antes de que se confirme desde esta agenda
- **THEN** servidor responde según su contrato y la interfaz refresca el rango en lugar de conservar acciones sobre un estado viejo
