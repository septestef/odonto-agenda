# Spec Delta

## Purpose

Permitir al odontólogo consultar sus turnos diarios y semanales con datos del paciente y la prestación, sin revelar agendas ajenas.

P1/P2/P7/P9 aprobadas y apply autorizado explícitamente el 2026-11-22; primera etapa limitada a preparación/baseline según design.md.

## ADDED Requirements

### Requirement: Consulta privada de agenda
`GET /api/turnos?desde=&hasta=` MUST requerir sesión con rol ODONTOLOGO y limitar todos los resultados al dueño de esa sesión. MUST ignorar identificadores de odontólogo recibidos en query, headers o cuerpo como fuente de pertenencia. MUST responder 200 JSON con array, `[]` si no hay turnos. GET MUST NOT requerir CSRF. Sin sesión MUST responder 401 `NO_AUTENTICADO`; con sesión sin rol permitido MUST responder 403 `ACCESO_DENEGADO`, con formato uniforme y sin redirect.

#### Scenario: Dos agendas con los mismos horarios
- **WHEN** A consulta un rango que tiene turnos de A y B e incluye `odontologoId` de B en query y header
- **THEN** recibe solo turnos de A, con totales implícitos y datos exclusivamente propios

#### Scenario: Agenda vacía
- **WHEN** un odontólogo con sesión válida consulta un rango válido sin turnos propios
- **THEN** recibe 200 con `[]`

#### Scenario: Lectura protegida
- **WHEN** se consulta sin sesión o con sesión que no tiene rol ODONTOLOGO
- **THEN** se recibe respectivamente 401 `NO_AUTENTICADO` o 403 `ACCESO_DENEGADO`, sin datos de agenda

### Requirement: Rango local semiabierto por intersección
`desde` y `hasta` MUST ser fechas ISO `YYYY-MM-DD` obligatorias. El rango MUST interpretarse como `[desde a inicio de día, hasta a inicio de día)` en zona de negocio Buenos Aires, con entre 1 y 31 días calendario. MUST incluir todo turno que cumpla `inicio < hasta && fin > desde`, sin reglas de anticipación 24 h ni horizonte de reserva 60 días para lectura. MUST admitir históricos.

#### Scenario: Vista diaria y cruce de medianoche
- **WHEN** se consulta desde 2026-11-25 hasta 2026-11-26 y un turno inicia el 24 a las 23:45 local y termina el 25 a las 00:15 local
- **THEN** se incluye ese turno; se excluye uno que termina exactamente el 25 a las 00:00 y uno que comienza exactamente el 26 a las 00:00

#### Scenario: Semana y conversión UTC
- **WHEN** se consulta desde 2026-11-23 hasta 2026-11-30 en Buenos Aires
- **THEN** el intervalo efectivo es `[2026-11-23T03:00:00Z,2026-11-30T03:00:00Z)` independientemente de la zona del sistema

#### Scenario: Lectura fuera de ventana de reserva
- **WHEN** se consulta un rango histórico válido o un rango válido a más de 60 días del presente
- **THEN** se responde 200 sin aplicar reglas de reserva de 24 h ni de 60 días

### Requirement: Validación del rango de agenda
Ausencia, vacío o formato/conversión inválidos de `desde` o `hasta` MUST responder 400 `SOLICITUD_INVALIDA`, mensaje `No pudimos interpretar la solicitud.`, sin detalles. Orden inválido MUST responder 400 `VALIDACION`, mensaje `Hay datos inválidos. Revisalos y probá de nuevo.`, detalle `hasta` / `Elegí una fecha posterior a desde.`; longitud mayor a 31 MUST usar el mismo código/mensaje con detalle `hasta` / `Elegí un rango de hasta 31 días.`. MUST evaluar orden antes que longitud.

#### Scenario: Límites de longitud
- **WHEN** se consultan rangos de 1, 31, 0, negativos y 32 días calendario
- **THEN** 1 y 31 se aceptan, 0/negativos se rechazan por orden y 32 por longitud con los detalles definidos

#### Scenario: Entrada no interpretable
- **WHEN** falta alguna fecha o se envía vacía, con hora/offset o con fecha imposible
- **THEN** se responde 400 `SOLICITUD_INVALIDA` sin detalles

### Requirement: Forma de la respuesta de agenda
Cada elemento MUST contener exactamente `id`, `inicio`, `fin`, `estado`, `origen`, `paciente`, `prestacion`, `canceladoPor`, `canceladoEn` según esquema y ejemplo P2 de design.md. `paciente` MUST contener `id,dni,nombre,apellido,telefono,email`; email MUST ser el contacto mutable vigente de paciente, nunca el snapshot email_gestion. `prestacion` MUST contener `id,nombre,duracionMinutos`, con duración real entre inicio y fin.

#### Scenario: Duración de prestación editada
- **WHEN** un turno conserva 30 minutos entre inicio y fin pero su prestación vigente ahora dura 45
- **THEN** el DTO indica 30 minutos, el nombre vigente y el horario persistido; 12:00Z se muestra como 09:00-03:00 en Buenos Aires

### Requirement: Contenido y orden de la agenda
La agenda MUST incluir todos los estados y orígenes, incluso cancelados y prestaciones inactivas, ordenados por inicio ascendente y luego id ascendente. Fechas MUST ser ISO offset-date-time en zona de negocio; metadatos de cancelación MUST ser null cuando ausentes.

#### Scenario: Agenda con estados y orígenes mixtos
- **WHEN** hay turnos RESERVADO, CANCELADO, ATENDIDO y AUSENTE, de origen ONLINE y MANUAL, y dos comparten inicio
- **THEN** todos aparecen ordenados por inicio/id, con los campos contratados y sin secretos

### Requirement: Agenda sin secretos
La agenda MUST NOT exponer token, hash (`token_hash`/`tokenHash`), email de gestión (`email_gestion`/`emailGestion`), identificador de dueño, auditoría ni metadatos internos de persistencia, tampoco sus valores reservados en detalle anidado, listados o errores.

#### Scenario: Turno cancelado y contactos vigentes
- **WHEN** se lista un turno cancelado cuyo paciente cambió su email y difiere del email_gestion congelado del turno
- **THEN** se informa el contacto vigente y los metadatos originales de cancelación, sin token/hash ni email_gestion (claves o valores centinela distintos del contacto mutable)
