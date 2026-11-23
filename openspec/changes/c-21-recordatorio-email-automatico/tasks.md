# Tasks

## 1. Gate de Estefi y credencial de gestión

- [ ] 1.1 **Checkpoint bloqueante de Estefi:** antes de cualquier implementación, registrar aprobación del alcance, alternativa D2 y compatibilidad de ambos links (revocación, expiración, seguridad, población inicial, claves/rotación si aplica, C-09/C-11 y rollback), y de D3 (toma durable, marca, auditoría, resultado incierto y fallas previas a SMTP). Actualizar propuesta, diseño, delta y tareas, y formalizar las aprobaciones de política necesarias antes de codificar. Actualizar la rama contra `main` y reconfirmar los contratos de C-08/C-09/C-11.
- [ ] 1.2 Solo con 1.1 aprobado: escribir primero pruebas de ambos links con el C-09 y el consumidor C-11 vigentes, rechazo de acceso ajeno al alcance, transiciones a solo lectura, no filtración y compatibilidad de links antiguos. Implementar solo la opción aprobada; verificar cancelación/reprogramación, sucesor independiente y revocación/rotación según corresponda.

## 2. Scheduler, toma durable y correo

- [ ] 2.1 Escribir primero pruebas unitarias con `Clock` fijo de ventana y ambos bordes, tick nominal truncado a 15 min con corridas demoradas (ventanas consecutivas contiguas, sin huecos), corrida perdida/reinicio fuera de ventana, estado, marca, manual dentro del umbral y creados con 24 h exactas, snapshot inválido y ausencia de fallback. Implementar selección/revalidación y scheduler con `@Scheduled(cron = "0 0/15 * * * *")`; verificar tiempo UTC sin depender del huso de la máquina.
- [ ] 2.2 Escribir primero pruebas PostgreSQL con dos corridas reales en conexiones distintas, reinicio dentro de ventana, caída después de toma y carreras C-09/C-12 en ambos órdenes. Implementar toma durable y actualizaciones acotadas según 1.1, sin columnas que disparen el trigger de ocupación y sin SMTP bajo locks. Verificar una toma, ausencia de envío si cancelación/reprogramación gana primero, nuevo turno independiente y test existente de anti-solapamiento con exactamente una ganadora intacto.
- [ ] 2.3 Escribir primero pruebas unitarias e integración con SMTP falso para asunto, HTML escapado, link funcional, destinatario snapshot, aceptación, fallo/timeout y error de registro. Implementar despacho y auditoría según 1.1, reutilizando C-08 sin fabricar su evento; verificar estado y ocupación sin cambios, marca según decisión, auditoría sanitizada y ningún SMTP con link inválido.
- [ ] 2.4 **Checkpoint:** verificar scheduler desde datos confirmados, credencial entregada utilizable en la UI real y pruebas de reinicio, SMTP falso, reprogramación y concurrentes en verde; preservar las pruebas de C-07/C-08/C-09/C-12, sin H2 ni envío real obligatorio para los tests.

## 3. Verificación integrada

- [ ] 3.1 Verificar suites backend unitarias, integración con PostgreSQL real, Vitest y build, sin disminuir cobertura existente. Registrar evidencia y aprobaciones.
