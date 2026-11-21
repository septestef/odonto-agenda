# Delta: Notificaciones por email

## Purpose

Entregar al paciente los avisos de turno por email después de una operación confirmada, sin acoplar la reserva al correo ni exponer el link de gestión fuera del mensaje.

## ADDED Requirements

### Requirement: Procesamiento de avisos solo después de un commit

El consumidor MUST procesar `TurnoReservado`, `TurnoCancelado` y `TurnoReprogramado` solo después de que confirme la transacción donde se publicó el evento y el estado resultante sea visible desde otra conexión. MUST NOT procesar eventos publicados sin transacción ni eventos de transacciones revertidas. El transporte MUST ejecutarse fuera del hilo del request y de toda transacción de base; su resultado MUST NOT modificar el turno ni el contrato HTTP de C-07 (DD-09, RN-NO-04).

El despacho MUST rechazar explícitamente las tareas que excedan el cupo, sin bloquear el request ni ejecutar el aviso en su hilo. Ante un rechazo, MUST emitir solo un log sanitizado de categoría cerrada, sin intentar SMTP ni escribir en base.

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
- **THEN** exactamente una responde 201 y la otra obtiene el 409 `RN-TU-01` de C-07; con cupo y transporte disponible, solo el turno ganador origina un aviso

### Requirement: Confirmación con datos de la reserva y link de gestión

El consumidor MUST enviar al email capturado en `TurnoReservado` una confirmación HTML con fecha y hora local del turno, prestación, dirección del consultorio cuando esté informada y un link `{APP_BASE_URL}/turno/{token}` armado con el token de ese evento. MUST NOT consultar el contacto actual para armar o redirigir el mensaje (US-007, RN-NO-01, RN-SE-02).

#### Scenario: El contacto cambia antes del envío
- **WHEN** se procesa una reserva con inicio `2026-11-27T12:00:00Z`, zona `America/Argentina/Buenos_Aires`, dirección informada y una URL base configurada, y el contacto en la base ya cambió
- **THEN** se envía un solo email HTML al destinatario de la reserva, con fecha local del 27/11 a las 09:00, prestación, dirección y el link armado con la base configurada y el token de esa reserva

### Requirement: Avisos de cancelación y reprogramación a partir de eventos existentes

Ante un `TurnoCancelado` confirmado el consumidor MUST renderizar un aviso de cancelación del turno indicado, sin link de gestión. Ante un `TurnoReprogramado` confirmado MUST generar un único aviso con el email y horario de `nuevo` y solo el link del token nuevo; la FK de la fila, si se registra, MUST apuntar al turno `nuevo`. MUST NOT notificar a `anterior` ni releer el contacto. Este requisito se limita al consumidor; no define cómo los emisores futuros obtienen el contacto ni cuándo publican en sus flujos (RN-NO-02).

#### Scenario: Evento de prueba de cancelación
- **WHEN** confirma una transacción de prueba que publica `TurnoCancelado` con un email en el snapshot `turno`
- **THEN** se envía un aviso a ese email con el turno cancelado en fecha y hora local, sin token ni link de gestión

#### Scenario: Evento de prueba de reprogramación
- **WHEN** confirma una transacción de prueba que publica `TurnoReprogramado` con emails distintos en `anterior` y `nuevo`
- **THEN** se envía un único aviso al email de `nuevo`, no al de `anterior`, con el nuevo horario local y solo el link con el nuevo token; el registro se asocia con el turno nuevo

### Requirement: Los mensajes se arman con contenido y destinos seguros

El consumidor MUST escapar el contenido dinámico en su contexto HTML, usar la zona horaria de negocio y construir los links únicamente con la base configurada y el token del evento. Si no puede construir un link válido para los avisos que lo requieren, MUST NOT enviar un mensaje con un link inválido. MUST NOT enviar a un email ausente, inválido o a un destinatario sustituto.

El remitente MUST usar `MAIL_USERNAME` como dirección y `MAIL_FROM_NAME` como nombre visible. El asunto MUST ser «Confirmaste tu turno», «Cancelamos tu turno» o «Reprogramamos tu turno», según el tipo de aviso. Si no hay dirección del consultorio, el aviso MUST omitir esa línea. Si falta el destinatario o es inválido, el consumidor MUST emitir solo un diagnóstico sanitizado, sin enviar ni intentar registrar una fila.

#### Scenario: Remitente y asunto de cada aviso
- **WHEN** se arma un aviso de confirmación, cancelación o reprogramación con configuración SMTP válida
- **THEN** usa la dirección de `MAIL_USERNAME`, el nombre visible de `MAIL_FROM_NAME` y el asunto fijo del tipo de aviso, sin datos del turno ni del paciente en el asunto

#### Scenario: Datos del snapshot con caracteres especiales
- **WHEN** la prestación o la dirección contiene metacaracteres HTML y el instante UTC cae en otra fecha local, en cualquiera de las tres plantillas
- **THEN** los valores se muestran como texto y no como etiquetas o atributos, se muestra la fecha y hora del negocio, y el único link de gestión, si corresponde, usa la base configurada

