# Diseño: C-08 — Notificaciones por email

## Context

Ver [propuesta](proposal.md) y [delta](specs/notificaciones-email/spec.md). En C-07, `ReservaService` hace `saveAndFlush`, arma el `ResumenTurno` inmutable y publica `TurnoReservado(turno, origen, token)` dentro de la transacción de reserva. `ReservaEventosIT` ya comprueba visibilidad desde otra conexión. El contrato durable `eventos-turno` solo normatiza la reserva. D9 del C-07 archivado y el código documentan `TurnoCancelado(turno, canceladoPor, canceladoEn)` y `TurnoReprogramado(anterior, nuevo, token)`; existen los records, no los emisores.

El backend ya tiene `AppProperties.baseUrl()` enlazado a `APP_BASE_URL`, `ZonaNegocio`, `Clock`, `Notificacion` y `NotificacionRepository`. V2 exige `destinatario` y `enviado_en` no nulos, además de la FK al turno. El build usa Java 25 y Spring Boot 4.1.2; no declara todavía el starter de Mail.

## Goals / Non-Goals

- **Objetivos:** separar publicación, envío y escritura; usar solo datos inmutables del evento para el mensaje, con privilegio y persistencia mínimos; poder probar todos los efectos sin Gmail ni zonas horarias de la máquina.
- **No objetivos:** alterar productores, DTOs/HTTP ni reglas de reserva o solapamiento. No crear UI, endpoints, mecanismos de recuperación ni nuevos contratos de publicación. Los fixtures de cancelación y reprogramación no representan un flujo productivo implementado.

## Decisions

**Borrador ALTO, sin aprobación de Estefi.** Las propuestas P1–P6 se incluyen para dar criterios de revisión y pruebas a este borrador; no se deben tratar como cerradas. Si cambia una de ellas, ajustá delta, diseño y tareas antes de Apply.

### D1 — Separar commit, despacho y escritura

`NotificacionService` recibe los tres eventos con `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = false)`. El callback solo deriva el trabajo a un ejecutor dedicado en memoria; no hace I/O, no espera cupo, no carga entidades ni permite que el rechazo salga al request. Propuesta de dimensionamiento inicial para la demo: dos workers con cola de 32, rechazo explícito, sin `CallerRunsPolicy` ni cola ilimitada. Con beans separados e inyección por constructor, el worker no transaccional renderiza y llama al transporte; el registrador invocado a través del proxy abre su propia transacción `REQUIRES_NEW` y usa una referencia al turno por id para guardar la fila. No hay SMTP antes, durante ni dentro de la transacción de registro. Un ejecutor explícito permite manejar el rechazo en el punto de entrega; un método que se autoinvoca con `@Async` o `@Transactional` no garantiza esos límites.

**P1 — Rechazo de cola.** Propuesta: si hay destinatario válido, intentar registrar `ERROR`/`COLA_SATURADA` por un segundo ejecutor exclusivo para auditoría (un worker y cola de 16 como punto de partida), con la misma transacción independiente y sin SMTP. Si también se rechaza ese despacho o falla la base, emitir solo un diagnóstico de categoría cerrada, sin bloquear ni reintentar. No se promete una fila cuando ambos recursos fallan. Alternativas: descartar sin intentar escribir o registrar en el callback; la primera reduce trazabilidad y la segunda acopla latencia de DB al request.

### D2 — Usar snapshots, escapar y acotar el link

Renderizar plantillas HTML UTF-8 separadas bajo `resources/templates/mail/` (`confirmacion.html`, `cancelacion.html`, `reprogramacion.html`), con texto rioplatense, conversión en el borde por `ZonaNegocio` y escape por contexto HTML. No interpolar datos sin escape ni derivar URLs del request. Para confirmación y reprogramación, validar `AppProperties.baseUrl()` como base absoluta HTTP(S), con host y sin credenciales, query ni fragmento; normalizar la barra final y agregar `/turno/` y el token como segmento seguro. Una base inválida impide enviar correos que requieran link. Cancelación no construye ni necesita link. Validar un solo destinatario; no admitir listas, encabezados inyectados ni copia a terceros.

**P2 — Contacto de los futuros eventos.** Confirmación usa `turno.pacienteEmail` del evento real. Como criterio provisional del consumidor: cancelación usa `turno`; reprogramación genera un solo aviso con `nuevo.pacienteEmail`, horario y datos de `nuevo`, link del token nuevo y FK de `nuevo`. No se combina con `anterior`, no se envía un segundo aviso y no se relee el paciente. Falta revisar qué contacto deben capturar los futuros emisores; este change no decide su política. Enviar a ambos o releer el contacto vigente podría entregar el token a una dirección no autorizada.

