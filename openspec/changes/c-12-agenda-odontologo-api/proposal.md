# Proposal

## Why

El odontólogo necesita consultar su agenda y sus pacientes, y cancelar un turno futuro con aviso por email (US-010 backend y US-012). C-03, C-05, C-07, C-08 y C-09 ya están integrados y archivados: aportan sesión, configuración, persistencia, consumidor de notificaciones y protocolo de gestión para completar este contrato antes de C-13.

## What Changes

- Proponer `GET /api/turnos?desde=&hasta=`: fechas locales obligatorias, intervalo semiabierto por intersección, todos los estados y orden estable; DTOs privados para el dueño de sesión.
- Proponer `GET /api/pacientes`: lectura paginada de pacientes propios con un wrapper JSON estable, sin búsqueda ni historia clínica.
- Proponer `POST /api/turnos/{id}/cancelar`: sin cuerpo, 204 vacío, transición transaccional de un reservado futuro a cancelado por odontólogo y repetición idempotente.
- Serializar cancelaciones con el protocolo ya integrado de `GestionTurnoService.cancelar`: lock solo de fila de turno, reloj posterior al lock, transición, flush y evento solo por transición efectiva; autorizar por id + dueño de sesión, no por token.
- El aviso usa exclusivamente `turno.email_gestion`, nunca `paciente.email`: si falta o no es admisible por `BuzonCorreo`, cancelar igual y emitir sin email utilizable; C-08 no envía ni registra aviso. Ninguna respuesta (agenda, detalle anidado, pacientes, cancelación o errores) expone `token_hash` ni `email_gestion`, sus variantes JSON ni el valor reservado de esos campos; el email mutable del paciente sí forma parte del DTO privado previsto.
- Conservar el contrato compartido de locks: 409 `RN-TU-01`, sin introducir `CONFLICTO_CONCURRENTE`. Precisar precedencia CSRF/autorización sin cambiar seguridad. P4 queda resuelta para planificación: CANCELADO → 204 idempotente; otros estados con inicio <= ahora → 409 RN-TU-07; ATENDIDO/AUSENTE futuros → inconsistencia interna persistida con 500 ERROR_INTERNO genérico y diagnóstico local sanitizado, sin mutación, flush de actualización, evento ni aviso; RESERVADO futuro → cancelación normal. Sin reparación automática ni RN nueva o RN-TU-10 como rechazo.
- **P1–P9 aprobadas y apply autorizado explícitamente por el usuario el 2026-11-22**, según pedido humano al ejecutor C12; resoluciones en `design.md`. Los tres checkpoints están implementados y el check completo final pasa (299 unitarios/416 IT). `TDD-EVIDENCE.md` conserva comandos, resultados y desviación de trazabilidad RED de algunos escenarios, con tareas formales abiertas para revisión del coordinador. La autorización no se infiere del CLI.

## Capabilities

### New Capabilities

- `agenda-odontologo`: consulta privada de turnos diarios/semanales por rango, DTOs y aislamiento.
- `pacientes-odontologo`: consulta privada y paginada de pacientes del dueño de sesión.
- `cancelacion-odontologo`: cancelación futura, estados, idempotencia, concurrencia y liberación del horario.

### Modified Capabilities

- `eventos-turno`: agregar contrato del emisor de `TurnoCancelado` para cancelación autenticada; mantener intacto el de reserva.
- `errores-api`: precisión de precedencia de filtros y confidencialidad de respuestas C-12; conserva el requisito compartido de conflictos de escritura y códigos actuales, sin excepción nueva para locks.

## Impact

- Futuro código acotado a módulos `turno` y `paciente`: controladores delgados, servicios transaccionales, consultas filtradas, records DTO y método de cancelación por odontólogo en `Turno`; reutilizar códigos/advice existentes. P4 es compatible con el fallback 500 actual mediante diagnóstico local sanitizado y excepción de mensaje fijo sin datos ni causas sensibles, sin cambios compartidos.
- Reutilizar `OdontologoActual`, `AislamientoOdontologo`, `ZonaNegocio`/Clock, `ResumenTurno`, `TurnoCancelado` y notificaciones C-08. No cambiar sesión, filtros, CSRF, configuración ni infraestructura SMTP.
- Dependencias directas C-07/C-08 y prerrequisito técnico C-03; GATE 3 satisfecho por C-05/C-08. C-05 (`56a17b0`, merge `4f38ffc`, archivo `152452f`) y C-09 (`bf5de6a`, merge `308d33a`, archivo `c09cd5d`) son ancestros de `origin/main=2130a3a`; diseño registra evidencia de código/specs.
- No incluye UI, cambios de horarios/bloqueos C-05/C-13, carga manual C-15, cambios de gestión por token C-09 ni búsqueda C-17. Reutiliza lo integrado sin modificar esos contratos.
- No se prevén migraciones, dependencias nuevas ni cambios en specs principales. El baseline existente precede a producción y tests nuevos de consulta agenda, implementados bajo TDD.
- El PR funcional conserva el change activo y no modifica `CHANGES.md`. Solo después de integrar ese PR se prepara el archivado/sync/actualización del roadmap en un PR separado, con su autorización correspondiente.
