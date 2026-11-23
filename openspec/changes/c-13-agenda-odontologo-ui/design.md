# Design — C-13 `agenda-odontologo-ui`

## Context

La motivación y el alcance están en `proposal.md`; los requisitos en los deltas de `specs/`. Este diseño solo fija cómo se consume lo que ya existe, sin cambiar backend ni contratos.

Contratos verificados:

- **C-12** (`openspec/changes/archive/2026-11-22-c-12-agenda-odontologo-api/design.md`, P1–P9): `GET /api/turnos?desde=&hasta=` con fechas locales `YYYY-MM-DD`, rango semiabierto `[desde, hasta)` de 1 a 31 días, array ordenado por `inicio` e `id`, todos los estados (incluidos `CANCELADO`) y fechas con offset de Buenos Aires. Errores: 400 `SOLICITUD_INVALIDA` (fecha ausente/inválida), 400 `VALIDACION` (orden o más de 31 días). `POST /api/turnos/{id}/cancelar` no lleva cuerpo, usa CSRF `XSRF-TOKEN` / `X-XSRF-TOKEN`, responde 204 incluso si ya estaba cancelado; errores 404 `NO_ENCONTRADO`, 409 `RN-TU-07`, 409 `RN-TU-01`, 500 `ERROR_INTERNO`. El DTO P2 trae `paciente` con `id,dni,nombre,apellido,telefono,email`, `prestacion` con `id,nombre,duracionMinutos`, `canceladoPor` y `canceladoEn`. Nunca expone token, hash ni `email_gestion`.
- **C-05** (`openspec/changes/archive/2026-11-22-c-05-configuracion-agenda-api/design.md`, D2/D3/D6): `GET /api/horarios` devuelve `{franjas:[{diaSemana,inicio:"HH:mm",fin:"HH:mm"}]}`; `GET /api/bloqueos` devuelve `{bloqueos:[{id,inicio,fin,motivo,turnosAfectados:[...]}]}` con `id,inicio,fin,estado,pacienteNombre,pacienteApellido,prestacionNombre`. Crear un bloqueo **no** cancela turnos (RN-AG-02).
- **Frontend existente (C-04)**: `router.ts` ya tiene la ruta `/agenda`; `shared/api-client.ts` reenvía `X-XSRF-TOKEN` desde la cookie `XSRF-TOKEN`, maneja 204 y errores `{codigo,mensaje}` y notifica sesión vencida en 401 `NO_AUTENTICADO`; `shared/fechas.ts` aporta `fechaLocalArgentina`, `horaArgentina`, `sumarDias` y `restarDias`; `shared/ui/componentes.ts` y `estilos/app.css` ya importan los tokens. Vitest corre en `environment: 'node'`, así que los tests son de funciones puras y descriptores, no de DOM real.

La política visual `system-and-mockups` ya está satisfecha: los cinco mockups y sus capturas fueron aprobados el 2026-11-22 y no se editan en este change.

## Goals / Non-Goals

**Goals:**

- Entregar la agenda diaria/semanal y el detalle/cancelación del odontólogo con el diseño aprobado, usando exclusivamente los contratos de C-12 y C-05.
- Mantener el anti-solapamiento y la autoridad del servidor: la UI nunca decide si un turno es cancelable, solo refleja estados y reacciona al 409 del backend.
- Tipar todo en modo strict, sin `any`, con estilos solo desde `design-system/tokens.css` y textos en voseo.

**Non-Goals:**

- Backend, migraciones, Flyway, `Clock`, nuevos endpoints ni cambios en specs principales.
- Selector de horarios de C-10 / reprogramación de C-11: esos componentes pertenecen a la rama pública y no se reutilizan ni duplican acá.
- Cancelar turnos desde un bloqueo automáticamente: solo se listan con `turnosAfectados` y el odontólogo decide (RN-AG-02).
- WhatsApp (C-14), "Nuevo turno" (C-15) y "Marcar atendido/ausente" (C-19).
- Aprobar de nuevo los mockups; la aprobación vigente es la del 2026-11-22.

