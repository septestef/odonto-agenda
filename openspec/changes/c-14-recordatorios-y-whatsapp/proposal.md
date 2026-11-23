# Proposal

## Why

Un aviso cerca de la consulta ayuda al paciente a recordar el turno o a liberar el horario a tiempo. El odontólogo también necesita poder avisar por WhatsApp sin escribir el mensaje de cero y sin habilitar envíos automáticos por ese canal.

## What Changes

- **US-014 — email automático:** proponer una corrida cada 15 minutos para turnos elegibles, con un recordatorio que incluya un link válido de gestión, control durable de duplicados y auditoría de resultados. Estefi debe aprobar el diseño de la credencial y la semántica de fallas antes de implementar esta historia.
- **US-015 — WhatsApp manual:** agregar una consulta privada por turno y una acción secundaria en el detalle de agenda Día/Semana. La app abre el mensaje preparado en otra pestaña; solo el odontólogo decide si lo envía.
- Mantener ambas historias separadas en aceptación y tareas. C-14 es **MEDIO [UI]**, opcional en la demo: no se recorta ninguna historia ni prueba sin decisión explícita. No se implementa C-15, ni se cambia la reserva, la cancelación, la reprogramación o los contratos vigentes en este Propose.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `notificaciones-email`: agrega la selección periódica y el aviso de recordatorio (US-014), sin alterar los tres avisos de C-08.
- `agenda-odontologo`: agrega la consulta privada de URL y la acción del detalle (US-015), sin ampliar el DTO de agenda.

## Impact

- Dependencias: **C-08 y C-13** archivados; se preservan los contratos de C-07/C-09/C-12 y la exclusión de PostgreSQL. Se reutiliza el transporte, HTML y auditoría de C-08 cuando corresponda; no se reutiliza el evento original como persistencia.
- US-014 puede requerir cambios de persistencia o del contrato de gestión según la alternativa aprobada. El token aleatorio original no se puede reconstruir desde su SHA-256; ninguna modificación de seguridad se aprueba acá.
- US-015 usa la sesión existente para `GET /api/turnos/{id}/whatsapp` y un cambio local al detalle de `features/agenda`; no necesita integración ni credenciales de WhatsApp.
- La aprobación explícita del mockup HTML C-14 es requisito **antes de cualquier Apply**, además de coordinar la base y solicitar Apply por separado. La validación de OpenSpec no acredita esas aprobaciones.
