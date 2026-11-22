# Diseño: C-08 — Notificaciones por email

## Context

Ver [propuesta](proposal.md) y [delta](specs/notificaciones-email/spec.md). En C-07, `ReservaService` hace `saveAndFlush`, arma el `ResumenTurno` inmutable y publica `TurnoReservado(turno, origen, token)` dentro de la transacción de reserva. `ReservaEventosIT` ya comprueba visibilidad desde otra conexión. El contrato durable `eventos-turno` solo normatiza la reserva. D9 del C-07 archivado y el código documentan `TurnoCancelado(turno, canceladoPor, canceladoEn)` y `TurnoReprogramado(anterior, nuevo, token)`; existen los records, no los emisores.

El backend ya tiene `AppProperties.baseUrl()` enlazado a `APP_BASE_URL`, `ZonaNegocio`, `Clock`, `Notificacion` y `NotificacionRepository`. V2 exige `destinatario` y `enviado_en` no nulos, además de la FK al turno. El build usa Java 25 y Spring Boot 4.1.2; no declara todavía el starter de Mail.

## Goals / Non-Goals

- **Objetivos:** separar publicación, envío y escritura; usar solo datos inmutables del evento para el mensaje, con privilegio y persistencia mínimos; poder probar todos los efectos sin Gmail ni zonas horarias de la máquina.
- **No objetivos:** alterar productores, DTOs/HTTP ni reglas de reserva o solapamiento. No crear UI, endpoints, mecanismos de recuperación ni nuevos contratos de publicación. Los fixtures de cancelación y reprogramación no representan un flujo productivo implementado.

## Decisions

**Governance ALTO — Estefi aprobó P1–P6 el 2026-11-21.** La autorización separada de Apply está registrada en `tasks.md`.

### D1 — Separar commit, despacho y escritura

`NotificacionService` recibe los tres eventos con `@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = false)`. El callback solo deriva el trabajo a un ejecutor dedicado en memoria: no llama a SMTP ni a la base, no espera cupo, no carga entidades ni deja que el rechazo se propague al request. El único ejecutor tiene dos workers y una cola de 32, con rechazo explícito, sin `CallerRunsPolicy` ni cola ilimitada. Con beans separados e inyección por constructor, el worker no transaccional renderiza y llama al transporte; el registrador invocado a través del proxy abre su propia transacción `REQUIRES_NEW` y usa una referencia al turno por id para guardar la fila. SMTP corre fuera de toda transacción de base; la transacción de registro empieza después de determinar el resultado del intento. Un ejecutor explícito permite manejar el rechazo en el punto de entrega; un método que se autoinvoca con `@Async` o `@Transactional` no garantiza esos límites.

**P1 — Rechazo de tarea.** Si el ejecutor rechaza una tarea, emití solo un log sanitizado de categoría cerrada, sin bloquear el request ni reintentar. No se ejecuta el aviso, no se intenta SMTP y no se invoca el registrador ni se intenta escribir en base.

### D2 — Usar snapshots, escapar y acotar el link

Renderizar plantillas HTML UTF-8 separadas bajo `resources/templates/mail/` (`confirmacion.html`, `cancelacion.html`, `reprogramacion.html`), con texto rioplatense, conversión en el borde por `ZonaNegocio` y escape por contexto HTML. No interpolar datos sin escape ni derivar URLs del request. Para confirmación y reprogramación, validar `AppProperties.baseUrl()` como base absoluta HTTP(S), con host y sin credenciales, query ni fragmento; normalizar la barra final y agregar `/turno/` y el token como segmento seguro. Una base inválida impide enviar correos que requieran link. Cancelación no construye ni necesita link. Validar un solo destinatario; no admitir listas, encabezados inyectados ni copia a terceros.

**P2 — Contacto del snapshot.** Confirmación usa `turno.pacienteEmail` del evento real, cancelación usa el snapshot `turno` y reprogramación genera un solo aviso con `nuevo.pacienteEmail`, horario y datos de `nuevo`, link del token nuevo y FK de `nuevo`. No se usa el contacto de `anterior` ni se relee el paciente. Este change no define qué contacto deben capturar los futuros emisores.

**P3 — Copy y ausencias.** Usar `MAIL_USERNAME` como dirección de remitente, `MAIL_FROM_NAME` como nombre visible, y asuntos fijos sin datos personales: «Confirmaste tu turno», «Cancelamos tu turno», «Reprogramamos tu turno». Si falta la dirección física, omitir esa línea. Si el email falta, es vacío o inválido, no se envía ni se escribe una fila: solo queda un diagnóstico sanitizado. No se cambia la validación HTTP de C-07 ni se flexibiliza el esquema.