## Decisions

### D1 — Módulo y reutilización

Todo el código nuevo vive en `frontend/src/features/agenda/` (tipos, consultas, armado de vistas, estados y tests). Se reutilizan el shell, el `api-client`, `shared/fechas.ts`, los descriptores de `shared/ui/componentes.ts` —incluido `modal()`— y el toast, todos de C-04. La copia y el contenido del diálogo se derivan del mockup aprobado de C-11, **sin depender de código de C-11** (que aún no está mergeado). No se agrega dependencia ni se toca `router.ts`. Alternativa descartada: crear un módulo compartido de agenda con C-10/C-11; sus necesidades son distintas (disponibilidad reservable vs. lectura de agenda) y acoplarlos rompería la independencia de los changes.

### D2 — Vista por defecto (decisión #2 del README, resuelta)

La primera carga elige **día** con ancho < 720 px y **semana** con ancho ≥ 720 px. El umbral 720 px es el que ya usa `mockup.css` para separar hoja/cajón y disponer las tarjetas de turno; en escritorio se prioriza la densidad según `design-system/brand.md`. La nota de `agenda-semana.html` ("la vista por defecto es la diaria") se interpreta en su contexto celular y coincide con el README. En celular, la grilla semanal se desplaza horizontalmente y arranca con el día de hoy visible.

La elección queda en el estado de la vista mientras no se recargue; cambiar de día a semana (o viceversa) y navegar fechas conserva la otra elección. No se persiste en URL ni `localStorage`: agregar persistencia no está en el mockup y complicaría los tests. La fecha inicial es `fechaLocalArgentina(new Date())`; la semana empieza el lunes local y termina el domingo, pero la grilla solo dibuja columnas para días con franjas (sábado/domingo no aparecen si no atiende).

### D3 — Rangos y mapeo de contratos

- Día: `desde = F`, `hasta = sumarDias(F, 1)`. Semana: `desde = lunes`, `hasta = sumarDias(lunes, 7)`. Ambos cumplen el rango 1–31 de P1, por lo que los 400 `SOLICITUD_INVALIDA`/`VALIDACION` son imposibles con fechas generadas por la UI; se tratan como error inesperado si llegaran.
- Los DTOs de respuesta se validan al entrar (`unknown` → tipo propio) sin `any`. Los instantes se formatean con `horaArgentina`/utilidades existentes para mostrar offsets de Buenos Aires, sin recalcular zonas con la zona del dispositivo.
- Se pide `GET /api/horarios` y `GET /api/bloqueos` una vez por carga de rango; `GET /api/bloqueos` se llama sin filtros y se filtra localmente por superposición con el rango visible (los filtros de C-05 son opcionales y el volumen de la demo es chico). Alternativa descartada: pasar fechas con offset en la query, porque D2 no fija un formato exacto para esos filtros.
- El detalle usa `paciente` del DTO de C-12, no `GET /api/pacientes`. Si un campo no viniera, se omite esa fila; el diseño visual no cambia. Nunca se renderizan token, hash, `email_gestion`, `odontologoId` ni auditoría.

### D4 — Composición visual

La vista diaria es una lista por franjas: horas `inicio–fin`, badges de estado/origen, nombre del paciente, prestación, "libre" para huecos entre eventos dentro de una franja, "fuera del horario de atención" entre franjas y una línea "ahora". Los turnos cancelados se tachan y los pasados/en curso se atenúan. La vista semanal es una grilla posicionada por minutos desde la primera apertura hasta el último cierre, con columnas por día, hoy resaltado, bloqueos y borde de aviso en turnos afectados. Los bloqueos y sus `turnosAfectados` se dibujan en ambas vistas; en la diaria el aviso incluye acción de cancelar por id del turno afectado. Todas las piezas nuevas se derivan de tokens (`--turno-*`, `--bloqueo-bg`, `--color-warning-600`, `--space-*`, `--radius-*`, `--focus-ring`); está prohibido hardcodear colores, espaciados, radios o fuentes. Alternativa descartada: reutilizar el listado de horarios libres de C-10; acá los huecos son informativos, no reservables.

