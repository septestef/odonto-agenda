# Delta: Notificaciones por email

## Purpose

Entregar al paciente los avisos de turno por email después de una operación confirmada, sin acoplar la reserva al correo ni exponer el link de gestión fuera del mensaje.

## ADDED Requirements

> Borrador ALTO: los escenarios marcados como propuesta se incluyen para la revisión de Estefi, no como decisiones aprobadas. Ver las condiciones de aprobación en `design.md`.

### Requirement: Procesamiento de avisos solo después de un commit

El consumidor MUST procesar `TurnoReservado`, `TurnoCancelado` y `TurnoReprogramado` solo después de que confirme la transacción donde se publicó el evento y el estado resultante sea visible desde otra conexión. MUST NOT procesar eventos publicados sin transacción ni eventos de transacciones revertidas. El transporte MUST ejecutarse fuera del hilo del request y de toda transacción de base; su resultado MUST NOT modificar el turno ni el contrato HTTP de C-07 (DD-09, RN-NO-04).

#### Scenario: Commit real y reserva sin espera por el correo
- **WHEN** se hace una reserva válida con el transporte de correo bloqueado por un doble de prueba
- **THEN** la API responde 201 con el mismo cuerpo de C-07 y sin token, sin esperar a que se desbloquee el doble; al iniciar el transporte el turno ya es visible como `RESERVADO` desde otra conexión

#### Scenario: Publicación válida seguida de rollback
- **WHEN** se escribe un turno válido y se publica el evento dentro de la transacción, pero se fuerza su rollback después de publicar
- **THEN** no se envía un email ni se agrega una fila de notificación

#### Scenario: Publicación sin transacción
- **WHEN** se publica cualquiera de los eventos sin una transacción activa
- **THEN** no se envía un email ni se agrega una fila de notificación

#### Scenario: Reserva rechazada antes de publicar
- **WHEN** la reserva se rechaza con 409 `RN-TU-01` o `RN-PA-03`
- **THEN** el consumidor no envía un email ni agrega una fila para esa solicitud

#### Scenario: Dos reservas concurrentes del mismo horario
- **WHEN** dos solicitudes válidas de pacientes distintos compiten por el mismo horario del consultorio
- **THEN** exactamente una responde 201 y la otra obtiene el 409 `RN-TU-01` de C-07; solo el turno ganador origina un aviso

### Requirement: Confirmación con datos de la reserva y link de gestión

El consumidor MUST enviar al email capturado en `TurnoReservado` una confirmación HTML con fecha y hora local del turno, prestación, dirección del consultorio cuando esté informada y un link `{APP_BASE_URL}/turno/{token}` armado con el token de ese evento. MUST NOT consultar el contacto actual para armar o redirigir el mensaje (US-007, RN-NO-01, RN-SE-02).

#### Scenario: El contacto cambia antes del envío
- **WHEN** se procesa una reserva con inicio `2026-11-27T12:00:00Z`, zona `America/Argentina/Buenos_Aires`, dirección informada y una URL base configurada, y el contacto en la base ya cambió
- **THEN** se envía un solo email HTML al destinatario de la reserva, con fecha local del 27/11 a las 09:00, prestación, dirección y el link armado con la base configurada y el token de esa reserva

### Requirement: Avisos de cancelación y reprogramación a partir de eventos existentes

Ante un `TurnoCancelado` confirmado el consumidor MUST renderizar un aviso de cancelación del turno indicado, sin link de gestión. Ante un `TurnoReprogramado` confirmado el consumidor MUST renderizar un aviso de reprogramación con el horario nuevo y solo el link del token nuevo del evento. MUST usar el contacto provisto en el evento y MUST NOT buscar un contacto alternativo. Este requisito se limita al consumidor; no define cómo los emisores futuros obtienen el contacto ni cuándo publican en sus flujos (RN-NO-02).

#### Scenario: Evento de prueba de cancelación
- **WHEN** confirma una transacción de prueba que publica `TurnoCancelado` con un email en el snapshot `turno`
- **THEN** se envía un aviso a ese email con el turno cancelado en fecha y hora local, sin token ni link de gestión

