# Propuesta: C-07 — Disponibilidad y reserva pública

## Why

El paciente necesita consultar horarios y reservar sin llamar ni crear una cuenta. C-07 habilita esa parte de US-005 y US-006 en el backend sin permitir sobreturnos, aunque dos personas elijan el mismo horario a la vez.

## What Changes

- `GET /api/public/{slug}` devuelve el perfil público y las prestaciones activas; `GET /api/public/{slug}/disponibilidad?prestacionId=&fecha=` lista inicios libres para una fecha local.
- `POST /api/public/{slug}/turnos` valida los datos, actualiza o crea el paciente por odontólogo y DNI y reserva en una transacción. Respeta el máximo de dos turnos futuros `RESERVADO`, devuelve 201 sin token y reserva el 409 `RN-TU-01` para la violación de `turno_sin_solapamiento`.
- Se propone emitir únicamente `TurnoReservado` en C-07 y acordar el contrato de `TurnoCancelado` y `TurnoReprogramado` con sus futuros productores/consumidores. Fuera de alcance: listeners, SMTP, gestión por token, turnos manuales, UI y autenticación. C-07 no es [UI] y no requiere mockup.

## Capabilities

### New Capabilities

- `disponibilidad-y-reserva-publica`: perfil y disponibilidad públicos, reserva online, aislamiento y resultados de concurrencia.

### Modified Capabilities

Ninguna. Se reutilizan `errores-api`, `tiempo-servidor` y `configuracion-entorno` sin cambiar sus requisitos.

## Impact

- Backend: módulos `publico`, `agenda`, `turno`, `paciente`, `prestacion` y `odontologo`; C-10 será consumidor de la API y C-08/C-09 requieren coordinación de eventos.
- C-02 es el único prerrequisito directo y no está integrado en esta rama. C-07 consume su esquema, entidades y repositorios de negocio; no los suplanta. La integración y verificación del contrato de C-02 son condición para implementar.
- Gobernanza **ALTA**: propuesta exclusivamente para revisión humana. El contrato JSON/HTTP, los bordes temporales y de grilla, la estrategia de cupo y el alcance de eventos se detallan como propuestas pendientes de ratificación.
- El CLI rechazó `C-07-disponibilidad-y-reserva-api` por las mayúsculas; el identificador es `c-07-disponibilidad-y-reserva-api` y conserva el código de roadmap C-07.
