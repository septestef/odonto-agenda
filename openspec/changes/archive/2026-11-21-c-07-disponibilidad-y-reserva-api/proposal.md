# Proposal

## Por qué este es el change que presentamos

> Agregado el 2026-11-25 para la entrega del Trabajo Global; el resto del proposal queda como se aprobó el 2026-11-21.

Elegimos C-07 porque es el núcleo del MVP imprescindible que recomendó el Discovery (sección D.3: prevención de solapamiento y reserva online con DNI) y coincide con el ejemplo de la consigna: crear un turno sin solapamientos. Tiene reglas de negocio verificables (RN-TU-01, RN-TU-03, RN-PA-03) con escenarios felices, de error y de borde, incluidos los turnos contiguos y dos reservas simultáneas del mismo horario, donde exactamente una gana. Además, es una sola funcionalidad (la API de disponibilidad y reserva), no un módulo.

## Why

El paciente todavía no puede ver horarios libres ni reservar: C-02 dejó el esquema y la garantía anti-solapamiento en la base, pero nada la expone por HTTP. C-07 es el núcleo del camino crítico (`C-01 → C-02 → C-07 → C-08 → C-09 → C-11 → C-16`) y la demo es el miércoles 2026-11-25. Además, C-08 (emails), C-09 (gestión por token), C-10 (UI de reserva) y C-15 (turno manual) dependen de los contratos que define este change: endpoints, códigos de error y eventos de dominio.

## What Changes

- **Cálculo de disponibilidad** como función pura con el `Clock` inyectado: inicios cada 15 minutos desde el comienzo de cada franja del día, turno completo dentro de la franja, `inicio >= ahora + 24 h` (RN-TU-03), fecha local `<= hoy + app.reserva-max-dias` (60, RN-TU-11), sin cruce con bloqueos ni con turnos activos (RN-AG-05). Todo en la zona del negocio (`America/Argentina/Buenos_Aires`, RN-AG-06).
- **`GET /api/public/{slug}`**: perfil público del odontólogo, prestaciones activas y la ventana de fechas reservables calculada con el reloj del servidor (RN-GL-02).
- **`GET /api/public/{slug}/disponibilidad?prestacionId=&fecha=`**: lista de horarios libres en hora local; nunca expone turnos ni datos de pacientes (RN-SE-03).
- **`POST /api/public/{slug}/turnos`**: validación de entrada con Bean Validation (RN-PA-04), revalidación del inicio contra las reglas de agenda, upsert atómico del paciente por (`odontologo_id`, `dni`) (RN-PA-02), límite de 2 turnos futuros `RESERVADO` por DNI (RN-PA-03), token de gestión de 32 bytes `SecureRandom` en base64url con guardado solo del SHA-256 (RN-SE-02), `INSERT` del turno `RESERVADO`/`ONLINE` (RN-TU-04). Responde **201 sin token**.
- **Traducción de conflictos de la base**: la violación `23P01` de `turno_sin_solapamiento` y, de forma defensiva, `40P01` / `CannotAcquireLockException` se responden como **409 `RN-TU-01`** "Ese horario acaba de ser tomado, elegí otro" (RN-GL-01), desde el único `ManejadorErrores`.
- **Eventos de dominio** `TurnoReservado`, `TurnoCancelado`, `TurnoReprogramado` como `record` con contrato de payload fijo. C-07 publica solo `TurnoReservado` (dentro de la transacción, para que C-08 lo consuma `AFTER_COMMIT`); los otros dos quedan definidos para C-09. El token en claro viaja solo en memoria dentro de `TurnoReservado`.
- **Sin migraciones**: el esquema de C-02 alcanza. Si en el apply apareciera una necesidad, se usa `V7__` (convención de C-02).
- **Sin Spring Security**: C-07 no agrega `spring-boot-starter-security` (es de C-03). Se deja la recomendación de `permitAll` y exención de CSRF para `/api/public/**`.
- **Tests**: unitarios de disponibilidad con reloj fijo (bordes de franja, 24 h, 60 días, bloqueos, turnos, pasos); integración contra PostgreSQL real con el **test obligatorio de dos reservas concurrentes del mismo horario por HTTP: exactamente un 201 y un 409 `RN-TU-01`**, repetido 5 veces; límite de 2 turnos (también concurrente), DNI repetido que actualiza el contacto, validaciones de formato, 404 de slug/prestación y evento publicado solo tras el commit.

## Capabilities

### New Capabilities
- `disponibilidad-turnos`: cálculo de horarios libres de un odontólogo para una prestación y una fecha, y su exposición pública sin datos de pacientes.
- `reserva-online`: perfil público del odontólogo y reserva del paciente sin cuenta (validación, upsert de paciente, límite por DNI, token de gestión, respuesta sin token, conflicto concurrente).
- `eventos-turno`: contrato de los eventos de dominio de turnos (`TurnoReservado`, `TurnoCancelado`, `TurnoReprogramado`) y su publicación transaccional.

### Modified Capabilities
- `errores-api`: se agrega el requisito de traducir los conflictos de concurrencia de la base sobre turnos (`23P01` de `turno_sin_solapamiento`, `40P01`, falla de lock) a 409 `RN-TU-01` sin filtrar detalles internos.

## Impact

- **Código nuevo** (backend): `agenda` (cálculo y servicio de disponibilidad), `publico` (controlador y DTOs `record`), `turno` (servicio de reserva, token, eventos), `paciente` (upsert). Métodos de consulta nuevos en `OdontologoRepository`, `PrestacionRepository`, `HorarioAtencionRepository`, `BloqueoRepository`, `TurnoRepository` y `PacienteRepository` (este último con un upsert nativo `INSERT ... ON CONFLICT`).
- **Código modificado**: `shared/error/ManejadorErrores` (handler de conflictos de concurrencia), `CodigosError` sin cambios (los códigos `RN-*` viven con cada regla).
- **API pública nueva**: los tres endpoints bajo `/api/public/{slug}`. Contrato JSON publicado en `design.md` para C-10.
- **Contratos para otros changes**: eventos para C-08/C-09, recomendación de seguridad para C-03 y C-10, reuso de la disponibilidad para C-09 (reprogramar) y C-15 (manual, sin regla de 24 h).
- **Dependencias**: ninguna nueva. **Base**: sin migraciones.
- **Riesgo de merge**: C-05 (agenda) puede tocar los mismos repositorios de `agenda`/`prestacion`; los métodos se agregan al final de cada interfaz.
