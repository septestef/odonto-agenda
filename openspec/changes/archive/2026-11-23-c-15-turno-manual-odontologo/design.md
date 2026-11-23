# Design — C-15 `turno-manual-odontologo`

## Context

La motivación y el alcance están en `proposal.md`; los requisitos en los deltas de `specs/`. Estado verificado sobre `HEAD=origin/main=6e3f102` (2026-11-23):

- `ControladorAgenda` (`/api/turnos`) hoy solo expone `GET`; `ControladorCancelacionOdontologo` expone `POST /api/turnos/{id}/cancelar`. La cadena `ConfiguracionSeguridad` exige sesión `ROLE_ODONTOLOGO` para `/api/**` y CSRF SPA para escrituras, con excepción de `/api/public/**` y `/api/auth/login`. No hay que agregar seguridad nueva.
- `OdontologoActual.id()` resuelve el dueño desde el `SecurityContext`; `AislamientoOdontologo.exigir(Optional)` y `PrestacionRepository.findByIdAndOdontologoIdAndActivaTrue` cubren el 404 uniforme.
- `ReservaService.reservar` ya implementa en una sola transacción: validación de agenda → upsert de paciente con lock → RN-PA-03 → token + `saveAndFlush` dentro de `GuardiaSolapamiento` → `TurnoReservado` antes del commit. `OrigenTurno.ONLINE` está hardcodeado en dos líneas; la BD y `TurnoAgendaRespuesta` ya aceptan y exponen `MANUAL`.
- `CalculadoraDisponibilidad` recibe `anticipacionMinima` por parámetro, pero `DisponibilidadService` siempre pasa `ANTICIPACION_ONLINE = 24 h`. No existe aún una variante sin anticipación ni un código para inicio en el pasado.
- `NotificacionService.reservado` escucha `TurnoReservado` en `AFTER_COMMIT` y no filtra por origen: publicar el evento con origen `MANUAL` y token en claro alcanza para enviar el mismo correo de confirmación. El test `SmtpDePrueba` permite verificar el link sin SMTP real.
- Frontend: `features/agenda/control.ts` ya tiene `cabecera()` y el patrón cancelar → toast → `recargar(rango)`; los huecos libres hoy no son accionables. `shared/selector-horarios.ts`, `shared/navegador-dias.ts`, `shared/api-client.ts` (CSRF) y `features/reservar/validaciones.ts` son reutilizables. El diseño system-and-mockups está en revisión 2 aprobada.
- KB: `07_flujos_principales.md` llama “Flujo 6” al de WhatsApp y no tiene un flujo numerado de agenda; acá se sigue `US-011` de `06_funcionalidades.md` y el Flujo 3 como referencia de reserva. Es una discrepancia de numeración, no un bloqueo.

## Goals / Non-Goals

**Goals:**

- Exponer el alta manual con el mismo núcleo transaccional y la misma garantía de anti-solapamiento que la reserva online, sin duplicar lógica.
- Ofrecer al odontólogo horarios dentro de las próximas 24 h sin relajar la reserva pública.
- Dejar el flujo “Nuevo turno” con estados explícitos (carga, vacío, envío, conflicto, error, éxito) y verificable con Vitest.
- Cerrar PA-02 y documentar los supuestos nuevos en la KB sin tocar changes post-demo.

**Non-Goals:**

- Cambiar el modelo de datos, las migraciones o la configuración de correo.
- Modificar la reserva pública, la reprogramación ni el cálculo de franjas/bloqueos.
- Agregar WhatsApp, recordatorios, historia clínica ni marcar atendido/ausente.
- Reordenar o reformatear archivos de `features/agenda/` o `turno/` más allá de lo indispensable (C-14 corre en paralelo).

## Decisions

### D1 — Un solo núcleo de reserva con parámetros

`ReservaService` gana `reservarManual(Odontologo, Prestacion, Instant, DatosPaciente)` y delega en un método privado compartido que recibe `OrigenTurno` y un flag `aplicarLimitePaciente`. La reserva online sigue llamando al método público actual con `ONLINE` y límite activo. Se comparten upsert, token, `GuardiaSolapamiento`, `saveAndFlush` y evento; no se comparte la validación de anticipación ni el conteo RN-PA-03.

*Alternativa descartada*: un servicio `TurnoManualService` nuevo. Duplicaría el orden de locks (paciente → turno) y la traducción de conflictos, con riesgo de romper la garantía DD-03.

