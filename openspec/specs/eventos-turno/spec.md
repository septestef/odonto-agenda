# eventos-turno Specification

## Purpose
TBD - created by archiving change c-07-disponibilidad-y-reserva-api. Update Purpose after archive.

## Requirements

### Requirement: Evento de turno reservado
Cada reserva confirmada MUST publicar un único evento de dominio `TurnoReservado` con una copia inmutable de los datos necesarios para notificar: turno, inicio y fin, origen, prestación, datos de contacto del paciente vigentes al reservar, datos públicos del consultorio y el token de gestión en claro. MUST NOT contener entidades de persistencia.

#### Scenario: Datos del evento
- **WHEN** se completa una reserva online del viernes 2026-11-27 a las 09:00 con email `luis@example.com`
- **THEN** se publica un `TurnoReservado` con el id del turno creado, inicio `2026-11-27T12:00:00Z`, origen `ONLINE`, email `luis@example.com` y un token cuyo SHA-256 en hexadecimal coincide con el `token_hash` guardado

### Requirement: El evento se entrega solo después del commit
`TurnoReservado` MUST publicarse dentro de la transacción de la reserva, de modo que un consumidor transaccional posterior al commit lo reciba solo si la reserva se confirmó. Una reserva rechazada o revertida MUST NOT entregar el evento a esos consumidores (DD-09).

#### Scenario: Reserva confirmada
- **GIVEN** un consumidor que escucha `TurnoReservado` después del commit
- **WHEN** se completa una reserva
- **THEN** el consumidor recibe exactamente un evento y, al recibirlo, el turno ya es visible en la base desde otra conexión

#### Scenario: Reserva rechazada por conflicto
- **GIVEN** un consumidor que escucha `TurnoReservado` después del commit
- **WHEN** una reserva recibe 409 `RN-TU-01` o 409 `RN-PA-03`
- **THEN** el consumidor no recibe ningún evento por esa reserva

### Requirement: El token del evento no se filtra
El token en claro del evento MUST existir solo en memoria: MUST NOT persistirse y la representación textual del evento (por ejemplo, al loguearlo) MUST NOT mostrarlo.

#### Scenario: Evento logueado
- **WHEN** se convierte a texto un `TurnoReservado`
- **THEN** el texto no contiene el token en claro

### Requirement: Evento de cancelación del paciente

La cancelación directa por token MUST publicar un solo `TurnoCancelado(turno, canceladoPor, canceladoEn)` dentro de la transacción, con un snapshot inmutable, `PACIENTE` y el instante de cancelación. El snapshot MUST usar el email de gestión admisible del turno o no llevar email; MUST NOT contener token ni entidades de persistencia. C-08 MUST procesarlo solo tras commit.

#### Scenario: Cancelación con destinatario admisible
- **WHEN** confirma la cancelación por token, con el SMTP falso bloqueado al empezar el intento
- **THEN** el POST responde 204 sin esperar al correo, el consumidor recibe un solo evento y ve el turno cancelado desde otra conexión; el aviso va al buzón del snapshot, sin link y con registro asociado al original

### Requirement: Evento de reprogramación del paciente

Una reprogramación por token MUST publicar únicamente `TurnoReprogramado(anterior, nuevo, token)` dentro de la transacción, no `TurnoCancelado` ni `TurnoReservado`. Los snapshots MUST ser inmutables, sin entidades de persistencia; `nuevo` MUST incluir el destinatario heredado y `token` MUST ser solo el token nuevo en claro, en memoria y redactado en la representación textual. C-08 MUST procesarlo solo tras commit.

#### Scenario: Sucesor y token del aviso
- **WHEN** confirma una reprogramación con destinatario y transporte admisibles
- **THEN** el consumidor ve el original cancelado y el sucesor reservado desde otra conexión y recibe un solo evento; se notifica al destinatario del sucesor con solo el link nuevo y se asocia el registro al sucesor. El SHA-256 del token coincide con el hash del sucesor y el token no aparece al convertir el evento a texto

### Requirement: Los avisos de gestión no revierten ni anticipan cambios

La gestión MUST NOT entregar eventos a consumidores posteriores al commit ante un rechazo o rollback, aunque ya se haya publicado el evento dentro de la transacción. Una falla posterior de SMTP, de cola o de auditoría MUST NOT revertir el resultado de la gestión ni causar reenvío. El envío sigue las garantías de mejor esfuerzo de C-08 (DD-09, RN-NO-04).

#### Scenario: Rollback después de publicar
- **WHEN** se fuerza rollback después de escribir y publicar un evento de gestión
- **THEN** el consumidor posterior al commit no recibe el evento y no hay email ni fila de notificación

#### Scenario: Falla SMTP sin revertir
- **WHEN** SMTP falla por un aviso admitido para una reprogramación ya confirmada
- **THEN** el original sigue cancelado y el sucesor reservado, el 204 no cambia y C-08 registra `ERROR`/`SMTP_FALLO` si la escritura está disponible, sin reenvío ni filtración
