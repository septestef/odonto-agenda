# Proposal — C-13 `agenda-odontologo-ui`

## Why

C-12 ya expone la agenda del odontólogo y la cancelación privada, y C-05 expone horarios y bloqueos, pero hoy no hay ninguna pantalla que los consuma: el odontólogo no puede ver ni gestionar su agenda desde la app. Este change entrega esa pantalla para la demo del 2026-11-25, con los mockups aprobados por el usuario el 2026-11-22 como contrato visual.

## What Changes

- Nueva carpeta `features/agenda` en el frontend (TypeScript vanilla + Vite): vista diaria y semanal con navegación anterior/siguiente y botón "Hoy", sin tocar el backend.
- Cada turno muestra paciente, prestación, horario en hora de Argentina, estado y origen; se ven franjas de atención, huecos "libre", fuera de horario, bloqueos y turnos afectados por un bloqueo (`turnosAfectados` de C-05).
- Detalle del turno (panel fijo en día de escritorio, hoja en celular, cajón en semana de escritorio) con los campos que expone C-12; si falta algún dato del paciente se omite esa fila sin cambiar el diseño.
- Cancelación con diálogo de confirmación derivado de C-11, `POST /api/turnos/{id}/cancelar` sin cuerpo y con CSRF; después de 204 se recarga el rango visible y se muestra el aviso aprobado.
- Estados visuales: carga, sin horarios, sin turnos, día sin atención, turno cancelado, 409 `RN-TU-07`, 404/409 y error de red/500.
- **Vista por defecto resuelta** (decisión #2 del README de mockups): día en celular (ancho < 720 px) y semana en escritorio (ancho ≥ 720 px). Se deduce del README + la nota de `agenda-semana.html` ("la vista por defecto es la diaria" en celular) + `design-system/brand.md` ("prioriza la densidad de información en escritorio (agenda semanal)"). En celular, la grilla semanal arranca desplazada hasta el día de hoy.
- Se reutilizan el shell, el toast y el componente `modal()` de `shared/ui/componentes.ts` (C-04); la copia y el contenido del diálogo se derivan del **mockup aprobado** de C-11, sin depender de código de C-11 (que aún no está mergeado). **No** se reutiliza ni se duplica el selector de horarios de C-10 (es de C-11). El detalle de C-13 consume horarios y bloqueos de C-05.
- Mockups ya aprobados: no se crean ni se editan mockups ni capturas en este change.

## Capabilities

### New Capabilities

- (ninguna)

### Modified Capabilities

- `agenda-odontologo`: nuevos requisitos de UI para consultar y renderizar la agenda diaria/semanal, el detalle del turno y los estados de carga/vacío/error sobre el contrato vigente de `GET /api/turnos`.
- `cancelacion-odontologo`: nuevo requisito de UI para cancelar desde la agenda con confirmación y mapear 204/404/409/500 a los estados visuales aprobados.
- `configuracion-agenda`: nuevo requisito de UI para mostrar horarios y bloqueos con `turnosAfectados` dentro de la agenda, sin cancelar turnos automáticamente.

`pacientes-odontologo` se leyó como contexto pero **no** se modifica: C-13 no llama a `GET /api/pacientes`; los datos del paciente llegan anidados en `GET /api/turnos` (P2 de C-12).

## Impact

- Frontend: carpeta nueva `frontend/src/features/agenda/`; estilos solo con variables de `design-system/tokens.css`; textos en español rioplatense con voseo; `tsconfig` strict, sin `any`; datos de usuario siempre con `textContent`.
- APIs consumidas: `GET /api/turnos?desde=&hasta=`, `POST /api/turnos/{id}/cancelar` (C-12); `GET /api/horarios`, `GET /api/bloqueos` (C-05). CSRF y errores `{codigo, mensaje}` a través del `api-client` existente de C-04.
- Tests: Vitest (`npm test` en `frontend/`) sobre funciones puras y descriptores de render, más verificación visual manual contra las capturas 390×844 y 1440×900.
- Sin cambios en backend, migraciones, secretos, specs principales ni `CHANGES.md`. Sin apply en este paso.

## Fuera de alcance (lugar reservado en el diseño)

- Botón de WhatsApp (C-14), "Nuevo turno" (C-15) y "Marcar atendido/ausente" (C-19, post-demo). El layout reserva el espacio/hueco, pero no se implementan controles ni tareas para ellos.

## Decisiones de revisión

- **B1 (resuelto por el usuario 2026-11-22, contrato C-12 vs. mockup visible)**: `estados.html` estado 7 dice "Este turno cambió / Puede que el paciente lo haya cancelado o cambiado de horario" para 404/409. Según C-12, cancelar o reprogramar por el paciente deja el turno `CANCELADO` y el `POST` de cancelación responde **204 idempotente**; los 404 son recurso ajeno/inexistente y los 409 son `RN-TU-07` (ya empezó) o `RN-TU-01` (lock). **Decisión: mantener la copia aprobada tal cual**, sin editar el mockup. El estado 7 se mostrará ante 404 `NO_ENCONTRADO` / 409 `RN-TU-01` y la salvedad se documentará en `visual-review.md` durante el verify. El mapeo funcional (aviso + recarga del rango) queda especificado igual.
- Sin pregunta abierta sobre la vista por defecto: quedó resuelta más arriba a partir de los propios mockups y `brand.md`.
- Sin recorte de datos del detalle: el DTO de C-12 (P2) expone nombre, apellido, DNI, teléfono y email del paciente, prestación con duración, estado, origen, `canceladoPor` y `canceladoEn`. Si en el apply algún campo no estuviera disponible, se omite la fila sin alterar el diseño.
