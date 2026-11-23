# disponibilidad-turnos Specification

## Purpose
TBD - created by archiving change c-07-disponibilidad-y-reserva-api. Update Purpose after archive.

## Requirements

### Requirement: Inicios cada 15 minutos dentro de la franja
Para una fecha y una prestación, el sistema MUST ofrecer como candidatos los inicios que arrancan en el comienzo de cada franja de atención de ese día de la semana y avanzan de a 15 minutos. Un inicio MUST ofrecerse solo si el turno completo (inicio + duración de la prestación) termina a la hora de cierre de la franja o antes. En los escenarios, el reloj marca 2026-11-25T12:00:00Z (09:00 local), el horizonte es de 60 días y las horas son locales.

#### Scenario: Turno que entra completo en la franja
- **GIVEN** una franja el viernes de 09:00 a 10:00 y una prestación de 30 minutos
- **WHEN** se consulta la disponibilidad del viernes 2026-11-27
- **THEN** los horarios libres son 09:00, 09:15 y 09:30, y no se ofrece 09:45 porque terminaría 10:15

#### Scenario: Varias franjas en el mismo día
- **GIVEN** franjas el viernes de 09:00 a 10:00 y de 16:00 a 17:00 y una prestación de 60 minutos
- **WHEN** se consulta la disponibilidad del viernes 2026-11-27
- **THEN** los horarios libres son 09:00 y 16:00

#### Scenario: Día sin franjas de atención
- **GIVEN** que el odontólogo no tiene franjas los sábados
- **WHEN** se consulta la disponibilidad del sábado 2026-11-28
- **THEN** la lista de horarios libres está vacía

### Requirement: Anticipación mínima de 24 horas
El sistema MUST ofrecer solo inicios que sean iguales o posteriores al instante actual del servidor más 24 horas exactas (RN-TU-03). El "ahora" MUST salir del reloj del servidor, nunca del cliente.

#### Scenario: Borde exacto de 24 horas
- **GIVEN** una franja el jueves de 08:00 a 10:00 y una prestación de 30 minutos
- **WHEN** se consulta la disponibilidad del jueves 2026-11-26
- **THEN** los horarios libres empiezan en 09:00 (exactamente 24 h después del ahora) y no incluyen 08:00, 08:15, 08:30 ni 08:45

#### Scenario: Fecha ya pasada o de hoy
- **WHEN** se consulta la disponibilidad del martes 2026-11-24 o del miércoles 2026-11-25
- **THEN** la respuesta es exitosa con la lista de horarios libres vacía

### Requirement: Horizonte máximo de reserva
El sistema MUST ofrecer solo inicios cuya fecha local sea igual o anterior a la fecha de hoy del negocio más `app.reserva-max-dias` días (RN-TU-11). Una fecha posterior MUST devolver una lista vacía.

#### Scenario: Último día reservable
- **GIVEN** una franja los domingos de 09:00 a 10:00 y una prestación de 30 minutos
- **WHEN** se consulta la disponibilidad del domingo 2027-01-24 (hoy + 60 días)
- **THEN** los horarios libres son 09:00, 09:15 y 09:30

#### Scenario: Un día después del horizonte
- **GIVEN** una franja los lunes de 09:00 a 10:00
- **WHEN** se consulta la disponibilidad del lunes 2027-01-25 (hoy + 61 días)
- **THEN** la lista de horarios libres está vacía

### Requirement: Sin cruce con bloqueos
El sistema MUST excluir todo inicio cuyo intervalo `[inicio, inicio + duración)` se superponga con un bloqueo del odontólogo. Un turno que termina justo cuando empieza un bloqueo, o que empieza justo cuando termina, MUST ofrecerse.

#### Scenario: Bloqueo en medio de la franja
- **GIVEN** una franja el viernes de 09:00 a 12:00, un bloqueo el viernes 2026-11-27 de 10:00 a 11:00 y una prestación de 45 minutos
- **WHEN** se consulta la disponibilidad de ese viernes
- **THEN** se ofrecen 09:00 y 09:15, no se ofrece ningún inicio entre 09:30 y 10:45, y se ofrecen 11:00 y 11:15

#### Scenario: Bloqueo de varios días
- **GIVEN** un bloqueo desde el jueves 2026-11-26 a las 18:00 hasta el sábado 2026-11-28 a las 08:00
- **WHEN** se consulta la disponibilidad del viernes 2026-11-27
- **THEN** la lista de horarios libres está vacía