### D5 — Detalle y cancelación

El detalle es panel fijo en día de escritorio (≥ 1100 px), hoja desde abajo en celular y cajón a la derecha en semana de escritorio; el mismo componente cambia de contenedor. Incluye el aviso de bloqueo cuando el turno está en `turnosAfectados`. Cancelar abre el diálogo de confirmación de C-11 con foco en "No, volver"; el POST va sin cuerpo y con CSRF a través del `api-client`. Tras 204 se cierra, se recarga el rango visible y se muestra el toast "Turno cancelado". Si el turno estaba en un bloqueo, se omite la frase sobre el horario libre.

Mapeo exacto: 204 (inclusive repetido) → éxito; 409 `RN-TU-07` → estado 6 ("Este turno ya empezó") + recarga; 404 `NO_ENCONTRADO` y 409 `RN-TU-01` → estado 7 ("Este turno cambió") + recarga; 500 `ERROR_INTERNO`, `SIN_CONEXION` y otros → estado 8 ("No pudimos cargar la agenda") con Reintentar; 401/403 → flujo de sesión vencida del `api-client`. No hay reintento automático del POST. **B1 resuelto por el usuario (2026-11-22)**: se mantiene la copia aprobada del estado 7 sin editar el mockup; la salvedad (C-12 no produce ese texto como 404/409) se documenta en `visual-review.md` durante el verify.

### D6 — Reloj y noción de "ahora"

No hay endpoint de reloj del servidor en el contrato de C-13 y agregarlo excedería el alcance. La línea "ahora", el encabezado "Hoy" y el atenuado de turnos usan la hora del navegador convertida con `fechaLocalArgentina`/`horaArgentina` (zona fija de negocio, no zona del dispositivo). Esto es solo presentación: la regla RN-TU-07 y el instante de cancelación los decide el `Clock` inyectado de C-12. Si el reloj del equipo está desfasado, la UI puede ofrecer Cancelar y recibir 409 `RN-TU-07`; el estado 6 cubre ese caso. Alternativa descartada: leer el header HTTP `Date` como reloj del servidor; no está en el contrato de C-12 y daría una falsa sensación de precisión.

### D7 — Accesibilidad, seguridad de texto y tests

Se conservan el salto al contenido, `aria-pressed` en Día/Semana, `aria-current` en el turno elegido, `role="dialog"/"alertdialog"` con `aria-modal`, foco inicial y cierre con Escape, objetivos táctiles de `--control-height` y `prefers-reduced-motion` vía tokens. Todo dato de usuario se inserta con `textContent`, nunca `innerHTML`. Los tests Vitest cubren rangos de fecha, armado de consultas, fusión turnos/franjas/bloqueos, mapeo de errores y descriptores de render, con reloj fijo inyectado en las funciones puras. La comparación pixel a pixel contra las capturas es verificación manual; los tests no la reemplazan.

### D8 — Lugar reservado para changes futuros

El maquetado deja el hueco para el botón de WhatsApp (C-14), el alta de "Nuevo turno" (C-15) y las acciones de asistencia (C-19), pero no se renderiza el recuadro de nota del mockup ni se implementan esos controles. No hay tareas en `tasks.md` para ellos.

## UI Mockups

- **Design system**: revisión 2 (`design-system/tokens.css`, `brand.js`, `components.html`)
- **Decision**: yes (2026-11-22)
- **Approval**: approved (2026-11-22, by user)
- **Preview**: rendered (2026-11-22, 390×844 y 1440×900, capturas en `previews/`)

Pantallas aprobadas (no se editan):

