# Design

## Context

Ver `proposal.md` — Why. El estado actual del frontend es mínimo: solo `frontend/src/main.ts` y `frontend/src/shared/error-api.ts`. El shell de C-04 (router, `api-client`, utilidades de fechas y componentes base) todavía figura `[ ]` en `CHANGES.md`, y C-07 ya expone los tres endpoints públicos con DTOs estables (`PerfilPublico`, `DisponibilidadPublica`, `ReservaConfirmada`). El frontend corre con TypeScript `strict` + `noUncheckedIndexedAccess` y Vitest en entorno `node`; no hay framework de UI ni dependencias de runtime.

Restricciones que mandan: presupuesto cero (sin librerías nuevas), textos en español rioplatense, `textContent` para todo dato de usuario/API, tokens del design system y un mockup aprobado antes del apply.

## Goals / Non-Goals

**Goals:**
- Implementar el flujo público completo (perfil → prestación → fecha/horario → datos → éxito) consumiendo los contratos de C-07.
- Dejar la lógica de flujo, validación y armado de la solicitud como módulos puros y testeables en Vitest sin DOM.
- Entregar un selector de horarios reutilizable por C-11 y C-15.
- Cubrir con mensajes claros todos los rechazos que la API ya define.

**Non-Goals:**
- No implementar el shell de C-04 ni el router: C-10 se monta sobre ellos.
- No modificar endpoints, DTOs, migraciones ni reglas del backend.
- No mostrar ni manejar el token de gestión (llega por email; lo usa C-11).
- No agregar framework de UI, gestor de estado, librería de fechas ni de validación.
- No probar el DOM con jsdom/happy-dom: se testean módulos puros en el entorno `node` existente.

## Decisions

### D1 — Módulos de `features/reservar` y contrato con C-04

Se separa la feature en módulos con una única responsabilidad:

| Módulo | Responsabilidad |
|---|---|
| `features/reservar/api-publica.ts` | Llamadas tipadas a los tres endpoints y validación de la forma del JSON con guardas (`unknown` → tipo propio). |
| `features/reservar/modelo.ts` | Tipos de la feature (`PerfilReserva`, `DiaReservable`, `HorarioOpcion`, `DatosPaciente`, `EstadoReserva`). |
| `features/reservar/validaciones.ts` | Validadores puros de DNI, teléfono E.164, email, nombre y apellido, con mensajes por campo. |
| `features/reservar/estado.ts` | Máquina de estados pura: acciones y transiciones del flujo. |
| `features/reservar/solicitud.ts` | Armado del cuerpo del `POST` a partir de la selección y los datos, sin normalizar el `inicio`. |
| `features/reservar/pantalla-reserva.ts` | Render de la página y wiring de eventos; usa solo `textContent` y `createElement`. |
| `shared/selector-horarios.ts` | Componente reutilizable: recibe horarios, seleccionado y callback; no conoce la API. |

C-10 **no** crea router, `api-client` ni componentes base: consume los de C-04. Si la forma final de esos módulos difiere de lo previsto, se adapta la feature sin cambiar la spec. Las tareas arrancan con una verificación de C-04; si no existe, el apply se detiene.

Alternativa descartada: implementar la página dentro de `main.ts` con HTML por `innerHTML`. Se descarta por seguridad (XSS), por la regla de `textContent` y porque impide reutilizar el selector en C-11/C-15.

### D2 — Máquina de estados explícita y sin dependencias

Estados: `cargando-perfil` → `eligiendo-prestacion` → `eligiendo-horario` → `completando-datos` → `enviando` → `exito`, con ramas `error-perfil`, `sin-horarios`, `conflicto` y `error-envio`. Cada transición es una función pura `(estado, accion) => estado`.

- `elegirPrestacion` limpia fecha/horario y dispara la carga de disponibilidad.
- `elegirFecha` y `elegirHorario` avanzan el paso sin perder datos previos.
- `confirmar` solo es válido desde `completando-datos` con validación local en verde.
- `conflicto` conserva `datosPaciente`, vuelve a `eligiendo-horario` con la disponibilidad marcada para refresco.
- `exito` es terminal; el botón "Reservar otro turno" reinicia a `eligiendo-prestacion`.

Así los tests cubren transiciones, doble envío y conservación de datos sin tocar el DOM.

### D3 — Fechas y horas: el servidor manda

- La ventana reservable sale de `fechaMinima` y `fechaMaxima` del perfil; la UI nunca calcula "hoy + 24 h" ni "hoy + 60 días" con el reloj del cliente (RN-GL-02).
- La tira de días se arma con `fechaMinima` como piso y se navega de a 7 días hasta `fechaMaxima`; además hay un `input type="date"` con `min`/`max` para saltar. No se ofrecen fechas fuera de la ventana.
- El label del horario usa el campo `hora` (`HH:mm`) que ya devuelve la API; el `inicio` con offset se guarda tal cual y se envía sin re-serializar, para no reinterpretar la zona.
- El nombre del día y la fecha se formatean con `Intl.DateTimeFormat('es-AR', { timeZone: 'America/Argentina/Buenos_Aires' })` a partir de las partes de `YYYY-MM-DD`; nunca con `new Date('YYYY-MM-DD')` seguido de métodos locales.

Alternativa descartada: calcular la disponibilidad o filtrar horarios en el cliente. Duplicaría reglas del backend y podría mostrar horarios que la reserva después rechaza.

### D4 — Validación local espejo, no autoridad

`validaciones.ts` replica los formatos de `SolicitudReserva` (DNI `^[0-9]{7,8}$`, teléfono `^\+[1-9][0-9]{7,14}$`, email con la misma forma general, nombre/apellido no vacíos y ≤ 100) para dar feedback inmediato. El envío siempre va al servidor y se muestran los `detalles` de un 400 `VALIDACION` mapeados por `campo`. Si una regla cambia en el backend, la UI sigue obedeciendo al servidor.