### D3 — Transporte SMTP y operación de demo

Agregar el starter de Spring Mail y usar `JavaMailSender` para Gmail por `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` y `MAIL_FROM_NAME`; referencia de demo `smtp.gmail.com:587`, autenticación con contraseña de aplicación y STARTTLS requerido con verificación del servidor. Proponer tiempos finitos de conexión, lectura y escritura (5 s cada uno para empezar). El reemplazo explícito de perfil de test construye/captura mensajes y simula errores sin abrir conexiones, aun con credenciales presentes en el entorno. Mantener el debug SMTP y los logs con parámetros sensibles desactivados. No comprobar la conexión SMTP al atender una reserva.

**P4 — Configuración incompleta y demo.** En `demo`, si falta o es inválida cualquiera de `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` o `MAIL_FROM_NAME`, la app no arranca (DD-06). El mensaje de arranque identifica la variable, nunca su valor. Esta validación de configuración es local y no certifica que Gmail acepte las credenciales. En los demás perfiles, como `dev` y `test`, la API queda disponible: ante esa configuración se registra `ERROR`/`CONFIG_SMTP` para cada aviso que entra al procesamiento con destinatario válido, sin salida de red y sin intentar auditar tareas rechazadas según P1. La demo con correo real requiere una comprobación operativa separada y autorizada con datos sintéticos: credenciales vigentes, envío aceptado y `APP_BASE_URL` alcanzable desde el dispositivo receptor. `localhost` solo sirve en la máquina anfitriona; usá una base HTTPS alcanzable si el receptor está en otro equipo. La comprobación no implica que C-08 implemente la pantalla ni valide remotamente la URL.

### D4 — Registro mínimo y fallas parciales

**P5 — Semántica de auditoría y datos personales.** Reutilizar el esquema sin migración: guardar turno, tipo, `destinatario` como único dato personal directo, `estado`, `enviado_en` en UTC obtenido del `Clock` inyectado al iniciar el procesamiento y `error` nulo en éxito o de lista cerrada en falla. En `ERROR`, `enviado_en NOT NULL` indica el instante del intento, no que el aviso salió por SMTP. Las únicas categorías persistidas son `CONFIG_SMTP`, `URL_INVALIDA`, `RENDER` y `SMTP_FALLO`. No guardar texto de excepción, cuerpo, token ni link. No loguear snapshots (su `toString` puede contener datos personales), objetos MIME, parámetros SQL, identificadores de turno, credenciales ni causas crudas. `ENVIADO` indica aceptación por el transporte, no llegada a la bandeja.

**P6 — Escritura fallida o resultado SMTP ambiguo.** Si SMTP acepta el correo pero falla la escritura, emití solo un diagnóstico sanitizado, sin afirmar que se guardó una fila ni intentar otra escritura. Si un timeout deja incierta la aceptación SMTP de un aviso admitido con destinatario válido, intentá guardar `ERROR`/`SMTP_FALLO` en una transacción independiente `REQUIRES_NEW`. Puede quedar una fila `ERROR` aunque Gmail haya aceptado el correo: `enviado_en` es el instante UTC del intento, tomado del `Clock` inyectado según P5, no una confirmación de entrega. En ninguno de estos casos se reenvía el aviso ni se reintenta. Una falla SMTP no ambigua conserva `ERROR`/`SMTP_FALLO`. El turno sigue intacto; no hay outbox, atomicidad entre SMTP y PostgreSQL ni garantía de entrega exactamente una vez frente a fallas del proceso.

## Risks / Trade-offs

- Caída entre commit y procesamiento → puede existir una reserva sin correo, como advierte D9 de C-07. El rechazo del ejecutor (P1) también puede dejar una reserva sin correo, y una escritura fallida (P6) puede dejar un correo sin fila. No hay outbox, reintentos durables ni reenvío.
- Cuota gratuita de Gmail (referencia DD-06: aprox. 500 envíos/día, dependiente de la cuenta y sujeto a cambios), spam o bloqueo → documentar el límite y separar aceptación SMTP de entrega; el turno no se revierte.
- Mensajes engañosos o fuga de token/PII → usar snapshots, datos dinámicos escapados, base controlada y categorías cerradas de error; el link sale solo en el correo correspondiente.

## Migration Plan

No se requiere migración. Con autorización separada de Apply, validar el esquema actual y la regresión de C-07 en PostgreSQL real, con perfil de correo falso. Para una demo con envío real, preparar `MAIL_*` y verificar la base alcanzable solo en una operación posterior y autorizada. Si se retira este módulo de la versión desplegada, las reservas, sus tokens hasheados y los registros ya escritos quedan intactos; retirar la versión no entrega correos pendientes.
