# Spec Delta — `errores-api`

## ADDED Requirements

### Requirement: Rechazo por inicio en el pasado al crear un turno manual

Cuando el alta manual de un turno recibe un `inicio` en el pasado o igual al ahora del servidor, la API MUST responder 422 con `{ "codigo": "RN-TU-12", "mensaje": "..." }` en español rioplatense, sin crear ni modificar pacientes, turnos ni notificaciones. El reloj del servidor es la única fuente de “ahora” (RN-GL-02).

#### Scenario: Inicio ya pasado

- **GIVEN** el reloj del servidor en `2026-11-25T12:00:00Z` (09:00 local)
- **WHEN** se intenta cargar manualmente un turno con inicio `2026-11-25T08:45:00-03:00`
- **THEN** la API responde 422 con `codigo` `RN-TU-12` y el cuerpo no contiene datos del paciente ni detalles internos

#### Scenario: Inicio exactamente igual al ahora

- **GIVEN** el reloj del servidor fijado en el mismo instante que el `inicio` solicitado
- **WHEN** se intenta cargar manualmente ese turno
- **THEN** la API responde 422 con código `RN-TU-12`
