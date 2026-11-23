# Spec Delta — `disponibilidad-turnos`

## ADDED Requirements

### Requirement: Consulta autenticada de disponibilidad para turnos manuales

`GET /api/disponibilidad?prestacionId=&fecha=` MUST exigir sesión de odontólogo y MUST limitar los resultados al odontólogo de la sesión. Debe responder 200 con la misma forma que la consulta pública: fecha consultada, duración de la prestación y lista ordenada de horarios libres con instante con offset y hora local `HH:mm`. GET MUST NOT requerir CSRF. Sin sesión MUST responder 401 `NO_AUTENTICADO`; con sesión sin rol permitido MUST responder 403 `ACCESO_DENEGADO`.

#### Scenario: Disponibilidad autenticada con horarios de hoy

- **GIVEN** el reloj en `2026-11-25T12:00:00Z` (09:00 local), una franja hasta las 13:00 local y el odontólogo con sesión iniciada
- **WHEN** consulta `GET /api/disponibilidad?prestacionId=<id>&fecha=2026-11-25`
- **THEN** responde 200 e incluye horarios futuros de hoy como `09:15`, con offset `-03:00`, aunque estén a menos de 24 h

#### Scenario: Misma forma que la respuesta pública

- **GIVEN** una prestación activa propia y un día con horarios libres
- **WHEN** se consulta la disponibilidad autenticada y la pública del mismo día
- **THEN** ambas respuestas tienen la misma estructura de campos y el mismo orden de horarios, salvo la diferencia de anticipación

#### Scenario: Parámetros faltantes o mal formados

- **WHEN** se omite `prestacionId` o `fecha`, o `fecha` no tiene formato `YYYY-MM-DD`
- **THEN** la API responde 400 con el formato uniforme de error

#### Scenario: Prestación ajena, dada de baja o inexistente

- **GIVEN** una prestación dada de baja o que pertenece a otro odontólogo
- **WHEN** el odontólogo autenticado consulta su disponibilidad con esa prestación
- **THEN** la API responde 404 con código `NO_ENCONTRADO` y el mismo mensaje genérico

### Requirement: Reglas de la disponibilidad autenticada

La disponibilidad autenticada MUST aplicar el horizonte de `app.reserva-max-dias` (RN-TU-11) y MUST excluir todo inicio que cruce bloqueos o turnos activos (RN-TU-01). MUST NOT aplicar la anticipación de 24 h de RN-TU-03: solo se excluyen los inicios pasados o iguales al ahora del servidor. MUST NOT incluir datos de pacientes ni motivos de bloqueos (RN-SE-03).

#### Scenario: Dentro del horizonte pero con menos de 24 h

- **GIVEN** un horario libre mañana a las 09:00 local con el reloj en `2026-11-25T12:00:00Z`
- **WHEN** se consulta la disponibilidad autenticada de ese día
- **THEN** el horario figura en la respuesta aunque la disponibilidad pública no lo ofrezca

#### Scenario: Más allá del horizonte

- **GIVEN** el horizonte por defecto de 60 días
- **WHEN** se consulta la disponibilidad autenticada para una fecha posterior al último día permitido
- **THEN** la respuesta es 200 con la lista de horarios vacía

#### Scenario: Horario ocupado o bloqueado

- **GIVEN** un turno activo o un bloqueo que cubre un rango de la franja
- **WHEN** se consulta la disponibilidad autenticada de ese día
- **THEN** los inicios que se superponen no figuran en la respuesta y esta no contiene datos del paciente ni del bloqueo