| Pantalla | Mockup | Capturas |
|---|---|---|
| Vista diaria | [agenda-dia.html](mockups/agenda-dia.html) | [390×844](mockups/previews/agenda-dia-390.png) · [1440×900](mockups/previews/agenda-dia-1440.png) |
| Vista semanal | [agenda-semana.html](mockups/agenda-semana.html) | [390×844](mockups/previews/agenda-semana-390.png) · [1440×900](mockups/previews/agenda-semana-1440.png) |
| Detalle del turno | [turno-detalle.html](mockups/turno-detalle.html) | [390×844](mockups/previews/turno-detalle-390.png) · [1440×900](mockups/previews/turno-detalle-1440.png) |
| Cancelar turno | [cancelar-confirmacion.html](mockups/cancelar-confirmacion.html) | [390×844](mockups/previews/cancelar-confirmacion-390.png) · [1440×900](mockups/previews/cancelar-confirmacion-1440.png) |
| Estados | [estados.html](mockups/estados.html) | [390×844](mockups/previews/estados-390.png) · [1440×900](mockups/previews/estados-1440.png) |

Las notas punteadas amarillas (`.nota-mockup`) y los marcadores de lugar reservado son ayudas para quien revisa: no van a la UI real.

## Visual verify

**Completado el 2026-11-22 (tarea 8.3).** Se capturó la app real contra backend +
Vite con Chrome headless/CDP a 390×844 y 1440×900; las diez imágenes están en
[`mockups/verify/`](mockups/verify/) y se compararon con las diez capturas aprobadas
de `mockups/previews/`. Resultado: **0 bloqueantes, 0 mayores, 4 menores** (datos
demo, alto viewport vs. full-page, estados 6/7/8 cubiertos por tests y salvedades
B1/Escape). Durante el verify se corrigió con TDD el `<h1>` semanal para que muestre
el último día con franjas: «23 al 27 de noviembre», idéntico al mockup. El detalle,
la clasificación por pantalla y la evidencia de tests/build están en
[`visual-review.md`](visual-review.md). Los mockups y previews aprobados no se tocaron.

## Risks / Trade-offs

- **[B1] Copia del estado 7 vs. contrato C-12 (resuelto 2026-11-22)** → el usuario decidió mantener la copia aprobada; el mockup no se edita y la salvedad se registra en `visual-review.md`. El comportamiento funcional (alerta + recarga) está especificado igual.
- **[Desfase del reloj del navegador]** → solo afecta la línea "ahora" y la atenuación; el backend sigue siendo autoridad y el 409 `RN-TU-07` se muestra según D5.
- **[Datos del detalle más pobres que el mockup]** → se omiten filas, no se rediseña; el DTO P2 de C-12 cubre todos los campos dibujados.
- **[C-05 no devuelve franjas por rango, sino semanales]** → se combinan con la fecha local; un cambio de horario mientras la agenda está abierta solo se ve al recargar, aceptable para la demo.
- **[Grilla semanal con muchas columnas]** → se acota a días con franjas y al rango de la primera apertura al último cierre, como en el mockup.
- **[Sin persistencia de la vista elegida]** → una recarga vuelve al default por dispositivo; se prefirió simpleza y testabilidad.

## Migration Plan

No hay migración de esquema ni cambios de backend. El apply se limita a `frontend/src/features/agenda/`, estilos derivados de tokens y tests Vitest; se verifica con `cd frontend && npm test` y `npm run build`. Rollback: revertir esos archivos; los datos y contratos de C-05/C-12 quedan intactos. Después del apply y la revisión visual contra las cinco capturas, se sigue el flujo habitual de archive y marcado de `[x]` en `CHANGES.md`, sin incluirlo en este proposal.

## Open Questions

Ninguna. La vista por defecto quedó resuelta en D2 y el blocker B1 fue resuelto por el usuario el 2026-11-22: se mantiene la copia aprobada del estado 7 y la salvedad se documenta en `visual-review.md`.
