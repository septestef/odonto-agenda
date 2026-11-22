# Delta: Eventos de turno

## ADDED Requirements

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
