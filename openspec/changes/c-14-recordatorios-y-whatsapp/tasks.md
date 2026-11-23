# Tasks

## 1. Gates anteriores a cualquier Apply de C-14

- [ ] 1.1 Coordinar con el usuario una actualización autorizada de la base y reconfirmar C-08/C-13, C-09/C-12 y el flujo C-11 archivado en `6e3f102`; verificar el SHA acordado y el alcance local de agenda sin modificaciones innecesarias a `app.css` ni helpers compartidos. Este paso no autoriza rebase, merge ni cambios de refs durante Propose.
- [ ] 1.2 Con autorización separada, crear el mockup HTML en `mockups/`, presentar demo a **390 y 1440 px** y registrar aprobación explícita de la revisión antes de cualquier Apply. Verificar acción secundaria WhatsApp en Día/Semana, hoja/panel/cajón, foco, 44 px, preparación, no disponible, error y apertura bloqueada, con tokens, `brand.js` y confirmación de Cancelar intacta. El Propose no crea este HTML.
- [ ] 1.3 Verificar gates 1.1–1.2 y pedir autorización expresa de Apply. El estado de OpenSpec no da esa autorización; US-015 puede empezar sin US-014, que requiere además 4.1. Acordar Strict TDD: cada tarea de implementación empieza con prueba nueva en rojo por la razón esperada, sigue con el mínimo código en verde y cierra con refactor y regresiones en verde; no eliminar, desactivar, relajar ni recortar pruebas existentes.

## 2. US-015 — GET privado de WhatsApp

- [ ] 2.1 Con JUnit, fijar primero el contrato de disponibilidad y composición: reloj fijo, inicio igual/pasado, estados, ambos orígenes sin mínimo de 24 h, E.164 con país explícito, teléfono local/inválido, dirección ausente y fecha local al cambiar de día. Implementar política y formateo; verificar URL exacta, decodificación UTF-8 una sola vez con caracteres reservados y ausencia de PII extra y credenciales.
- [ ] 2.2 Agregar primero pruebas de integración HTTP con PostgreSQL real (`turnotarde_test`) para sesión/rol, GET sin CSRF, id inválido, dueño inyectado, ajeno/inexistente indistinguibles, no-store y no mutación. Implementar consulta por id+dueño y DTO `{url}` sin alterar la cadena de seguridad ni el DTO de C-12; verificar 401/403/404/400 y 200/409/422 según el delta. **Checkpoint API:** unitarias e integración verdes; ninguna llamada externa, notificación o escritura causada por el GET.

## 3. US-015 — Acción secundaria en detalle C-13

- [ ] 3.1 Agregar primero pruebas Vitest del cliente local, con `unknown` y payloads válidos/inválidos; implementar llamada relativa autenticada sin header CSRF, validación estricta de `wa.me` y manejo de códigos/errores sin log de URL, teléfono ni datos del error sin filtrar. Verificar que 401 conserva la señal de sesión y que el detalle no navega ante URL inválida.
- [ ] 3.2 Agregar primero pruebas Vitest de descriptor/control para Día y Semana, elegibilidad independiente de Cancelar, teclado/foco, carga y errores; cubrir apertura desde el gesto, rechazo del popup y enlace manual, doble clic, cierre/cambio/desmontaje y respuestas tardías o fuera de orden. Implementar en `features/agenda` con el diseño aprobado y verificar que no queda una pestaña vacía en fallas o al abandonar el detalle.
- [ ] 3.3 **Checkpoint US-015:** comprobar 390/1440 px en navegador en ambos modos y contrastar con el mockup aprobado. Verificar pestaña sin opener/referrer, alternativa cuando se bloquea, prellenado sin envío y flujo Cancelar sin regresiones. Confirmar Vitest, tipado/build y regresiones de agenda, C-06 y recorridos públicos C-10/C-11 si hay cambios compartidos.

## 4. US-014 — Gate de Estefi y credencial de gestión

- [ ] 4.1 **Checkpoint bloqueante de Estefi:** antes de cualquier implementación de US-014, registrar aprobación del alcance, alternativa D2 y compatibilidad de ambos links (revocación, expiración, seguridad, población inicial, claves/rotación si aplica, C-09/C-11 y rollback), y de D1–D3 (ventana perdida, toma durable, marca, auditoría, resultado incierto y fallas previas a SMTP). Actualizar propuesta/diseño/deltas/tareas y formalizar las aprobaciones de política necesarias antes de codificar. Si Estefi recorta o difiere, registrar y revisar alcance explícitamente; no autorizar, dar por completada ni ejecutar US-014 por defecto.
- [ ] 4.2 Solo con 4.1 aprobado para mantener US-014: escribir primero pruebas de ambos links con el C-09 y consumidor C-11 vigentes, rechazo de acceso ajeno al alcance, transiciones a solo lectura, no filtración y compatibilidad de links antiguos. Implementar solo la opción aprobada; verificar cancelación/reprogramación, sucesor independiente y revocación/rotación según corresponda.

## 5. US-014 — Scheduler, toma durable y correo

- [ ] 5.1 Escribir primero pruebas unitarias con `Clock` fijo de ventana y ambos bordes, corrida perdida/reinicio fuera de ventana, estado, marca, manual dentro del umbral y creados con 24 h exactas, snapshot inválido y ausencia de fallback. Implementar selección/revalidación y scheduler cada 15 min; verificar tiempo UTC sin depender del reloj de cliente ni del huso de máquina.
- [ ] 5.2 Escribir primero pruebas PostgreSQL con dos corridas reales en conexiones distintas, reinicio dentro de ventana, caída después de toma y carreras C-09/C-12 en ambos órdenes. Implementar toma durable y actualizaciones acotadas según 4.1, sin columnas que disparen el trigger de ocupación y sin SMTP bajo locks. Verificar una toma, ausencia de envío si cancelación/reprogramación gana primero, nuevo turno independiente y test existente de anti-solapamiento con exactamente una ganadora intacto.
- [ ] 5.3 Escribir primero pruebas unitarias e integración con SMTP falso para asunto, HTML escapado, link funcional, destinatario snapshot, aceptación, fallo/timeout y error de registro. Implementar despacho y auditoría según 4.1, reutilizando C-08 sin fabricar su evento; verificar estado y ocupación sin cambios, marca según decisión, auditoría sanitizada, no SMTP con link inválido, y no reintento tras toma confirmada ni con resultado incierto.
- [ ] 5.4 **Checkpoint US-014:** verificar scheduler desde datos confirmados, credencial entregada utilizable en la UI real y pruebas de reinicio, SMTP falso, reprogramación y concurrentes en verde; preservar las pruebas de C-07/C-08/C-09/C-12, sin H2 ni envío real obligatorio para los tests.

## 6. Verificación integrada

- [ ] 6.1 Con el alcance que se haya aprobado, verificar suites backend unitarias, integración con PostgreSQL real, Vitest y build, sin disminuir cobertura existente. Registrar evidencia por historia, aprobaciones y cualquier recorte explícito; no usar el resultado CLI de planificación como evidencia de implementación o aprobación.