**P3 — Copy y ausencias.** Propuesta: usar `MAIL_USERNAME` como dirección de remitente, `MAIL_FROM_NAME` como nombre visible, y asuntos fijos sin datos personales: «Confirmaste tu turno», «Cancelamos tu turno», «Reprogramamos tu turno». Si falta la dirección física, omitir esa línea. Si el email falta, es vacío o inválido, omitir envío, emitir diagnóstico sanitizado y no inventar un `destinatario` para llenar la columna obligatoria. No se cambia la validación HTTP de C-07 ni se propone flexibilizar el esquema. Confirmar estos criterios y los asuntos en revisión.

### D3 — Transporte SMTP y operación de demo

Agregar el starter de Spring Mail y usar `JavaMailSender` para Gmail por `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` y `MAIL_FROM_NAME`; referencia de demo `smtp.gmail.com:587`, autenticación con contraseña de aplicación y STARTTLS requerido con verificación del servidor. Proponer tiempos finitos de conexión, lectura y escritura (5 s cada uno para empezar). El reemplazo explícito de perfil de test construye/captura mensajes y simula errores sin abrir conexiones, aun con credenciales presentes en el entorno. Mantener el debug SMTP y los logs con parámetros sensibles desactivados. No comprobar la conexión SMTP al atender una reserva.

**P4 — Configuración incompleta y demo.** Propuesta: en ejecución normal, ante SMTP faltante o inválido, mantener la API disponible y registrar `ERROR`/`CONFIG_SMTP` por aviso con destinatario válido, sin salida de red. La demo con correo real requiere una comprobación operativa separada y autorizada con datos sintéticos: credenciales vigentes, envío aceptado y `APP_BASE_URL` alcanzable desde el dispositivo receptor. `localhost` solo sirve en la máquina anfitriona; usar una base HTTPS alcanzable si el receptor está en otro equipo. La comprobación no implica que C-08 implemente la pantalla ni valide remotamente la URL. Revisar si el perfil `demo` debería fallar al arrancar en vez de degradar solo el correo.

### D4 — Registro mínimo y fallas parciales

**P5 — Semántica de auditoría y PII.** Propuesta: reutilizar el esquema tal cual; guardar el turno, tipo, `destinatario` como único dato personal directo, `estado`, `enviado_en` obtenido de `Clock` y `error` nulo en éxito o de lista cerrada en falla. Tomar el instante al iniciar el procesamiento (o al detectar el rechazo si no empezó); en `ERROR`, `enviado_en NOT NULL` indica cuándo se intentó procesar el aviso, nunca que salió por SMTP. Categorías candidatas: `CONFIG_SMTP`, `URL_INVALIDA`, `RENDER`, `SMTP_FALLO`, `COLA_SATURADA`. No guardar texto de excepción, cuerpo, token ni link. No loguear snapshots (su `toString` puede contener PII), objetos MIME, parámetros SQL, identificadores de turno, credenciales ni causas crudas. `ENVIADO` indica aceptación por el transporte, no llegada a la bandeja. Alternativa: migrar a un timestamp de intento separado o redefinir la retención de destinatario; no se incluye sin revisión.

**P6 — SMTP exitoso y DB fallida.** Propuesta: un diagnóstico sanitizado sin afirmar que se registró la fila; no reenviar automáticamente, porque el proveedor pudo aceptar el correo aun cuando no haya fila. Tampoco reintentar ante un timeout de SMTP de resultado ambiguo. Registrar `ERROR` cuando se pueda y dejar intacto el turno. No hay atomicidad entre SMTP y PostgreSQL, ni garantía de entrega exactamente una vez frente a fallas del proceso.

## Risks / Trade-offs

- Caída entre commit y procesamiento, cola en memoria saturada o caída antes de la escritura → puede existir una reserva sin correo o un correo sin fila. Limitación aceptada para la demo según D9 de C-07; sin outbox, reintentos durables ni reenvío.
- Cuota gratuita de Gmail (referencia DD-06: aprox. 500 envíos/día, dependiente de la cuenta y sujeto a cambios), spam o bloqueo → documentar el límite y separar aceptación SMTP de entrega; el turno no se revierte.
- Mensajes engañosos o fuga de token/PII → usar snapshots, datos dinámicos escapados, base controlada y categorías cerradas de error; el link sale solo en el correo correspondiente.

## Migration Plan

No se propone migración. Después de aprobar el diseño, validar el esquema actual y la regresión de C-07 en PostgreSQL real, con perfil de correo falso. Para una demo con envío real, preparar `MAIL_*` y verificar la base alcanzable solo en una operación posterior y autorizada. Si se retira este módulo de la versión desplegada, las reservas, sus tokens hasheados y los registros ya escritos quedan intactos; retirar la versión no entrega correos pendientes.
