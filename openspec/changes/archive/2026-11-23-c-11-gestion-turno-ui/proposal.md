# Proposal

## Why

C-09 ya expone la gestión del turno por link (`GET`, `cancelar` y `reprogramar` con token), y el email de C-08 le manda al paciente el link `/turno/{token}`, pero esa ruta todavía muestra un marcador vacío. Sin esta pantalla el paciente no puede cancelar ni cambiar su turno, y la demo no muestra el cierre del flujo central (US-008 y US-009).

## What Changes

- Nueva página pública `/turno/{token}` que carga `GET /api/public/turnos/{token}` y muestra fecha, hora, prestación y consultorio del turno, sin datos del paciente ni el token.
- Acciones según `puedeCancelar` y `puedeReprogramar` del GET: "Cancelar turno" con confirmación previa (hoja inferior en celular, diálogo en escritorio) y "Cambiar horario".
- Reprogramación con la misma prestación: día y horario con la navegación por semana, "Ir a una fecha" y el selector de horarios de C-10, usando el perfil y la disponibilidad públicos de C-07; comparación "Antes / Nuevo" antes de confirmar.
- Después de cancelar o reprogramar (ambos 204 sin cuerpo), la página vuelve a pedir el turno con el mismo token y lo muestra en solo lectura. El link nuevo de la reprogramación llega solo por email: la página no lo recibe ni redirige.
- Vista de solo lectura para turnos cancelados, reprogramados o pasados.
- Mensajes propios por `codigo` para 404 `NO_ENCONTRADO`, 409 `RN-TU-07`, `RN-SE-02`, `RN-SE-04` y `RN-TU-01` (refresca la disponibilidad), 422 `RN-TU-03`, `RN-TU-11` y `RN-AG-05`, 400 `VALIDACION`/`SOLICITUD_INVALIDA`, y error de red o 500 con "Reintentar".
- La navegación de días de C-10 pasa a un módulo compartido para que la usen la reserva y la reprogramación sin duplicarla; la reserva pública no cambia de comportamiento.
- Tests Vitest del cliente de gestión, la máquina de estados y el render.
- Gate de diseño: mockups en `mockups/`, con la revisión 2 de `reprogramar.html` (alineada con C-10) pendiente de aprobación.

## Capabilities

### New Capabilities

- `gestion-turno-ui`: comportamiento de la pantalla del paciente en `/turno/{token}` (consulta del turno, cancelación con confirmación, reprogramación con la misma prestación, solo lectura y manejo de rechazos).

### Modified Capabilities

Ninguna. C-11 consume `gestion-turno-token`, `disponibilidad-turnos`, `reserva-online` y `errores-api` sin cambiar sus contratos. Extraer la navegación de días de C-10 es un refactor sin cambio de requisitos en `reserva-publica-ui`.

## Impact

- **Código**: `frontend/src/features/turno/` (cliente de gestión, modelo, estado y pantalla), `frontend/src/shared/navegador-dias.ts` (extraído de `features/reservar/pantalla-reserva.ts`), registro de la ruta `turno` en `main.ts` en lugar del marcador y estilos en `frontend/src/estilos/app.css`.
- **API**: consume `GET /api/public/turnos/{token}`, `POST /api/public/turnos/{token}/cancelar`, `POST /api/public/turnos/{token}/reprogramar`, `GET /api/public/{slug}` y `GET /api/public/{slug}/disponibilidad`. No agrega ni modifica endpoints.
- **Seguridad**: el token solo viaja en la ruta hacia la propia API; no se guarda, no se loguea, no se muestra ni se envía a terceros. Se conservan `no-store` y `no-referrer` que fija C-09.
- **Dependencias**: C-09 y C-10 archivados en `main`. Sin librerías nuevas.
- **Datos**: no toca el modelo ni las migraciones.
