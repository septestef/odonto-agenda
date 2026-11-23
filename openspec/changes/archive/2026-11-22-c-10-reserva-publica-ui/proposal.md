# Proposal

## Why

La API de reserva pública de C-07 ya permite consultar disponibilidad y crear turnos, pero el paciente todavía no tiene una pantalla para hacerlo: hoy solo existe la API. Sin esta UI no se puede mostrar en la demo el flujo central del producto (elegir prestación, ver horarios libres y reservar con DNI sin cuenta), que es el que justifica OdontoAgenda.

## What Changes

- Nueva página pública `/reservar/{slug}` que carga el perfil del odontólogo, permite elegir prestación, fecha y horario libre, y completa los datos del paciente.
- Flujo en una sola columna, pensado primero para celular de 360 px: 4 pasos (prestación → día → horario → tus datos) y la pantalla de éxito.
- Validación en cliente de los mismos campos que exige la API (DNI de 7 u 8 dígitos, teléfono E.164, email, nombre y apellido obligatorios) con mensajes en español rioplatense, sin reemplazar la validación del servidor.
- Manejo explícito de los errores del backend: 409 `RN-TU-01` con refresco de la disponibilidad, 409 `RN-PA-03` por el límite de dos turnos futuros, 422 `RN-TU-03`/`RN-TU-11`/`RN-AG-05` por ventana y agenda, 400 `VALIDACION` con detalle por campo y 404 `NO_ENCONTRADO` por slug o prestación inexistente.
- Pantalla de éxito que confirma fecha, hora, prestación, consultorio y el email al que se enviará el link de gestión (el token nunca se muestra en la UI).
- Componente reutilizable de selección de horarios, que después reutilizan C-11 (reprogramación) y C-15 (turno manual).
- Tests Vitest de validadores, armado de la solicitud y transiciones del estado del flujo.
- Gate de diseño: mockup HTML aprobado en `mockups/` (selección, formulario, éxito y conflicto, a 390×844 y 1440×900) antes del apply.

## Capabilities

### New Capabilities

- `reserva-publica-ui`: comportamiento de la pantalla pública de reserva del paciente (carga del perfil, selección de prestación y horario, validación de datos, confirmación, éxito y manejo de errores de reserva).

### Modified Capabilities

Ninguna. C-10 consume los contratos ya definidos en `reserva-online`, `disponibilidad-turnos`, `errores-api` y `entrega-frontend`; no cambia su comportamiento.

## Impact

- **Código**: `frontend/src/features/reservar/` (página, estado del flujo, validadores y cliente de la API pública), componentes compartidos de selección de horarios y ruta `/reservar/{slug}` en el router.
- **Dependencias**: C-04 (shell, router, `api-client` y componentes base) y C-07 (endpoints públicos) ya están mergeados y archivados; los mockups quedaron aprobados en el PR #12 (2026-11-22).
- **API**: consume `GET /api/public/{slug}`, `GET /api/public/{slug}/disponibilidad` y `POST /api/public/{slug}/turnos`; no agrega ni modifica endpoints.
- **Diseño**: usa `design-system/tokens.css`, `brand.js`, `assets/logo.svg` y los patrones de `components.html` (opciones, tira de días, chips de horario, alertas, resumen y estado vacío). Sin colores ni medidas hardcodeadas.
- **Datos**: no toca el modelo ni las migraciones. No expone tokens ni datos de otros pacientes.