### D2 — Anticipación inyectada, no una segunda calculadora

`DisponibilidadService` gana `horariosLibresManual(...)` y `validarInicioManual(...)`, que pasan `Duration.ZERO` a `CalculadoraDisponibilidad`. El camino online sigue pasando 24 h. El mapeo de `MotivoRechazo` para manual es: `ANTICIPACION` → `RN-TU-12` (422, inicio en el pasado), `HORIZONTE` → `RN-TU-11`, `FUERA_DE_AGENDA` → `RN-AG-05`, `OCUPADO` → `RN-TU-01`. Se conserva el horizonte y el reloj inyectado (`ZonaNegocio`), nunca `Instant.now()`.

*Alternativa descartada*: restar 24 h al reloj en un servicio nuevo. Sería frágil frente a cambios de zona y oscurecería RN-TU-11.

### D3 — Códigos de error y KB

Se agrega `RN-TU-12` a `knowledge-base/05_reglas_de_negocio.md` con estado 422 para inicio en el pasado en altas manuales; `ReglasTurno` expone la fábrica correspondiente. Se reutilizan `RN-TU-01`, `RN-AG-05`, `RN-TU-11`, `VALIDACION` y `NO_ENCONTRADO`, sin códigos nuevos ad hoc. `RN-TU-07` sigue siendo de 409 para gestionar un turno ya empezado; no se redefine.

### D4 — RN-PA-03 no aplica al alta manual, pero los turnos manuales cuentan después

`reservarManual` salta el conteo de dos turnos futuros: el odontólogo conoce al paciente (decisión del equipo, 2026-11-23). `contarReservadosFuturos` no filtra por `origen`, así que ese mismo turno manual cuenta cuando el DNI reserva online después. Se documenta como supuesto nuevo en `knowledge-base/09_decisiones_y_supuestos.md` y se cierra PA-02 en `knowledge-base/10_preguntas_abiertas.md`.

### D5 — Horizonte sí aplica al turno manual

RN-TU-11 es configuración de la agenda, no una regla anti-abuso del paciente: se aplica igual al alta manual y a la disponibilidad autenticada (decisión del equipo, 2026-11-23). Fuera de horizonte responde 422 `RN-TU-11`.

### D6 — Contratos HTTP

| Método y ruta | Autenticación | Cuerpo / query | Respuesta |
|---|---|---|---|
| `POST /api/turnos` | Sesión `ODONTOLOGO` + CSRF | `{ prestacionId: number, inicio: "YYYY-MM-DDTHH:mm:ss±HH:mm", dni, nombre, apellido, telefono, email }` | 201 `ReservaConfirmada` (sin token ni hash); 400 `VALIDACION`; 401/403; 404 `NO_ENCONTRADO`; 409 `RN-TU-01`; 422 `RN-AG-05`, `RN-TU-11`, `RN-TU-12` |
| `GET /api/disponibilidad` | Sesión `ODONTOLOGO`, sin CSRF | `?prestacionId=<id>&fecha=YYYY-MM-DD` | 200 `DisponibilidadPublica` (misma forma que `/api/public/{slug}/disponibilidad`); 400; 401/403; 404 `NO_ENCONTRADO` |

La validación de campos reutiliza las anotaciones de `SolicitudReserva` (DNI `^[0-9]{7,8}$`, teléfono E.164, email y longitudes). El `odontologoId` que pueda llegar en cuerpo/query/header se ignora. No se exponen `token_hash`, `email_gestion` ni datos de otros pacientes.

### D7 — Frontend: módulo nuevo, reutilización quirúrgica

Se crea `features/turno-manual/` con: `modelo.ts` (tipos y payload), `validaciones.ts` (reexporta/usa las de reservar), `estado.ts` (reducer y transiciones), `api.ts` (POST autenticado y GET de disponibilidad vía `cliente.pedir`), `pantalla.ts` (descriptores y manejadores) y tests. Se reutilizan `selectorHorarios`, `navegadorDias`, `campoReserva`/`leerDatosDe` (extraídos a `shared/formulario-paciente.ts` sin cambiar su comportamiento), `erroresPorCampo` y el `api-client`. En `features/agenda/control.ts` solo se agrega el botón en `cabecera()`, una acción `nuevo-turno`, el estado del diálogo y un manejador que llama a `recargar(datos.rango)` tras el 201, imitando cancelación. No se toca la grilla salvo lo necesario para abrir el formulario desde la cabecera.

