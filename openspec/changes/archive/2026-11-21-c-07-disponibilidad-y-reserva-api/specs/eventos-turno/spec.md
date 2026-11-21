## ADDED Requirements

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