#### Scenario: URL base no utilizable y destinatario válido
- **WHEN** la URL base no permite armar un link absoluto HTTP(S) para confirmación o reprogramación
- **THEN** no se contacta al transporte, se registra `ERROR`/`URL_INVALIDA` y el turno confirmado no cambia

#### Scenario: Datos faltantes
- **WHEN** un evento de prueba trae email válido pero no dirección del consultorio, o trae un email ausente o inválido
- **THEN** para el primer caso se omite la línea de dirección y se envía el aviso; para el segundo no hay envío ni se intenta escribir una fila, solo queda un diagnóstico sanitizado sin valores personales

### Requirement: Configuración SMTP según el perfil

En `demo`, si falta o es inválida cualquiera de `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` o `MAIL_FROM_NAME`, la aplicación MUST fallar al arrancar (DD-06). El diagnóstico de arranque MUST identificar la variable y MUST NOT incluir su valor. En otros perfiles, como `dev` y `test`, la API MUST seguir disponible: con la misma configuración inválida o incompleta, cada aviso admitido al procesamiento con destinatario válido MUST quedar en `ERROR`/`CONFIG_SMTP`, sin conexión SMTP. Un rechazo de despacho no genera fila ni intento de escritura.

#### Scenario: Demo no arranca con configuración SMTP incompleta o inválida
- **WHEN** se inicia la app con el perfil `demo` y alguna de las variables SMTP requeridas falta o es inválida
- **THEN** la app no arranca y el diagnóstico identifica la variable sin mostrar su valor

#### Scenario: SMTP sin configurar fuera de demo
- **WHEN** en un perfil distinto de `demo` se confirma una reserva, se admite su aviso con destinatario válido y la configuración local SMTP está incompleta o es inválida
- **THEN** la API conserva el 201, no hay conexión SMTP y se registra `ERROR`/`CONFIG_SMTP` sin datos sensibles en el error

### Requirement: Registro independiente del intento y confidencialidad

El consumidor MUST registrar por separado del turno el resultado de un aviso admitido con destinatario válido: `ENVIADO` con error nulo si el transporte acepta el mensaje y `ERROR` si el intento falla de forma no ambigua (RN-NO-04). Las únicas categorías persistidas son `CONFIG_SMTP`, `URL_INVALIDA`, `RENDER` y `SMTP_FALLO`. La fila MUST identificar el turno, tipo, destinatario, instante UTC y estado. En `ERROR`, `enviado_en` MUST ser el instante de inicio del intento, tomado del `Clock` inyectado, no una confirmación de envío. El destinatario MUST ser el único dato personal directo de la fila. El texto de excepción, el token en claro, el link completo y el cuerpo MUST NOT persistirse ni loguearse; los logs MUST NOT incluir datos personales, secretos SMTP ni representaciones de los eventos.

Ante un timeout que deja incierta la aceptación SMTP, el consumidor MUST emitir solo un diagnóstico sanitizado, sin intentar escribir una fila. Si falla la escritura del resultado, MUST emitir solo un diagnóstico sanitizado, sin afirmar que se guardó una fila ni intentar otra escritura. En ambos casos el consumidor MUST NOT reenviar ni reintentar el aviso; el turno confirmado queda intacto.

#### Scenario: Transporte aceptado
- **WHEN** el transporte acepta el aviso
- **THEN** se guarda `ENVIADO`, sin error, asociado al turno y tipo correctos; el instante se toma del reloj del servidor, no acredita entrega a la bandeja

#### Scenario: SMTP falla y la reserva sigue vigente
- **WHEN** SMTP devuelve una falla no ambigua para una reserva ya confirmada y el error del proveedor contiene datos personales o el link
- **THEN** el turno sigue `RESERVADO`, el 201 no cambia y se guarda `ERROR`/`SMTP_FALLO`; no se filtran esos datos en logs ni en el error guardado

#### Scenario: Hora del intento fallido
- **WHEN** el intento para un destinatario válido empieza con el reloj inyectado en un instante conocido y falla de forma no ambigua
- **THEN** la fila `ERROR` guarda ese instante UTC en `enviado_en`, sin que el campo indique que salió un correo

#### Scenario: SMTP acepta pero falla el registro
- **WHEN** el transporte acepta el correo pero falla la escritura del resultado
- **THEN** el turno sigue confirmado, solo se emite un diagnóstico sanitizado y no hay reenvío, reintento ni confirmación de fila guardada

#### Scenario: Timeout SMTP con aceptación incierta
- **WHEN** SMTP devuelve un timeout y no se sabe si aceptó el aviso
- **THEN** el turno sigue confirmado y solo se emite un diagnóstico sanitizado, sin fila ni intento de escritura, reenvío o reintento