### D5 — Manejo de errores por código

`api-publica.ts` normaliza toda respuesta no exitosa con `interpretarErrorApi` y expone `{ estado, codigo, mensaje, detalles }`. La pantalla decide por `codigo`, no por texto:

| Situación | Código | Reacción |
|---|---|---|
| Horario tomado | `RN-TU-01` (409) | Mensaje "Ese horario acaba de ser tomado, elegí otro", refresca disponibilidad y conserva datos |
| Límite de turnos | `RN-PA-03` (409) | Explica el límite de 2 y cómo liberar uno desde el link del email |
| Menos de 24 h | `RN-TU-03` (422) | Explica la anticipación mínima y ofrece otro horario |
| Fuera del horizonte | `RN-TU-11` (422) | Explica el máximo de días y ofrece otra fecha |
| Fuera de agenda | `RN-AG-05` (422) | Explica que el horario dejó de estar disponible y refresca |
| Validación | `VALIDACION` (400) | Errores por campo desde `detalles` |
| No encontrado | `NO_ENCONTRADO` (404) | Mensaje genérico y recarga del perfil |
| Red / 500 | cualquiera | Mensaje genérico con "Reintentar", conserva datos |

Nunca se reintenta una escritura automáticamente. El doble toque se evita con el estado `enviando` y el botón deshabilitado.

### D6 — Render seguro y accesible

- Estructura construida con `createElement`/`textContent`; el único HTML estático es el markup fijo de la pantalla (sin interpolar datos).
- `fieldset`/`legend` para prestación y horarios; `aria-pressed` para opciones y chips; `aria-invalid` + `aria-describedby` para errores; `aria-live="polite"` para el resultado de la reserva y `role="alert"` para el conflicto.
- Al cambiar de paso se mueve el foco al título del paso; el error de un campo recibe foco cuando impide el envío.
- Layout de una columna con `--container-narrow`, tokens de `tokens.css`, controles de `--control-height` (44 px) y sin desborde horizontal a 360 px.
- Los textos usan voseo ("Elegí un horario", "Confirmá tu reserva", "Revisá tus datos").

### D7 — Selector de horarios reutilizable

`shared/selector-horarios.ts` recibe `{ horarios, seleccionado, onSeleccionar, vacio }` y devuelve el nodo. No sabe de slugs, prestaciones ni fetch. C-11 lo reutiliza para reprogramar (misma prestación) y C-15 para el turno manual; por eso el estado vacío y el mensaje de error son parámetros, no texto cableado.

### D8 — Testing sin DOM

Se testean en `node` con Vitest: validadores (casos válidos e inválidos por campo), armado de solicitud (no re-serializa `inicio`, incluye `prestacionId` numérico), máquina de estados (camino feliz, conflicto conserva datos, doble envío, error de red) y `api-publica` con `fetch` inyectado (201, 409, 400, 404, JSON inesperado). No se agregan dependencias de DOM; si en el futuro se quiere probar render, se evalúa `jsdom` en un change aparte.

## UI Mockups

Política `system-and-mockups`, revisión 2. Antes del apply hay que crear y aprobar:

- **Ubicación**: `openspec/changes/c-10-reserva-publica-ui/mockups/`.
- **Archivos**: `reserva-seleccion.html`, `reserva-formulario.html`, `reserva-exito.html`, `reserva-conflicto.html` y `mockup.css` (estilos compartidos, solo con tokens).
- **Estado**: creados el 2026-11-22 y **aprobados por el usuario el 2026-11-22**; verificados a 360 px y 1440 px sin overflow horizontal.
- **Tamaños de referencia**: 390×844 y 1440×900 (capturas o vista responsive en el mismo HTML).
- **Base**: importan `design-system/tokens.css`, `design-system/brand.js` y `design-system/assets/logo.svg`; replican los patrones de `components.html` (opciones, tira de días, chips, alertas, resumen, estado vacío).
- **Estados cubiertos**: perfil con prestaciones, cargando, día sin horarios, error de red, link inválido, formulario con error por campo, envío, error de validación del servidor, resumen previo, éxito con email, conflicto `RN-TU-01`, límite `RN-PA-03`, `RN-TU-03`, `RN-TU-11`, `RN-AG-05` y vista a 360 px.
- **Gate**: sin aprobación explícita del usuario sobre estos mockups no se ejecuta el apply.

## Risks / Trade-offs

- **C-04 ausente** → el apply queda bloqueado. Mitigación: primera tarea del change verifica el shell; si falta, se detiene y se retoma cuando C-04 esté archivado. No se duplica el shell acá.
- **Contrato de C-04 incierto** (nombres de `api-client`, router y helpers de fecha) → acoplamiento. Mitigación: la feature depende de una superficie mínima (fetch tipado, navegación y formato de fecha) y las tareas incluyen adaptar el wiring sin cambiar la spec.
- **Duplicar validación backend/frontend** → deriva si cambian las reglas. Mitigación: la UI muestra siempre los `detalles` del servidor y la validación local es solo feedback temprano.
- **Ventana de 60 días en la tira de días** → demasiados nodos. Mitigación: paginación de 7 días + `input type="date"`; nunca se renderizan los 60 de una.
- **Datos del paciente conservados tras conflicto** → riesgo de asociarlos a un horario que ya no está. Mitigación: al refrescar se limpia solo el horario seleccionado y se exige elegir uno nuevo antes de confirmar.
- **Sin tests de DOM** → regresiones visuales no detectadas por Vitest. Mitigación: mockup aprobado + checklist visual en C-16; los módulos con lógica quedan cubiertos.
