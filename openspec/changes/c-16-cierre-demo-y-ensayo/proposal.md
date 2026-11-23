# Propuesta

## Why

La demo necesita un arranque reproducible en localhost y un ensayo integrado de las funcionalidades entregadas. Los changes archivados no reemplazan la comprobación conjunta de datos, correo, concurrencia y pantallas en la notebook.

## What Changes

- Preparar arranque desde cero con PostgreSQL local, perfil `demo`, Flyway, frontend empaquetado y login en `http://localhost:8080`.
- Completar `.env.example` y documentar en README `Correr la demo en la notebook`, con pasos verificados y checklist.
- Agregar una nueva migración 9xx en `db/demo` con tres o cuatro turnos sintéticos adicionales, repartidos entre mañana y tarde, con prestaciones distintas y al menos uno manual; conservar V900 y los huecos de los días siguientes.
- Ejecutar suites completas y ensayo Gmail con datos sintéticos; guardar los resultados en `evidencias/` del change.
- Preparar guion, smoke, verificación a 360 px y comparación con mockups aprobados; declarar ejecución manual cuando corresponda.
- Congelar alcance: incluir WhatsApp y turno manual; excluir US-014/C-21, US-013 y funcionalidades post-demo. Las diferencias visuales se reportan para revisión.

## Capabilities

### New Capabilities

- `preparacion-demo`: condiciones observables de arranque local, datos, verificación integrada y evidencia; complementa `configuracion-entorno`, `datos-demo` y `entrega-frontend` sin cambiar sus contratos.

### Modified Capabilities

Ninguna. No cambian las reglas de reserva, autenticación, correo ni API.

## Impact

- Dependencias: C-06, C-11 y C-13 completos; C-12 archivada; C-14 incluye WhatsApp y difiere US-014 a C-21; C-15 incluye turno manual.
- Frente A: `README.md`, `.env.example`, `backend/**` para seed/configuración y tests necesarios, sección A de `tasks.md` y `openspec/changes/c-16-cierre-demo-y-ensayo/evidencias/` para sus resultados.
- Frente B: guion, evidencia y sección B de `tasks.md`. Responsables, fecha y coordinación se definen en design/tasks.
- README contiene pasos y checklist; los comandos ejecutados, revisiones, cantidades y resultados van en `evidencias/`.
- Sin dependencias nuevas ni cambios visuales; no requiere mockup nuevo. Cambios fuera del alcance se elevan para decisión.
