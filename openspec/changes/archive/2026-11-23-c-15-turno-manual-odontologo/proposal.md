# Proposal — C-15 `turno-manual-odontologo`

## Why

Hoy el odontólogo solo puede ver y cancelar turnos desde su agenda (C-12/C-13), pero no puede cargar uno cuando un paciente lo llama por teléfono: tiene que pedirle que reserve online, con la regla de 24 h de RN-TU-03. US-011 pide poder completar la agenda desde un solo lugar. Este change entrega ese alta manual para la demo del 2026-11-25, reutilizando el formulario de paciente y el selector de horarios de C-10 y con el mockup aprobado por el usuario como contrato visual.

## What Changes

- Nuevo endpoint autenticado `POST /api/turnos` con CSRF: crea un turno `RESERVADO` de origen `MANUAL` a partir de `prestacionId`, `inicio` con offset y los datos del paciente. El `odontologo_id` sale siempre de la sesión (RN-SE-01).
- Reutilización mínima de `ReservaService`: comparte con la reserva online el upsert de paciente (RN-PA-01/02), la generación de token (RN-SE-02), la exclusion constraint (RN-TU-01) y la publicación de `TurnoReservado`, que envía el mismo email de confirmación con `/turno/{token}` (RN-NO-01). No aplican RN-TU-03 ni RN-PA-03 al alta manual; sí aplican RN-TU-08, RN-TU-11, RN-AG-05 y RN-TU-01. Los turnos manuales cuentan para el límite de 2 turnos futuros cuando ese DNI reserva online después.
- Nuevo endpoint autenticado `GET /api/disponibilidad?prestacionId=&fecha=` con la misma forma de respuesta que el público, pero sin la anticipación de 24 h: permite ver horarios de hoy o de mañana temprano. Respeta el horizonte de 60 días, bloqueos y turnos que ocupan lugar.
- Errores uniformes del `errores-api`: 409 `RN-TU-01`, 422 `RN-AG-05`, 422 `RN-TU-11`, 422 para inicio en el pasado (se documenta el código `RN-TU-12` en la KB) y 400 `VALIDACION` con detalle por campo; 401 sin sesión y 403 si falla CSRF.
- UI: botón “Nuevo turno” en la cabecera de la agenda, formulario en pasos (prestación → día → horario → datos del paciente), conflictos en el propio formulario con recarga de horarios, y éxito que vuelve a la agenda con el turno nuevo visible. Textos en español rioplatense con voseo, 360 px de ancho mínimo y tokens del design system.
- Tests JUnit contra PostgreSQL real `turnotarde_test` y Vitest para validaciones, transiciones del flujo y recarga de la agenda. Sin recorte de tests (DD-08).
- KB: cerrar PA-02 (los turnos manuales no tienen regla de 24 h), documentar el nuevo supuesto sobre RN-PA-03 y agregar RN-TU-12. `CHANGES.md` se marca recién después del merge, en el cierre.

## Capabilities

### New Capabilities

- `turno-manual-api`: alta autenticada de turnos manuales, reglas aplicables, paciente, token, email, concurrencia y errores.
- `turno-manual-ui`: flujo de “Nuevo turno” desde la agenda, selección de prestación/día/horario, formulario de paciente, conflictos y éxito.

### Modified Capabilities

- `disponibilidad-turnos`: nueva consulta autenticada sin anticipación de 24 h, con la misma forma de respuesta y el mismo horizonte.
- `reserva-online`: un turno manual previo cuenta para el límite de dos turnos futuros por DNI en una reserva online posterior.
- `errores-api`: código `RN-TU-12` (422) para inicio en el pasado al crear un turno manual.

## Impact

- **Backend**: `turno/` (controlador, DTOs, `ReservaService`, `ReglasTurno`, eventos), `agenda/` (`DisponibilidadService`), `publico/` (reutilización del DTO de disponibilidad) y `shared/error/`. Sin migraciones ni secretos nuevos.
- **Frontend**: `features/agenda/` (botón y manejador), nuevo módulo `features/turno-manual/` y reutilización de `shared/selector-horarios.ts`, `shared/navegador-dias.ts`, `shared/api-client.ts`, `shared/error-api.ts` y validaciones/formulario de `features/reservar/`. Se evita reordenar archivos de C-14.
- **APIs**: `POST /api/turnos` y `GET /api/disponibilidad` (ambas autenticadas, con CSRF la de escritura); el cuerpo de error sigue siendo `{codigo, mensaje, detalles?}`.
- **Diseño**: política `system-and-mockups`, revisión 2 aprobada. Mockup HTML en `mockups/` con capturas 390×844 y 1440×900; el apply queda bloqueado hasta la aprobación explícita del usuario.
- **Fuera de alcance**: WhatsApp y recordatorios (C-14), marcar atendido/ausente (C-19), historia clínica (C-17) y cambios de diseño system. No se tocan C-17..C-20.
