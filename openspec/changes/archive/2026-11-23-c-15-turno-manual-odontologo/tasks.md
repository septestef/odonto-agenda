# Tasks — C-15 `turno-manual-odontologo`

## 1. Gate visual y documentación base

- [x] 1.1 Aprobación explícita del usuario recibida el 2026-11-23 (opción “Aprobar mockups”). `design.md` registra `Approval: approved (2026-11-23, by user)` antes de tocar código de UI; evidencia: respuesta del usuario y `design.md` actualizado.
- [x] 1.2 Cerrar PA-02 (sección “Preguntas resueltas” de `10_preguntas_abiertas.md`), agregar SU-07 sobre RN-PA-03 en `09_decisiones_y_supuestos.md` y documentar RN-TU-12 en `05_reglas_de_negocio.md`; RN-TU-11 queda vigente para turnos manuales y se registró su alcance en la propia regla.

## 2. Backend — disponibilidad autenticada

- [x] 2.1 Agregar `horariosLibresManual` y `validarInicioManual` en `DisponibilidadService` usando anticipación cero y mapeo `ANTICIPACION → RN-TU-12`; verificar con tests unitarios/de integración que hoy ofrece horarios y que ignora bloqueos, turnos ocupados y fechas fuera del horizonte.
- [x] 2.2 Crear `GET /api/disponibilidad` autenticado con DTO de respuesta idéntico al público; verificar con IT que responde 200 con sesión, 401 sin sesión, 403 sin rol, 400 por parámetros inválidos y 404 por prestación ajena o dada de baja.

## 3. Backend — alta manual

- [x] 3.1 Parametrizar `ReservaService` con `OrigenTurno` y `aplicarLimitePaciente`, exponiendo `reservarManual` sin duplicar el orden de locks; verificar que los tests existentes de reserva online siguen en verde.
- [x] 3.2 Crear `SolicitudTurnoManual` y `POST /api/turnos` en `ControladorAgenda`, tomando el odontólogo de `OdontologoActual` y resolviendo la prestación propia activa; verificar 201 sin token/hash, 400 `VALIDACION` por campo y 404 uniforme por prestación ajena.
- [x] 3.3 Agregar `RN-TU-12` (422) a `ReglasTurno` y mantener 422 `RN-AG-05`/`RN-TU-11` y 409 `RN-TU-01`; verificar los códigos y mensajes por IT contra PostgreSQL `turnotarde_test`.
- [x] 3.4 Ajustar `Turno.sucesor` para preservar el origen del turno original; verificar con un test que reprogramar un turno manual desde el link conserve `MANUAL`.
- [x] 3.5 Escribir los IT de reglas manuales: <24 h permitido por el endpoint autenticado y rechazado por el público, solapamiento 409, carrera manual/manual y manual/online (una sola gana), fuera de franja/bloqueo, pasado y horizonte; verificar con `./gradlew integrationTest` en verde.
- [x] 3.6 Verificar el efecto secundario completo: paciente upsert por DNI, `TurnoReservado` con origen `MANUAL` y correo de confirmación con `/turno/{token}` vía `SmtpDePrueba`, sin envío cuando la transacción se revierte; verificar con IT dedicado.
- [x] 3.7 Verificar aislamiento: identificador de odontólogo en el pedido ignorado, prestación ajena 404 y sin sesión/CSRF 401/403; verificar con IT reutilizando `CuentaDePrueba`.

## 4. Frontend — flujo de turno manual

- [x] 4.1 Extraer los helpers y tipos del formulario de paciente a `shared/formulario-paciente.ts` sin cambiar el flujo público; verificar que `npm test` y `npm run build` siguen en verde.
- [x] 4.2 Crear `features/turno-manual/` con modelo, payload, validaciones y cliente autenticado (`POST /api/turnos`, `GET /api/disponibilidad`); verificar con tests de armado de solicitud y mapeo de errores.
- [x] 4.3 Implementar el reducer y la pantalla del flujo (prestación → día → horario → datos → envío/éxito), reutilizando `selectorHorarios` y `navegadorDias`; verificar con tests la máquina de estados, el estado vacío, los errores de campo y el 409 con refresco de horarios sin perder datos.
- [x] 4.4 Agregar el botón “Nuevo turno” en la cabecera de agenda y el manejador que abre el formulario, cierra al 201, recarga el rango y muestra el turno nuevo con origen `MANUAL`; verificar con tests que la recarga ocurre y que el toast aparece, sin reordenar archivos de C-14.
- [x] 4.5 Revisar el layout a 360 px y los atributos de accesibilidad contra el mockup aprobado; verificar con capturas locales y `npm test` en verde. Sin overflow horizontal; los tres días que sobresalen están dentro de la tira scrolleable `days` (previsto en el mockup). `aria-label`/`aria-labelledby`/`aria-current` presentes.
- [x] 4.6 Corregir los dos errores de lint del integrate (`prefer-const` en `control.ts` y asignación inútil en `main.ts`) y el paso del progreso al elegir día; `npm run lint` limpio.

## 5. Verificación integral

- [x] 5.1 Suite completa en verde: `.\gradlew.bat test integrationTest` → `BUILD SUCCESSFUL in 5m 10s`; `npm test` → 51 archivos / 592 tests; `npm run build` y `npm run lint` limpios. Ningún test omitido.
- [x] 5.2 Smoke real con perfil `demo`: login → agenda → “Nuevo turno” → prestación → día → horario → datos → 201 → toast “Turno cargado” → badge “Manual” en vista Día; horario de hoy (sin anticipación de 24 h) aceptado. La entrega del correo y el link `/turno/{token}` quedan cubiertos por `TurnoManualCorreoIT` (no se verificó una caseta real: no hay SMTP real en local).
- [x] 5.3 Comparación con el mockup aprobado a 390×844 y 1440×900 (más 360×740): capturas en `mockups/verify/`; sin errores de consola en el flujo real (solo el warning preexistente de submit cancelado) ni requests fallidos (200/201). Diferencias: ninguna bloqueante ni mayor; menor — el badge “Manual” solo se renderiza en vista Día (comportamiento existente de la agenda, no de C-15). `Visual verify:` completado en `design.md`.

## Workflow follow-up

- Hacer `git pull --ff-only origin main` antes del PR y resolver solapamientos con C-14 sin reformatos.
- Después del merge a `main`: archivar el change con OpenSpec, sincronizar specs, marcar `[x]` C-15 en `CHANGES.md` y guardar en memoria las decisiones RN-TU-11/RN-PA-03.
