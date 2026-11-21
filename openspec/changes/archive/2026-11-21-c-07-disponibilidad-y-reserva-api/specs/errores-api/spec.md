## ADDED Requirements

### Requirement: Conflicto de turnos en la base como RN-TU-01
Cuando una escritura de turnos falla en la base por la restricción `turno_sin_solapamiento`, por un deadlock o por no poder tomar un lock, la API MUST responder 409 con `{ "codigo": "RN-TU-01", "mensaje": "Ese horario acaba de ser tomado, elegí otro" }` (RN-GL-01). La respuesta MUST NOT incluir SQL, nombres de restricciones ni mensajes de la base.

#### Scenario: Violación de la restricción anti-solapamiento
- **WHEN** un endpoint que escribe turnos recibe de la base una violación de exclusión de `turno_sin_solapamiento`
- **THEN** la API responde 409 con código `RN-TU-01` y el cuerpo no contiene `turno_sin_solapamiento` ni texto de la base

#### Scenario: Deadlock o falla de lock
- **WHEN** un endpoint que escribe turnos recibe de la base un deadlock o una falla al tomar un lock
- **THEN** la API responde 409 con código `RN-TU-01`

#### Scenario: Otra violación de integridad
- **WHEN** un endpoint recibe de la base una violación de integridad que no es de `turno_sin_solapamiento`
- **THEN** la API no la presenta como `RN-TU-01` y responde 500 con código `ERROR_INTERNO`
