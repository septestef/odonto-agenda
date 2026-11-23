# Tasks

## 1. Gates anteriores a cualquier Apply de C-14

- [x] 1.1 Base confirmada: la rama parte de `6e3f102` (tip de `main` al 2026-11-23), con C-08/C-13, C-09/C-12 y C-11 archivados. Si `main` avanza antes del Apply, actualizar la rama y reconfirmar que no cambien `features/agenda` ni los contratos de C-09/C-11.
- [x] 1.2 Con autorización separada, crear el mockup HTML en `mockups/`, presentar demo a **390 y 1440 px** y registrar aprobación explícita de la revisión antes de cualquier Apply. Verificar acción secundaria WhatsApp en Día/Semana, hoja/panel/cajón, foco, 44 px, preparación, no disponible, error y apertura bloqueada, con tokens, `brand.js` y confirmación de Cancelar intacta. El Propose no crea este HTML. Evidencia: [mockup y capturas aprobados](mockups/README.md).
- [x] 1.3 Verificar gates 1.1–1.2 y pedir autorización expresa de Apply. El estado de OpenSpec no da esa autorización; US-015 puede empezar sin US-014, que requiere además 4.1. Acordar Strict TDD: cada tarea de implementación empieza con prueba nueva en rojo por la razón esperada, sigue con el mínimo código en verde y cierra con refactor y regresiones en verde; no eliminar, desactivar, relajar ni recortar pruebas existentes. Gates verificados; [autorización expresa del 2026-11-23](design.md#ui-mockups) solo para 2.x y 3.x.

## 2. US-015 — GET privado de WhatsApp

- [x] 2.1 Con JUnit, fijar primero el contrato de disponibilidad y composición: reloj fijo, inicio igual/pasado, estados, ambos orígenes sin mínimo de 24 h, teléfono E.164 con la misma regex de `SolicitudReserva` (extraída a una constante compartida, sin dependencia nueva), teléfono local/inválido, dirección ausente y fecha local al cambiar de día; 409 `RN-NO-06` y 422 `RN-NO-07`. Implementar política y formateo; verificar URL exacta, decodificación UTF-8 una sola vez con caracteres reservados y ausencia de PII extra y credenciales.
- [x] 2.2 Agregar primero pruebas de integración HTTP con PostgreSQL real (`turnotarde_test`) para sesión/rol, GET sin CSRF, id inválido, dueño inyectado, ajeno/inexistente indistinguibles, no-store y no mutación. Implementar consulta por id+dueño y DTO `{url}` sin alterar la cadena de seguridad ni el DTO de C-12; verificar 401/403/404/400 y 200/409/422 según el delta. **Checkpoint API:** unitarias e integración verdes; ninguna llamada externa, notificación o escritura causada por el GET.

## 3. US-015 — Acción secundaria en detalle C-13

- [x] 3.1 Agregar primero pruebas Vitest del cliente local, con `unknown` y payloads válidos/inválidos; implementar llamada relativa autenticada sin header CSRF, validación estricta de `wa.me` y manejo de códigos/errores sin log de URL, teléfono ni datos del error sin filtrar. Verificar que 401 conserva la señal de sesión y que el detalle no navega ante URL inválida.
- [x] 3.2 Agregar primero pruebas Vitest de descriptor/control para Día y Semana, elegibilidad independiente de Cancelar, teclado/foco, carga y errores; cubrir apertura desde el gesto, rechazo del popup y enlace manual, doble clic, cierre/cambio/desmontaje y respuestas tardías o fuera de orden. Implementar en `features/agenda` con el diseño aprobado y verificar que no queda una pestaña vacía en fallas o al abandonar el detalle.
- [x] 3.3 **Checkpoint US-015:** comprobar 390/1440 px en navegador en ambos modos y contrastar con el mockup aprobado. Verificar pestaña sin opener/referrer, alternativa cuando se bloquea, prellenado sin envío y flujo Cancelar sin regresiones. Confirmar Vitest, tipado/build y regresiones de agenda, C-06 y recorridos públicos C-10/C-11 si hay cambios compartidos. [Evidencia visual y diferencias preexistentes de C-13](mockups/verify/README.md).

## 4–5. US-014 — Diferida a C-21

Por decisión de Estefi del 2026-11-23, US-014 sale de C-14 sin implementarse y sin recortar pruebas. Las tareas del gate de la credencial (antes 4.1–4.2) y del scheduler, la toma durable y el correo (antes 5.1–5.4) se movieron a `openspec/changes/c-21-recordatorio-email-automatico/tasks.md`, junto con el delta de `notificaciones-email` y las decisiones D1–D3.

## 6. Verificación integrada

- [x] 6.1 Con el alcance que se haya aprobado, verificar suites backend unitarias, integración con PostgreSQL real, Vitest y build, sin disminuir cobertura existente. Registrar evidencia por historia, aprobaciones y cualquier recorte explícito; no usar el resultado CLI de planificación como evidencia de implementación o aprobación. Evidencia sobre `main` en `f63186b` (C-14 + fix de C-13), el 2026-11-23: `gradlew build` completo con frontend, exit 0; **321/321 unitarias y 431/431 de integración** con PostgreSQL local, sin omisiones; tsc y ESLint limpios; Vitest **631/631**. Alcance: US-015 (aprobación de mockup y Apply en 1.2–1.3). Recorte explícito: US-014 diferida a C-21 por decisión de Estefi.
