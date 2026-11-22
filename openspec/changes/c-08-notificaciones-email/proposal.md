# Propuesta: C-08 — Notificaciones por email

## Why

Hoy la reserva online se confirma, pero el paciente no recibe el único link con el que podrá gestionar su turno sin cuenta (US-007). Para la demo necesitamos entregar ese link por email después de confirmar la reserva, sin que una demora o falla de Gmail cambie el resultado de la API.

## What Changes

- Consumir el `TurnoReservado` que ya emite C-07 y enviar la confirmación HTML con fecha y hora local, prestación, dirección si está informada y link de gestión. Preparar el consumo de los tipos existentes `TurnoCancelado` (sin link) y `TurnoReprogramado` (un aviso al email de `nuevo`, con su link nuevo y registro asociado a ese turno), verificados con eventos de prueba; no agregar emisores.
- Desacoplar el envío del request: solo después del commit, sin fallback, con un único ejecutor dedicado de dos workers y cola de 32, sin ejecución en el hilo llamador. Si el ejecutor rechaza una tarea, solo queda un diagnóstico sanitizado de categoría cerrada: no se intenta enviar ni escribir en base.
- Ejecutar SMTP fuera de toda transacción de base. Registrar `ENVIADO` o `ERROR` con la entidad y el repositorio existentes, en una escritura independiente, con categorías de error cerradas y el destinatario como único dato personal directo de la fila. Ante un timeout SMTP de aceptación incierta para un aviso admitido con destinatario válido, intentar guardar `ERROR`/`SMTP_FALLO`, sin reenvío ni reintento. Si SMTP acepta el correo pero falla la escritura, emitir solo un diagnóstico sanitizado, sin otra escritura, reenvío ni reintento.
- Usar Gmail SMTP vía `JavaMailSender` (DD-06), configuración `MAIL_*`, STARTTLS y tiempos de espera finitos. `demo` no arranca si falta o es inválida una variable SMTP requerida; el diagnóstico nombra la variable, no su valor. En `dev`/`test`, ante esa configuración la API sigue disponible y los avisos procesados con destinatario válido quedan en `ERROR`/`CONFIG_SMTP` sin red. El perfil de test usa un doble sin red. Documentar cuota, posible pérdida después del commit y ausencia de outbox y reintentos durables.
- **Fuera de alcance:** UI, mockups, endpoints, publicadores nuevos, cambios a C-07, ciclo de vida o reenvío de tokens, recordatorios (RN-NO-03), WhatsApp y cualquier implementación de C-09.

## Capabilities

### New Capabilities

- `notificaciones-email`: consumo posterior al commit, contenido y destinatario de los emails de turno, transporte y registro de resultados sin filtrar secretos o datos personales.

### Modified Capabilities

Ninguna. `eventos-turno` sigue siendo el contrato vigente para la publicación de la reserva. Los tipos de cancelación y reprogramación se conocen por el código y D9 del C-07 archivado; este delta no establece obligaciones para sus futuros emisores.

## Impact

- Depende de C-07, archivado el 2026-11-21. Afecta al módulo backend `notificacion`, las plantillas `resources/templates/mail/`, la configuración SMTP y los tests. Java 25, Spring Boot 4.1.2, Gradle y PostgreSQL real; no se propone una migración.
- Sin cambios incompatibles: mismo contrato HTTP de C-07, incluido el 201 sin token, los errores existentes y exactamente una reserva ganadora ante solapamiento. El token en claro solo sale por email; no se persisten ni se loguean token, link o cuerpo.
- **Governance ALTO — revisión aprobada por Estefi el 2026-11-21.** P1–P6 están cerradas en `design.md`. La autorización separada de Apply está registrada en `tasks.md`.
