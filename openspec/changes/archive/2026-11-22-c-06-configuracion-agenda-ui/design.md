# Design — C-06 `configuracion-agenda-ui`

## Context

Motivación y alcance: `proposal.md`. Requisitos: `specs/configuracion-agenda-ui/spec.md` y el delta de `specs/shell-frontend/spec.md`. Gobernanza **BAJO**, con el gate visual `system-and-mockups` (revisión 2 aprobada del design system) como restricción adicional: sin mockup aprobado no se arranca el apply.

Estado que hereda este change de C-01..C-05 y C-07/C-08:

| Pieza | Estado actual |
|---|---|
| Router | `frontend/src/router.ts` ya resuelve `/config`; `main.ts` lo muestra con `vistaMarcador` dentro del `layout(...)`. El guard consulta `/api/auth/me` y cachea la sesión. |
| Descriptor de UI | `shared/ui/componentes.ts` expone `boton`, `alerta`, `campo` y `modal` sobre el adaptador `NodoUI`; `shared/ui/layout.ts` ya dibuja app bar, nav Agenda/Configuración, nombre de la odontóloga y "Cerrar sesión". No se agrega framework ni librería de componentes. |
| Cliente API | `shared/api-client.ts` ya manda `X-XSRF-TOKEN` en escrituras, usa `credentials: 'same-origin'`, lanza `ErrorApi` con `{codigo, mensaje, detalles?}` y avisa de 401 `NO_AUTENTICADO`. `pedir<T>` hace un cast: los DTOs de respuesta DEBEN validarse en tiempo de ejecución antes de usarlos. |
| Fechas | `shared/fechas.ts` da `fechaLocalArgentina`, `horaArgentina`, `esFechaValida`, `sumarDias` y `rangoHorario` sin depender de la zona del dispositivo. No se agrega `date-fns` ni librería de fechas. |
| Contratos C-05 | `GET/PUT /api/horarios` (objeto `{franjas}` sin ids), `GET/POST/PUT/DELETE /api/prestaciones` (incluye inactivas) y `GET/POST/DELETE /api/bloqueos` (traen `turnosAfectados`). Instantes con offset; horas `HH:mm`. |
| Aislamiento | El id del odontólogo sale de la sesión en el servidor (RN-SE-01); la UI no tiene ni necesita un `odontologoId`. |
| Estilos | `frontend/src/estilos/app.css` ya importa los tokens del design system. Los componentes nuevos se derivan de variables `--*`, nunca de literales de color, espacio, radio o fuente. Durante el apply se confirmó el import existente y no se modifica ningún mockup. |

## Goals / Non-Goals

**Goals:**

- Sustituir el marcador de `/config` por tres secciones navegables que consuman los contratos de C-05 sin renegociar JSON.
- Mantener la validación pura y testeable por separado del render, con errores localizados en el control correspondiente.
- Reutilizar el descriptor, el cliente API y el layout de C-04; agregar solo lo que falta para fechas/horas y franjas.
- Dejar los seis mockups como contrato visual inmutable para la verificación del cierre.

**Non-Goals:**

- No se modifica la API de C-05, el esquema, Flyway, la seguridad ni el router.
- No se implementa la cancelación de turnos: el botón "Cancelá el turno" existe en el mockup y en la UI, pero no dispara pedido hasta C-12.
- No se reactivan prestaciones dadas de baja, no se cargan turnos manuales (C-15) y no se muestra agenda diaria/semanal (C-13).
- No se editan los mockups archivados de C-04 ni ningún artefacto de otro change.

## Decisions

### D1 — Estructura y responsabilidades en `features/config`

| Archivo nuevo | Responsabilidad |
|---|---|
| `modelos.ts` | Tipos `Franja`, `Horario`, `Prestacion`, `Bloqueo`, `TurnoAfectado` y los resultados de validación. Todos `readonly`, sin `any`. |
| `contratos.ts` | Funciones de lectura de `unknown` → DTO validado, con mensajes de error comprensibles si la API cambia de forma. `pedir<T>` se llama con `T = unknown` y se estrecha acá. |
| ` validadores.ts` | Funciones puras: `validarFranjas`, `validarDuracion`, `validarRangoBloqueo`. Devuelven un mapa `campo → mensaje` en voseo, sin tocar el DOM ni la fecha actual. |
| `franjas.ts` | Mapeo entre la lista plana de la API y la vista semanal: orden por `diaSemana`/`inicio`, agrupación en 7 días y aplanado inverso para el `PUT`. |
| `instantes.ts` | Convierte fecha local `YYYY-MM-DD` + `HH:mm` en ISO con offset de `America/Argentina/Buenos_Aires`, y viceversa para mostrar. Se apoya en `shared/fechas.ts`. |
| `api-config.ts` | Único lugar que conoce las rutas `/api/horarios`, `/api/prestaciones` y `/api/bloqueos`; delega el transporte al `cliente` de C-04. Ningún cuerpo incluye `odontologoId`. |
| `errores.ts` | Traduce el `ErrorApi` del cliente a alertas y errores por campo, con el `mensaje` del servidor y sin interpretar HTML. |
| `vistas/horarios.ts`, `vistas/prestaciones.ts`, `vistas/bloqueos.ts` | Constructores de `Descriptor` y manejadores de acciones, con el mismo patrón `alCambiar` → ` adaptador.montar` que usa `features/auth/login.ts`. |
| `config.ts` | Fachada que arma la pantalla `/config` con las tres secciones, coordina la selección de sección y expone `descriptor()` y `manejadores`. |