#### Scenario: Evento de prueba de reprogramación (propuesta para revisión)
- **WHEN** confirma una transacción de prueba que publica `TurnoReprogramado` con emails distintos en `anterior` y `nuevo`
- **THEN** se envía un único aviso al email de `nuevo`, no al de `anterior`, con el nuevo horario local y solo el link con el nuevo token; el registro se asocia con el turno nuevo

### Requirement: Los mensajes se arman con contenido y destinos seguros

El consumidor MUST escapar el contenido dinámico en su contexto HTML, usar la zona horaria de negocio y construir los links únicamente con la base configurada y el token del evento. Si no puede construir un link válido para los avisos que lo requieren, MUST NOT enviar un mensaje con un link inválido. MUST NOT enviar a un email ausente, inválido o a un destinatario sustituto.

#### Scenario: Datos del snapshot con caracteres especiales
- **WHEN** la prestación o la dirección contiene metacaracteres HTML y el instante UTC cae en otra fecha local, en cualquiera de las tres plantillas
- **THEN** los valores se muestran como texto y no como etiquetas o atributos, se muestra la fecha y hora del negocio, y el único link de gestión, si corresponde, usa la base configurada

#### Scenario: URL base no utilizable y destinatario válido
- **WHEN** la URL base no permite armar un link absoluto HTTP(S) para confirmación o reprogramación
- **THEN** no se contacta al transporte, se registra un error sanitizado y el turno confirmado no cambia

#### Scenario: Datos faltantes (propuesta para revisión)
- **WHEN** un evento de prueba trae email válido pero no dirección del consultorio, o trae un email ausente o inválido
- **THEN** para el primer caso se omite la línea de dirección y se envía el aviso; para el segundo no hay envío ni fila con dirección inventada, solo diagnóstico sanitizado sin valores personales

### Requirement: Registro independiente del intento y confidencialidad

El consumidor MUST registrar por separado del turno el resultado del procesamiento para un destinatario válido: `ENVIADO` si el transporte acepta el mensaje y `ERROR` si falla el intento (RN-NO-04). La fila MUST identificar el turno, tipo, destinatario, instante UTC y estado, con error sanitizado cuando corresponda. El destinatario MUST ser el único dato personal directo de la fila. El token en claro, el link completo y el cuerpo MUST NOT persistirse ni loguearse; los logs MUST NOT incluir datos personales, secretos SMTP, representaciones de los eventos ni excepciones crudas del transporte o la escritura.

#### Scenario: Transporte aceptado
- **WHEN** el transporte acepta el aviso
- **THEN** se guarda `ENVIADO`, sin error, asociado al turno y tipo correctos; el instante se toma del reloj del servidor, no acredita entrega a la bandeja

#### Scenario: SMTP falla y la reserva sigue vigente
- **WHEN** falla SMTP para una reserva ya confirmada y el error del proveedor contiene datos personales o el link
- **THEN** el turno sigue `RESERVADO`, el 201 no cambia y se guarda `ERROR` con una categoría segura; no se filtran esos datos en logs ni en el error guardado

#### Scenario: SMTP sin configurar en ejecución normal (propuesta para revisión)
- **WHEN** se confirma una reserva con destinatario válido y la configuración local del transporte está incompleta o es inválida
- **THEN** la API conserva el 201, no hay conexión SMTP y se registra `ERROR` con una categoría de configuración sin datos sensibles

#### Scenario: Hora del intento fallido (propuesta para revisión)
- **WHEN** el intento para un destinatario válido empieza con el reloj del servidor en un instante conocido y falla antes de ser aceptado por el transporte
- **THEN** la fila `ERROR` guarda ese instante UTC en `enviado_en`, sin que el campo indique que salió un correo

#### Scenario: Cola de envío llena (propuesta para revisión)
- **WHEN** se rechaza la tarea de un evento ya confirmado con destinatario válido por saturación
- **THEN** no se intenta SMTP ni se escribe en base desde el hilo del request; se intenta auditar `ERROR` por un canal independiente, y si no se puede auditar solo queda un diagnóstico sanitizado

#### Scenario: SMTP acepta pero falla el registro (propuesta para revisión)
- **WHEN** el transporte acepta el correo pero falla la escritura del resultado
- **THEN** el turno sigue confirmado, se emite un diagnóstico sanitizado y no se reenvía automáticamente ni se informa falsamente que hay una fila guardada
