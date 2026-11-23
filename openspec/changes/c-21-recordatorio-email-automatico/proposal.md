# Proposal

## Why

Un aviso cerca de la consulta ayuda al paciente a recordar el turno o a liberar el horario a tiempo (US-014, RN-NO-03). Esta historia nació en C-14 y se difirió el 2026-11-23, antes de la demo: no hay tiempo de cerrar el diseño del link de gestión del recordatorio y probarlo bien antes del 25/11. C-14 se archivó solo con US-015 (WhatsApp manual).

## What Changes

- **US-014 — email automático:** un job cada 15 minutos para turnos elegibles, con un recordatorio que incluya un link válido de gestión, control de duplicados y auditoría de resultados.
- Antes de implementar, Estefi tiene que decidir el diseño de la credencial del link (D2) y la semántica de toma y fallas (D3). Ninguna alternativa queda elegida en este Propose.
- No se cambian los avisos de C-08, la reserva, la cancelación, la reprogramación ni los contratos vigentes.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `notificaciones-email`: agrega la selección periódica y el aviso de recordatorio (US-014), sin alterar los tres avisos de C-08.

## Impact

- Dependencias: **C-08** y **C-14** archivados. Se preservan los contratos de C-07/C-09/C-11/C-12 y la exclusión de PostgreSQL. Se reutilizan el transporte, el HTML y la auditoría de C-08; no se reutiliza el evento original como persistencia.
- Según la alternativa de D2, puede requerir una migración aditiva o cambios en el contrato de gestión (C-09/C-11). El token original no se puede reconstruir desde su SHA-256; ninguna modificación de seguridad se aprueba acá.
- Post-demo. Governance **CRITICO** por la credencial de gestión: análisis y aprobación humana explícita antes de escribir código.
