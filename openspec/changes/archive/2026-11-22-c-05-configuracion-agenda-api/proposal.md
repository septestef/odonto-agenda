# Propuesta: C-05 — Configuración de la agenda (API)

## Why

C-02 dejó las tablas, entidades y repositorios de horarios, prestaciones y bloqueos, pero todavía no hay forma de configurarlos por HTTP: la agenda de la demo vive en el seed. C-06 (configuración UI) y C-13 (agenda del odontólogo) necesitan un contrato estable y aislado antes de poder implementarse.

## What Changes

- Exponer `GET/PUT /api/horarios` con reemplazo semanal atómico: varias franjas por día, días sin atención y rechazo de franjas solapadas o con inicio no anterior al fin (RN-AG-01).
- Exponer `GET/POST/PUT /api/prestaciones` y `DELETE` con baja lógica: duración múltiplo de 15 (RN-AG-03); una prestación dada de baja deja de ofrecerse en la reserva online, pero los turnos existentes conservan su prestación y duración (RN-AG-04, RN-TU-02).
- Exponer `GET/POST/DELETE /api/bloqueos` con instantes con offset y rango válido. Crear un bloqueo **lista** los turnos activos que caen en el rango sin cancelarlos (RN-AG-02); la cancelación posterior la hace C-12.
- Todos los endpoints quedan bajo `/api/**`, con la sesión HTTP y el CSRF `X-XSRF-TOKEN` que ya exige C-03. El dueño sale siempre de `OdontologoActual.id()` y `AislamientoOdontologo.exigir(...)`: se ignora cualquier `odontologoId` del request y un recurso ajeno o inexistente responde 404 uniforme (RN-SE-01).
- Los rechazos por regla de negocio usan `codigo` `RN-AG-NN` con el formato `{codigo, mensaje, detalles?}` del `@RestControllerAdvice` central; las validaciones estructurales usan `VALIDACION` 400.
- **Sin migración nueva:** `V2` ya cubre tablas, checks e índices. Si el apply descubre una necesidad, se agrega `V5__<descripcion>.sql` (o `V5_1__`, ...) sin editar `V2`, `V3` ni ninguna migración ya mergeada.
- Contratos JSON y tabla de errores documentados en `design.md` como referencia para C-06 y C-13. No incluye UI: los mockups los hacen esos changes.

## Capabilities

### New Capabilities

- `configuracion-agenda`: administración autenticada y aislada del horario semanal, las prestaciones y los bloqueos del odontólogo, con sus reglas y contratos HTTP.

### Modified Capabilities

Ninguna. `aislamiento-odontologo`, `errores-api`, `sesion-odontologo` y `modelo-datos` siguen vigentes sin cambios: C-05 los consume, no los redefine.

## Impact

- Backend: módulos `agenda` y `prestacion` (controladores, servicios, DTOs `record` y métodos nuevos al final de los repositorios existentes), reutilizando `shared/seguridad` y `shared/error`. Sin dependencias nuevas ni variables de entorno nuevas.
- Tests: unitarios puros de validación de franjas y reglas RN-AG; integración contra PostgreSQL real `turnotarde_test` con `AutenticacionDePrueba` (sesión + CSRF), incluyendo aislamiento entre odontólogos y regresión de los endpoints públicos de C-07.
- Seguridad: no se modifica `ConfiguracionSeguridad`. Los endpoints heredan `hasRole("ODONTOLOGO")` y la exigencia de CSRF en escrituras de C-03.
- Gobernanza: **MEDIO** según `CHANGES.md`: se implementa con checkpoints, sin aprobación previa de Estefi antes del apply. No es change **[UI]**: no requiere mockup ni gate visual.