Alternativa descartada: una única vista grande con todo el estado mezclado. La separación por sección permite testear validadores y mapeos sin DOM y evita que un error en bloqueos rompa el formulario de horarios.

### D2 — Estado, render y errores

Cada sección guarda su estado en un closure con forma de unión discriminada: `cargando | listo | guardando | error`. `alCambiar` vuelve a montar el descriptor con los mismos manejadores; los botones de envío quedan deshabilitados con `aria-busy` mientras hay un pedido en curso, igual que en el login.

El error del backend se traduce en un único punto (`errores.ts`):

| `codigo` |HTTP típico| Presentación |
|---|---|---|
| `RN-AG-01` | 422 | Alerta de error + marca en `franjas[i]` usando `detalles`, con el `mensaje` del servidor. |
| `RN-AG-03` | 422 | Mensaje junto al campo `duracionMinutos`. |
| `VALIDACION`, `SOLICITUD_INVALIDA` | 400 | Alerta general y, si `detalles` trae `campo`, error en ese control. |
| `NO_ENCONTRADO` | 404 | "No encontramos ese recurso." — mismo texto para ajeno e inexistente. |
| `NO_AUTENTICADO`, `ACCESO_DENEGADO` | 401/403 | No se reinterpretan como datos inválidos: el cliente ya se ocupa de la sesión; se muestra un aviso neutro y se ofrece reingresar. |
| `SIN_CONEXION`, `ERROR_INTERNO` | — | Aviso de reintento sobre la sección afectada. |

Todo `mensaje` y todo dato del servidor se insertan con `textContent` a través del adaptador; nunca con `innerHTML`. Si `detalles` no trae un campo reconocido, se muestra solo la alerta general, sin inventar una ubicación.

### D3 — Fechas, horas y zona horaria

- Las franjas son horas locales `HH:mm` sin fecha: se envían tal cual y se ordenan como texto, porque `HH:mm` con cero a la izquierda es comparable.
- Los bloqueos son instantes: se construyen en `instantes.ts` con el offset vigente en `America/Argentina/Buenos_Aires` para esa fecha (`-03:00` en la ventana de la demo), nunca con `new Date().toISOString()` ni con la zona del navegador. Se testean fecha corriente, cruce de mes/año y el valor límite de 23:59.
- El listado de bloqueos y sus `turnosAfectados` se muestran con `horaArgentina`/`rangoHorario` sobre el instante con offset recibido. El servidor sigue siendo la fuente de la hora (RN-GL-02): la UI solo presenta.

### D4 — Cancelación de turnos afectados: acción visible, sin efecto

El mockup y la UI muestran "Cancelá el turno" junto a cada elemento de `turnosAfectados`, junto al aviso "Crear un bloqueo no cancela tus turnos." El manejador de esa acción NO llama a la API en este change: muestra un aviso que indica que la cancelación se habilita con la gestión de turnos del odontólogo (C-12) y deja el turno en su estado original. Así se cumple US-004 sin duplicar el contrato `POST /api/turnos/{id}/cancelar` que aún no existe.

### D5 — Presentación responsive y mockups

Se reutilizan el app bar, la navegación y el salto al contenido de C-04 sin cambios. En escritorio (1440×900) las franjas se disponen en columnas por día y los listados en tabla; en móvil (360×844) cada día y cada registro pasa a una sola columna, con las mismas etiquetas y acciones. El modal de confirmación de baja no existe en `components.html` para este uso: se deriva del `modal` de `shared/ui/componentes.ts` con estilos de tokens, igual criterio que C-04 derivó sus estados. Ningún valor visual se escribe a mano: solo `var(--...)` de `tokens.css`, `0`, `1px` de borde, porcentajes y medidas de viewport.

Los mockups son entrados en la fase de propuesta y se congelan: **no se editan durante el apply**. Si el apply encuentra un detalle visual distinto, se documenta como desvío y se pide aprobación; no se reescribe el mockup para que coincida con la implementación. La UI real respeta literalmente los textos visibles, el orden de los bloques y los estados representados.

### D6 — Estrategia de tests (TDD estricto)

Cada test se escribe primero y se ve fallar (RED) antes de producir código (GREEN), se amplía con un caso borde (TRIANGULACIÓN) y se limpia sin cambiar el comportamiento (REFACTOR). Sin `any`: bajo `tsconfig` strict se usa `unknown` y estrechamiento explícito.

