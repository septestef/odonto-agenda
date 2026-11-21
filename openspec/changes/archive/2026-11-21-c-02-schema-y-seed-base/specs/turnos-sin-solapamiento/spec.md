## Purpose

Garantiza en la base de datos, y no solo en el código, que un odontólogo nunca tenga dos turnos activos superpuestos, aun cuando dos reservas del mismo horario lleguen al mismo tiempo (RN-TU-01, DD-03).

## ADDED Requirements

### Requirement: Sin turnos activos superpuestos
La base MUST rechazar cualquier inserción o modificación que deje a un odontólogo con dos turnos en estado `RESERVADO`, `ATENDIDO` o `AUSENTE` cuyos intervalos `[inicio, fin)` se superpongan. El rechazo MUST identificarse como una violación de la restricción `turno_sin_solapamiento`, para que la capa de servicio pueda traducirlo al código `RN-TU-01`.

#### Scenario: Superposición rechazada
- **GIVEN** un turno `RESERVADO` del odontólogo A de 10:00 a 10:45
- **WHEN** se inserta otro turno `RESERVADO` del odontólogo A de 10:30 a 11:00
- **THEN** la base rechaza la inserción con una violación de `turno_sin_solapamiento`

#### Scenario: Turno contenido dentro de otro
- **GIVEN** un turno `ATENDIDO` del odontólogo A de 09:00 a 10:00
- **WHEN** se inserta un turno `RESERVADO` del odontólogo A de 09:15 a 09:45
- **THEN** la base rechaza la inserción

#### Scenario: Turnos contiguos permitidos
- **GIVEN** un turno `RESERVADO` del odontólogo A de 10:00 a 10:30
- **WHEN** se inserta otro turno `RESERVADO` del odontólogo A de 10:30 a 11:00
- **THEN** la base acepta la inserción, porque el fin de un turno no ocupa el instante en que empieza el siguiente

#### Scenario: Odontólogos distintos en el mismo horario
- **GIVEN** un turno `RESERVADO` del odontólogo A de 10:00 a 10:30
- **WHEN** se inserta un turno `RESERVADO` del odontólogo B de 10:00 a 10:30
- **THEN** la base acepta la inserción

#### Scenario: Reactivar un turno sobre un horario ocupado
- **GIVEN** un turno `CANCELADO` y un turno `RESERVADO` del odontólogo A en el mismo horario
- **WHEN** se cambia el estado del turno cancelado a `RESERVADO`
- **THEN** la base rechaza la modificación

### Requirement: Los turnos cancelados no ocupan lugar
Un turno en estado `CANCELADO` MUST NOT bloquear su horario: la base MUST aceptar un turno activo superpuesto con uno o más turnos cancelados del mismo odontólogo.

#### Scenario: Reserva sobre un turno cancelado
- **GIVEN** un turno `CANCELADO` del odontólogo A de 10:00 a 10:30
- **WHEN** se inserta un turno `RESERVADO` del odontólogo A de 10:00 a 10:30
- **THEN** la base acepta la inserción

#### Scenario: Cancelar libera el horario
- **GIVEN** un turno `RESERVADO` del odontólogo A de 10:00 a 10:30
- **WHEN** se lo cambia a `CANCELADO` y después se inserta otro `RESERVADO` en ese horario
- **THEN** la base acepta la nueva inserción

### Requirement: Reservas concurrentes del mismo horario
Cuando dos transacciones intentan registrar al mismo tiempo turnos activos superpuestos para el mismo odontólogo, exactamente una MUST confirmarse y la otra MUST fallar con una violación de `turno_sin_solapamiento`, sin depender de validaciones previas en la aplicación.

#### Scenario: Dos inserciones simultáneas
- **WHEN** dos transacciones concurrentes insertan cada una un turno `RESERVADO` del odontólogo A de 10:00 a 10:30 y ambas intentan confirmar
- **THEN** exactamente una se confirma, la otra falla por `turno_sin_solapamiento` y en la base queda un único turno activo en ese horario