### D8 — Gate visual y verificación

La política es `system-and-mockups`, revisión 2 aprobada. Se crea el mockup local antes del apply y se renderiza a 390×844 y 1440×900 con Playwright; el apply no arranca hasta que el usuario apruebe explícitamente ese mockup. En verify se capturan las pantallas reales con el perfil `demo`, se revisan consola y red, y se completa el reporte de diferencias.

## Risks / Trade-offs

- **[Concurrencia manual/online]** → Mitigación: mismo `saveAndFlush` dentro de `GuardiaSolapamiento` y constraint `turno_sin_solapamiento`; tests de carrera manual/manual y manual/online con PostgreSQL real.
- **[Orden de locks]** → Mitigación: no crear un agregado nuevo que tome el advisory lock antes del upsert de paciente; test de concurrencia del mismo DNI sigue en pie.
- **[Turno manual reprogramado desde el link]** → Mitigación: `Turno.sucesor` hoy fija `ONLINE`; se ajusta para propagar el origen del turno original y se cubre con un test; fuera de eso, la gestion por token no cambia.
- **[C-14 tocando agenda en paralelo]** → Mitigación: diff mínimo en `features/agenda/`, sin reformatos; `git pull --ff-only origin main` antes del PR.
- **[Correo con datos inválidos]** → Mitigación: se mantiene el filtro `BuzonCorreo.admisible` y RN-NO-04: el turno queda creado aunque el correo falle; el test usa `SmtpDePrueba`.
- **[Tiempo hasta la demo]** → Mitigación: C-15 es recortable, pero no se recortan tests; si hay atraso se baja la historia antes que una prueba.

## Migration Plan

Sin cambios de esquema: no hay migración Flyway nueva ni rollback de datos. El deploy es el build habitual: `npm test` + suites Gradle (`test`, `integrationTest`), build del frontend copiado a `static` y arranque con perfil `demo`. La vuelta atrás consiste en revertir el commit del change; no deja datos nuevos obligatorios. `CHANGES.md` se marca recién después del merge a `main`.

## UI Mockups

- Design system: revisión 2 (`design-system/README.md`, `Approval: approved (2026-11-17, by user)`).
- Decisión: **sí** (política `system-and-mockups`), 2026-11-23.
- Mockups: `openspec/changes/c-15-turno-manual-odontologo/mockups/` (`README.md`, `agenda-con-nuevo-turno.html`, `formulario-turno-manual.html`, `conflicto-y-exito.html`; fuentes: mockups aprobados de C-10 y C-13, `design-system/components.html`, `tokens.css`, `brand.js`).
- Aprobación: approved (2026-11-23, by user) — aprobación explícita de los tres mockups antes del apply.
- Preview: rendered (2026-11-23, `mockups/previews/`).
- Visual verify: passed (2026-11-23, browser) — app real con perfil `demo` en `localhost:8080`, 1440×900 y 390×844. Capturas en `mockups/verify/` (agenda, selección, horarios, datos, éxito y conflicto en ambos tamaños). Revisión automatizada por DOM, bounding boxes, estilos calculados y snapshots de accesibilidad: diálogo dentro del viewport, sin overflow horizontal, rango visible preservado al abrir, etiquetas accesibles (`aria-label`, `aria-labelledby`, `aria-current`), toast “Turno cargado” y badge “Manual” en vista Día. Consola: 0 errores en el flujo real (solo el warning preexistente de submit cancelado, compartido con C-10); los 2 errores 409 aparecieron únicamente al simular el conflicto. Red: todas las llamadas `200`/`201`, ninguna fallida. Nota: el modelo no pudo inspeccionar visualmente los PNG; las capturas quedan para revisión humana y la comprobación fue programática sobre la página en vivo. Tras hacer `fast-forward` a `origin/main` (`daa28ce`, C-13/C-14) y resolver los solapamientos en `app.css` y `control.ts`, se repitió el smoke a 1440×900 y 390×844 (`post-merge-*-seleccion.png`): diálogo presente, 4 prestaciones, sin overflow horizontal, rango preservado, 0 errores de consola. Las diferencias se listan en `tasks.md` 5.3.

## Open Questions

Ninguna que bloquee specs, enfoque o tareas. PA-03 (pasos de 15 minutos) queda abierta en la KB como decisión de producto posterior; este change no la modifica.
