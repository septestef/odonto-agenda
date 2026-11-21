# Propuesta: C-08 — Notificaciones por email

## Why

Hoy la reserva online se confirma, pero el paciente no recibe el único link con el que podrá gestionar su turno sin cuenta (US-007). Para la demo necesitamos entregar ese link por email después de confirmar la reserva, sin que una demora o falla de Gmail cambie el resultado de la API.

## What Changes

- Consumir el `TurnoReservado` que ya emite C-07 y enviar la confirmación HTML con fecha y hora local, prestación, dirección y link de gestión. Preparar el consumo de los tipos existentes `TurnoCancelado` (sin link) y `TurnoReprogramado` (link nuevo), verificados con eventos de prueba; no agregar emisores.
- Desacoplar el envío del request: solo después del commit, sin fallback, con ejecución asíncrona acotada y sin SMTP dentro de una transacción de base. Registrar `ENVIADO` o `ERROR` con la entidad y el repositorio existentes, en una escritura independiente y con errores sanitizados.
- Usar Gmail SMTP vía `JavaMailSender` (DD-06), configuración `MAIL_*`, STARTTLS y tiempos de espera finitos. El perfil de test usa un doble sin red. Documentar cuota, posible pérdida después del commit y ausencia de outbox/reintentos durables.
- **Fuera de alcance:** UI, mockups, endpoints, publicadores nuevos, cambios a C-07, ciclo de vida o reenvío de tokens, recordatorios (RN-NO-03), WhatsApp y cualquier implementación de C-09.

## Capabilities

### New Capabilities

- `notificaciones-email`: consumo posterior al commit, contenido y destinatario de los emails de turno, transporte y registro de resultados sin filtrar secretos o datos personales.

### Modified Capabilities

Ninguna. `eventos-turno` sigue siendo el contrato vigente para la publicación de la reserva. Los tipos de cancelación y reprogramación se conocen por el código y D9 del C-07 archivado; este delta no establece obligaciones para sus futuros emisores.

## Impact

- Depende de C-07, archivado el 2026-11-21. Afecta al módulo backend `notificacion`, las plantillas `resources/templates/mail/`, la configuración SMTP y los tests. Java 25, Spring Boot 4.1.2, Gradle y PostgreSQL real; no se propone una migración.
- Sin cambios incompatibles: mismo contrato HTTP de C-07, incluido el 201 sin token, los errores existentes y exactamente una reserva ganadora ante solapamiento. El token en claro solo sale por email; no se persisten ni se loguean token, link o cuerpo.
- **Governance ALTO — revisión de Estefi pendiente.** Estos artefactos son solo para revisión; no autorizan Apply. Siguen pendientes la semántica de contacto de los futuros emisores; `enviado_en` en `ERROR`, `destinatario` como único dato personal directo y la sanitización; SMTP mal configurado y criterio de demo; auditoría al rechazar la cola; SMTP exitoso con falla posterior de base y propuesta de no reenviar; remitente, asuntos, ausencia de dirección del consultorio o email y alcance real de `APP_BASE_URL`. Los valores y comportamientos de referencia para discutirlos están en `design.md`; no constituyen aprobación.
