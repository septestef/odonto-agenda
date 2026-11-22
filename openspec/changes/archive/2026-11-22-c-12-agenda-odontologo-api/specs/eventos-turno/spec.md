# Spec Delta

## Purpose

Agregar el contrato del emisor autenticado de `TurnoCancelado`, sin modificar eventos de reserva ni el consumidor C-08.

P6 aprobada y apply autorizado explícitamente el 2026-11-22; primera etapa limitada a preparación/baseline según design.md.

## ADDED Requirements

### Requirement: Emisión de cancelación por odontólogo
Cada transición efectiva a CANCELADO por el odontólogo MUST publicar dentro de su transacción, después del flush, un único `TurnoCancelado` con `canceladoPor=ODONTOLOGO`, `canceladoEn` idéntico al persistido y resumen inmutable del turno. El evento MUST NOT contener entidades, token o hash. Repeticiones idempotentes y rechazos MUST NOT publicar otro evento.

#### Scenario: Repetición y rechazo
- **WHEN** el POST es idempotente, rechazado por pertenencia/tiempo, o falla por inconsistencia interna de ATENDIDO/AUSENTE futuro con 500 ERROR_INTERNO según P4 resuelta
- **THEN** no se publica un evento de cancelación por ese pedido

### Requirement: Resumen del turno cancelado
El resumen MUST conservar el contrato existente: `turnoId,odontologoId,inicio,fin,prestacionId,prestacionNombre,duracionMinutos,pacienteNombre,pacienteApellido,pacienteEmail,pacienteTelefono,odontologoNombre,odontologoApellido,direccionConsultorio`. La duración MUST provenir de fin-inicio, no de una duración mutable de prestación. Dirección MUST admitir null.

#### Scenario: Snapshot de cancelación y duración fijada
- **WHEN** turno.email_gestion conserva `original@example.test`, paciente.email cambió a `nuevo@example.test` y la prestación pasó de 30 a 45 minutos, y se cancela el turno original de 30 minutos
- **THEN** se publica una vez con pacienteEmail `original@example.test`, duración 30, inicio/fin originales y autor/instante persistidos, sin token/hash; los otros datos conservan sus valores leídos al cancelar y el aviso nunca va a `nuevo@example.test`

### Requirement: Datos vigentes al cancelar
Nombre/apellido/teléfono del paciente, nombre de prestación y datos del consultorio del resumen MUST ser los vigentes leídos al cancelar, sin locks explícitos de asociaciones.

#### Scenario: Datos editados antes de cancelar
- **WHEN** el teléfono del paciente o el nombre de la prestación cambian después de reservar y antes de cancelar
- **THEN** el resumen publicado lleva los valores vigentes leídos al cancelar

### Requirement: Destinatario del aviso de cancelación
`pacienteEmail` MUST salir exclusivamente de `turno.email_gestion` si es admisible según `BuzonCorreo`, o ser null en caso contrario; MUST NOT salir de `paciente.email`, request, otro turno ni auditoría, ni normalizarse para forzar admisión. Sin destinatario admisible el aviso MUST descartarse sin envío ni fila/intento de auditoría. El destinatario es interno y MUST NOT exponerse como email de gestión en ninguna respuesta HTTP.

#### Scenario: Contacto cambia después de publicar
- **WHEN** el contacto cambia después de que confirma la cancelación y antes del envío
- **THEN** el aviso conserva el destinatario del snapshot de cancelación

#### Scenario: Snapshot de gestión ausente
- **WHEN** se cancela un RESERVADO futuro propio con turno.email_gestion null y paciente.email válido
- **THEN** la cancelación confirma 204 y publica una vez con pacienteEmail null; C-08 no intenta SMTP ni crea fila/intento de auditoría, sin fallback al contacto actual

#### Scenario: Snapshot de gestión no admisible
- **WHEN** se cancela un RESERVADO futuro propio con turno.email_gestion no admisible para BuzonCorreo (por ejemplo múltiples buzones o más de 254 caracteres), aunque paciente.email sea válido
- **THEN** la cancelación confirma 204 y publica una vez con pacienteEmail null; C-08 no intenta SMTP ni registrar aviso, no normaliza ni cambia destinatario y no responde RN-SE-04

### Requirement: Aviso de cancelación condicionado al commit
El evento MUST entregarse al consumidor posterior al commit solo si confirma la transición y el turno ya es visible como CANCELADO desde otra conexión. Rollback después de publicar MUST NOT entregar aviso ni crear fila. Con destinatario admisible, cupo y transporte disponibles, MUST originar un aviso con asunto «Cancelamos tu turno», al email de gestión capturado al reservar/heredado al reprogramar y sin link/token.

#### Scenario: Commit con SMTP detenido
- **WHEN** confirma la cancelación mientras el doble de transporte está bloqueado
- **THEN** el POST completa 204 sin esperar SMTP y otra conexión observa CANCELADO antes del intento de envío

#### Scenario: Rollback posterior a publicación
- **WHEN** se publica dentro de una transacción que luego se revierte realmente
- **THEN** el consumidor posterior al commit no recibe el evento, no intenta SMTP ni registra notificación

### Requirement: Fallas y límites del aviso de cancelación
Fallas SMTP MUST NOT revertir CANCELADO ni convertir el 204 en error; el registro separado MUST mantener el contrato C-08 (ERROR si aviso admitido con destinatario válido y base disponible). Este contrato MUST NOT interpretarse como entrega durable o exactly-once SMTP; conserva límites de caída de proceso, rechazo del executor y aceptación SMTP incierta de C-08.

#### Scenario: SMTP fallido
- **WHEN** un aviso de cancelación admitido con email válido falla en SMTP y la base está disponible
- **THEN** se conserva CANCELADO/204 y se registra CANCELACION en ERROR/SMTP_FALLO, sin reintento durable