### Requirement: Sin cruce con turnos que ocupan lugar
El sistema MUST excluir todo inicio cuyo intervalo se superponga con un turno del odontólogo en estado `RESERVADO`, `ATENDIDO` o `AUSENTE`. Los turnos `CANCELADO` MUST NOT ocupar lugar.

#### Scenario: Turno reservado ocupa su horario
- **GIVEN** una franja el viernes de 09:00 a 11:00, un turno `RESERVADO` el viernes 2026-11-27 de 09:30 a 10:00 y una prestación de 30 minutos
- **WHEN** se consulta la disponibilidad de ese viernes
- **THEN** se ofrecen 09:00, 10:00, 10:15 y 10:30, y no se ofrecen 09:15, 09:30 ni 09:45

#### Scenario: Turno cancelado libera su horario
- **GIVEN** un turno `CANCELADO` el viernes 2026-11-27 de 09:30 a 10:00 dentro de una franja de 09:00 a 11:00
- **WHEN** se consulta la disponibilidad de ese viernes con una prestación de 30 minutos
- **THEN** 09:30 figura entre los horarios libres

#### Scenario: Turnos de otro odontólogo no interfieren
- **GIVEN** un turno `RESERVADO` de otro odontólogo el viernes 2026-11-27 de 09:00 a 10:00
- **WHEN** se consulta la disponibilidad de ese viernes para el odontólogo consultado
- **THEN** 09:00 figura entre sus horarios libres

### Requirement: Consulta pública de disponibilidad
`GET /api/public/{slug}/disponibilidad?prestacionId=&fecha=` MUST responder 200 con la fecha consultada, la duración de la prestación y la lista ordenada de horarios libres, cada uno con el instante con su offset y la hora local `HH:mm`. Sin sesión.

#### Scenario: Consulta exitosa
- **GIVEN** el odontólogo activo con slug `demo`, una prestación activa de 30 minutos y una franja el viernes de 09:00 a 10:00
- **WHEN** se hace `GET /api/public/demo/disponibilidad?prestacionId=<id>&fecha=2026-11-27`
- **THEN** la respuesta es 200 y el primer horario tiene inicio `2026-11-27T09:00:00-03:00` y hora `09:00`

#### Scenario: Parámetros faltantes o mal formados
- **WHEN** se omite `prestacionId` o `fecha`, o `fecha` no tiene formato `YYYY-MM-DD`
- **THEN** la API responde 400 con el formato uniforme de error

### Requirement: La disponibilidad no expone datos de pacientes
La respuesta de disponibilidad MUST contener solo horarios libres: MUST NOT incluir turnos, identificadores de turnos, nombres, DNI, emails ni teléfonos de pacientes, ni los motivos de los bloqueos (RN-SE-03).

#### Scenario: Día con turnos de otros pacientes
- **GIVEN** un turno `RESERVADO` de un paciente con DNI `12345678` el día consultado
- **WHEN** se consulta la disponibilidad de ese día
- **THEN** el cuerpo de la respuesta no contiene el DNI, el nombre ni el email de ese paciente ni el identificador del turno

### Requirement: Recurso público inexistente o inactivo
La consulta MUST responder 404 con código `NO_ENCONTRADO` y el mismo mensaje genérico cuando el slug no existe, el odontólogo está inactivo, o la prestación no existe, está dada de baja o pertenece a otro odontólogo, sin revelar cuál de los casos ocurrió.

#### Scenario: Prestación de otro odontólogo
- **GIVEN** una prestación activa que pertenece al odontólogo `otro`
- **WHEN** se consulta `GET /api/public/demo/disponibilidad` con el id de esa prestación
- **THEN** la API responde 404 con código `NO_ENCONTRADO`

#### Scenario: Prestación dada de baja
- **GIVEN** una prestación del odontólogo `demo` dada de baja
- **WHEN** se consulta su disponibilidad
- **THEN** la API responde 404 con código `NO_ENCONTRADO`

#### Scenario: Slug inexistente
- **WHEN** se consulta `GET /api/public/no-existe/disponibilidad?prestacionId=1&fecha=2026-11-27`
- **THEN** la API responde 404 con código `NO_ENCONTRADO`

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
