# Propuesta: C-09 - Gestión del turno por token

## Why

El paciente ya puede reservar y recibir el link, pero todavía no puede consultar, cancelar ni mover su turno sin llamar al consultorio (US-008 y US-009). C-09 completa la API que necesita C-11 para la demo, sin convertir el DNI ni el contacto editable en una credencial.

## What Changes

- Agregar `GET /api/public/turnos/{token}` y los `POST` del mismo recurso a `/cancelar` y `/reprogramar`. Resolver por SHA-256 del token; devolver 404 uniforme para token inválido o desconocido. La consulta devuelve un detalle mínimo; el link de un turno ya iniciado o no reservado sigue en solo lectura.
- Cancelar un turno `RESERVADO` antes de que empiece, incluso dentro de las 24 h previas. Reprogramar con `{inicio}` a otro horario disponible de la misma prestación, con al menos 24 h para el destino y un sucesor con token nuevo. Si falla el alta, queda intacto el original; PostgreSQL sigue garantizando el anti-solapamiento, incluyendo el 409 `RN-TU-01` por constraint, deadlock o falla de lock en la escritura.
- Emitir `TurnoCancelado` o `TurnoReprogramado` según la acción. Reusar C-08 para el aviso solo después del commit; el token nuevo en claro sale únicamente por email, nunca por respuestas, logs o persistencia.
- Proponer un email de gestión fijo por turno (V9), capturado al reservar y heredado al reprogramar, sin cambiar el 201, el upsert ni la validación pública de C-07. Para legados sin snapshot o snapshots no admitidos por C-08, proponer consulta y cancelación sin correo, y rechazo de reprogramación antes de escribir. No usar `paciente.email` ni auditoría de correo como fallback.
- Planificar Strict TDD con unitarios, PostgreSQL real y SMTP falso, incluyendo carreras por el original y el destino, rollback real y regresiones de C-07/C-08.
- **Fuera de alcance:** UI/mockups (C-11), cambios a reglas de sesión/CSRF, catálogo o configuración de agenda (C-05), recuperación o reenvío del token, outbox, recordatorios y WhatsApp.

## Capabilities

### New Capabilities

- `gestion-turno-token`: consulta, cancelación y reprogramación por link; reglas de solo lectura, confidencialidad y atomicidad.

### Modified Capabilities

- `eventos-turno`: agrega los contratos de los emisores de cancelación y reprogramación del paciente, compatibles con C-08.
- `modelo-datos`: incorpora un snapshot nullable de email de gestión en `turno`, separado del contacto editable y de la auditoría.
- `reserva-online`: la reserva persiste el email de esa solicitud con el turno, sin cambiar su respuesta, validación ni upsert.

## Impact

- Depende de C-07 y C-08 archivados. GATE 3 también menciona C-05, aún pendiente y no listado como dependencia directa de C-09: aclarar si bloquea Apply, sin cambiar el roadmap.
- Afecta `publico`, `turno`, persistencia y tests; reutiliza agenda, errores uniformes y el consumidor de C-08. V9 es una propuesta aditiva, sin backfill con fuentes no confiables. C-11 reutiliza los horarios de C-07 con los datos de selector de C-09.
- **Governance ALTO — revisión explícita de Estefi pendiente.** Los contratos y alternativas son propuestas; esta autorización es solo de planificación. No habilita Apply.