- `validadores.test.ts`: franja invertida, igualdad de horas, solapes y contiguos, día 0/8, duración 0/20/30/45/60, rango de bloqueo invertido y mensajes en voseo.
- `franjas.test.ts`: agrupación por los 7 días ISO, orden por inicio, días vacíos, aplanado inverso y round-trip con la respuesta de la API.
- `instantes.test.ts`: armar y mostrar instantes con offset en fines de mes/año y 23:59, sin depender de la zona del equipo de desarrollo.
- `contratos.test.ts`: respuesta bien formada, DTO con tipos inesperados, `detalles` ausente y `turnosAfectados` vacío o poblado.
- `config.test.ts`: carga de las tres secciones, error de red, rechazo 422 con foco en `franjas[i]`, baja confirmada/cancelada, alta de bloqueo con turnos afectados, y que "Cancelá el turno" no emita pedido.
- `main.config.test.ts`: `/config` monta la configuración real con `aria-current="page"`, `/agenda` sigue con marcador y la navegación no recarga.
- Verificación visual: cada pantalla real se compara contra su mockup aprobado del mismo viewport en C-16; las diferencias se reportan, no se autoaprueban.

## UI Mockups

- Design system: revisión 2, aprobada el 2026-11-17 (`design-system/README.md`).
- Estado: **APROBADOS por el usuario en revisión 2 (2026-11-22)**. Los seis archivos fueron aprobados explícitamente; el apply de C-06 puede arrancar respetando su inmutabilidad.
- Regla de inmutabilidad: una vez aprobados, los mockups **no se editan durante el apply**. La UI real respeta sus textos visibles, estados y orden de información; cualquier diferencia se documenta y se somete a aprobación.
- Viewports: escritorio 1440×900 y móvil 360×844 (ancho mínimo exigido por el proyecto para las pantallas del odontólogo).

| Archivo | Pantalla y viewport | Estados representados |
|---|---|---|
| `mockups/horarios-escritorio.html` | Horario semanal · 1440×900 | Franjas múltiples por día, día sin atención, franjas solapadas rechazadas con error en `franjas[i]` y guardado correcto. |
| `mockups/horarios-movil.html` | Horario semanal · 360×844 | Misma información en una columna, día sin franjas y error de franjas solapadas visible sin desplazamiento horizontal. |
| `mockups/prestaciones-escritorio.html` | Prestaciones · 1440×900 | Listado con activas e inactivas, alta/edición, duración inválida (20 min, `RN-AG-03`) y prestación dada de baja. |
| `mockups/prestaciones-movil.html` | Prestaciones · 360×844 | Misma lista y formulario en una columna, error de duración y distintivo "Dada de baja". |
| `mockups/bloqueos-escritorio.html` | Bloqueos · 1440×900 | Alta con rango fecha-hora y motivo, bloqueo con turnos afectados y acción "Cancelá el turno", aviso de que no se cancelan automáticamente. |
| `mockups/bloqueos-movil.html` | Bloqueos · 360×844 | Rango en una columna, lista de turnos afectados con estado y acción visible, y rango invertido rechazado. |

## Risks / Trade-offs

- **R1** [El mockup no llega a aprobarse antes del 25/11] → es gate de la política visual: se aprueba en lote con los mockups de C-10/C-11/C-13; si no hay aprobación, el apply se detiene y se reporta el bloqueo, nunca se saltea.
- **R2** [La API cambia de forma y `pedir<T>` acepta un DTO incorrecto por el cast] → validación de `unknown` en `contratos.ts` con tests de tipos inesperados; no se confía en el cast de C-04.
- **R3** [Offset de zona mal calculado en un cambio de horario] → `instantes.ts` calcula el offset según la fecha, con tests en bordes; el servidor sigue siendo dueño de la interpretación (RN-AG-06/RN-GL-02).
- **R4** [El odontólogo cree que cancelar desde un bloqueo ya ejecuta la cancelación] → texto explícito en el aviso y en la acción; el manejador no envía pedido hasta C-12.
- **R5** [El reemplazo semanal completo borra franjas por un borrador incompleto] → el borrador se inicializa desde el `GET` y solo se sobreescribe con la respuesta de un `PUT` exitoso; ante error se conserva lo editado.
- **R6** [Diferencias entre mockup y UI real en la verificación de C-16] → se registran como desvíos con capturas; no se editan los mockups aprobados ni se aprueban diferencias por defecto.

## Migration Plan

1. Rama `feat/c-06-configuracion-agenda-ui`, creada desde `origin/main` con C-04 y C-05 archivados. Sin migraciones de base ni cambios de configuración.
2. Aprobar los seis mockups; recién después arrancar el apply en orden: modelos/validadores/mapeos (RED→GREEN) → contratos e instantes → vistas → cableado de `/config` en `main.ts` → tests de integración de la vista → verificación visual.
3. Rollback: revertir los archivos de `features/config` y el despacho de `/config` en `main.ts`; el marcador de C-04 vuelve a mostrarse y la API queda intacta.
4. Después del merge, avisar a C-16 que la verificación visual de las tres pantallas (escritorio y móvil) debe hacerse contra los mockups aprobados de este change.

## Open Questions

Ninguna que cambie specs, enfoque o tareas. La habilitación real de "Cancelá el turno" pertenece a C-12 y está explícitamente fuera de este change.
