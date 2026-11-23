# Design

## Context

Ver `proposal.md` — Why. C-09 (`gestion-turno-token-api`) y C-10 (`reserva-publica-ui`) están archivados en `main`. C-09 fija el contrato en su `design.md` (D1, D2 y D5): `GET /api/public/turnos/{token}` devuelve `inicio`, `fin`, `estado`, `prestacion {id, nombre}`, `odontologo {slug, nombre, apellido, direccionConsultorio}`, `puedeCancelar` y `puedeReprogramar`; `cancelar` y `reprogramar` responden 204 sin cuerpo, y la tabla de errores define el orden de los rechazos.

En el frontend, C-10 dejó `features/reservar/` (cliente público, máquina de estados pura y pantalla por descriptores), `shared/selector-horarios.ts` y los estilos públicos acotados a `.reserva-publica` en `estilos/app.css`. El router ya resuelve la ruta `turno` (`/turno/{token}`), que hoy muestra `vistaMarcador`. TypeScript `strict` + `noUncheckedIndexedAccess`, Vitest en entorno `node`, sin framework de UI.

Restricciones: presupuesto cero, voseo, `textContent` para todo dato, tokens del design system, demo el 2026-11-25 y mockup aprobado antes del apply.

## Goals / Non-Goals

**Goals:**
- Implementar `/turno/{token}`: consulta, cancelación con confirmación, reprogramación y solo lectura, con los rechazos de C-09.
- Reutilizar sin duplicar la navegación de días y el selector de horarios de C-10.
- Dejar el flujo como módulos puros testeables en `node`.

**Non-Goals:**
- No modificar endpoints, DTOs, reglas ni headers del backend.
- No recibir, mostrar ni redirigir al link nuevo de la reprogramación.
- No cambiar el comportamiento de `/reservar/{slug}`.
- No agregar dependencias de DOM para tests ni librerías de fechas.

## Decisions

### D1 — Módulos de `features/turno`

| Módulo | Responsabilidad |
|---|---|
| `features/turno/modelo.ts` | Tipos: `TurnoGestion`, `EstadoGestion`, `AccionGestion`, vista (`activo`, `confirmar-cancelacion`, `reprogramando`, `solo-lectura`, `error-link`). |
| `features/turno/api-gestion.ts` | `consultar(token)`, `cancelar(token)`, `reprogramar(token, inicio)` con `fetch` inyectable; guardas sobre `unknown` y normalización con `interpretarErrorApi`. Reusa `perfil` y `disponibilidad` de `features/reservar/api-publica.ts`. |
| `features/turno/estado.ts` | Máquina de estados pura `(estado, accion) => estado`. |
| `features/turno/pantalla-turno.ts` | Render por descriptores y wiring; mismo patrón que `pantalla-reserva.ts`. |
| `shared/navegador-dias.ts` | Navegación por semana, "Ir a una fecha" y tira de días, extraída de `pantalla-reserva.ts` (D3). |

El token se valida con la misma forma que C-09 (43 caracteres base64url) antes de llamar a la API; si no cumple, la pantalla muestra el 404 genérico sin hacer la solicitud. El token vive solo en el cierre del control de la pantalla: no se guarda en `localStorage`, `sessionStorage`, el estado serializable ni los mensajes de error.

Alternativa descartada: meter la gestión dentro de `features/reservar/`. Mezcla dos pantallas con estados y reglas distintas y vuelve frágil el archivo más grande del frontend.

### D2 — Máquina de estados

Estados: `cargando` → `activo` | `solo-lectura` | `error-link` | `error-carga`. Desde `activo`: `confirmar-cancelacion` → `cancelando` → `cargando` (relectura); `reprogramando` (cargando perfil y disponibilidad) → `confirmando-cambio` → `enviando-cambio` → `cargando`.

- La vista se decide con el GET: `puedeCancelar || puedeReprogramar` → `activo`; si no, `solo-lectura`. La marca "Pasado" sale de `estado` y de comparar `fin` con el instante del servidor implícito en `puedeCancelar: false`; la UI no usa el reloj del navegador para habilitar acciones.
- Después de un 204, la acción guarda un `resultado` (`cancelado` o `reprogramado` con el horario elegido) y relee el turno. La vista de solo lectura usa ese `resultado` para el título ("Turno cancelado" / "Cambiaste tu turno") y el turno releído para el detalle.
- `RN-TU-07`, `RN-SE-02` y 404 en una acción → aviso + relectura, sin reintento.
- `RN-TU-01` → aviso + recarga de la disponibilidad del día, conservando día y semana; limpia solo el horario elegido.
- `enviando` bloquea una segunda acción del mismo tipo (doble toque).

### D3 — Navegación de días compartida

`shared/navegador-dias.ts` exporta funciones puras: `primeraSemana(fechaMinima)`, `semanaDe(ventana, fecha)` (hoy `paginaDe`), `moverSemana(ventana, inicio, ±1)` y `navegadorDias({ ventana, inicioSemana, seleccionada, diaMarcado?, etiqueta })`, que devuelve el `Descriptor` con las mismas clases y acciones que usa C-10 (`semana-anterior`, `semana-siguiente`, `salto-fecha`, `dia:{fecha}`). `diaMarcado` agrega `day--actual` y la leyenda "Día de tu turno actual".

`pantalla-reserva.ts` pasa a usar este módulo sin cambiar su salida: los tests existentes de C-10 son la red de seguridad y deben pasar sin modificar sus expectativas. Si algún test de C-10 necesita cambiar, el apply se detiene y se consulta.

Alternativa descartada: copiar `diasDescriptor` en C-11. Dos copias del cálculo de la ventana divergen con el primer ajuste y el mockup aprobado exige que se vean iguales.

### D4 — Fechas, horas y formato

- Ventana de reprogramación: `fechaMinima` y `fechaMaxima` de `GET /api/public/{slug}` con `odontologo.slug` del turno; nunca se calculan con el reloj del cliente (RN-GL-02).
- Fecha y hora del turno: se formatean desde `inicio`/`fin` con `Intl.DateTimeFormat('es-AR', { timeZone: 'America/Argentina/Buenos_Aires' })`, reusando los helpers de formato de C-10 (`formatearFechaLarga`, `horaDeInicio`), movidos a `shared/` si hace falta compartirlos.
- El `inicio` elegido se envía con el texto exacto que devolvió la disponibilidad.
- Primera semana mostrada: la que contiene el día del turno actual si cae dentro de la ventana; si no, la de `fechaMinima`.

### D5 — Mensajes por código

Textos propios de la UI por `codigo` (decisión 3 del mockup), tomados de `mockups/estados.html`, sin mostrar el `mensaje` crudo:

| Situación | Código | Reacción |
|---|---|---|
| Link inválido | `NO_ENCONTRADO` (GET) | "No encontramos este turno", sin acciones |
| Ya empezó / ya no está reservado | `RN-TU-07` / `RN-SE-02` | "No pudimos hacer el cambio" + relectura en solo lectura |
| No se puede reprogramar | `RN-SE-04` | Aviso + volver al turno (cancelar sigue disponible) |
| Consultorio o prestación inactivos | `NO_ENCONTRADO` (acción) | "No pudimos hacer el cambio" + relectura |
| Horario tomado | `RN-TU-01` | "Ese horario acaba de ser tomado" + refresco y selección limpia |
| Anticipación / horizonte / agenda | `RN-TU-03` / `RN-TU-11` / `RN-AG-05` | "Ese horario ya no está disponible" + refresco y selección limpia |
| Solicitud inválida | `VALIDACION` / `SOLICITUD_INVALIDA` | Aviso genérico + elegir otro horario |
| Red / 500 / forma inesperada | cualquiera | "No pudimos conectarnos" + "Reintentar", conserva la selección |

Un error de red se distingue de un 404 del token: el 502/sin conexión nunca muestra "No encontramos este turno" (en C-10 se vio ese cruce sin backend; acá queda cubierto por test).

### D6 — Render, accesibilidad y estilos

- Raíz `div.reserva-publica.gestion-turno` para heredar los estilos públicos acotados de C-10 (appbar, `card`, `btn`, `alerta`, días y chips) sin pisar las pantallas del odontólogo. Las piezas nuevas del mockup (hoja/diálogo de confirmación, `btn--danger-solid`, comparación "Antes / Nuevo", badge "Pasado", punto del día actual, aviso "Este link es personal") se agregan en `app.css` bajo `.gestion-turno`, solo con tokens. Los literales en píxeles del mockup (64 px, 6 px) se traducen a tokens.
- Confirmación de cancelación (según `cancelar-confirmacion.html`): `role="alertdialog"`, `aria-modal`, foco inicial en "No, volver"; `Escape` y "No, volver" cierran y devuelven el foco a "Cancelar turno". Como el adaptador solo escuchaba click/submit/change, se agregó al `Descriptor` el campo opcional `teclas` (`{ Escape: 'cerrar-cancelacion' }`), que el adaptador atiende con `keydown`.
- `aria-live="polite"` para el resultado de una acción y `role="alert"` para rechazos.
- Foco al título de cada vista al cambiar; textos con voseo; objetivos de 44 px; sin desborde a 360 px.

### D7 — Testing

Vitest en `node`, sin DOM:
- `api-gestion`: 200 con forma válida e inválida, 204 de cada POST, 404, 409 (`RN-TU-07`, `RN-SE-02`, `RN-SE-04`, `RN-TU-01`), 422, 400, 500 y red; el cuerpo de `reprogramar` conserva el texto de `inicio`; ninguna URL sale de `/api/public/`.
- `estado`: camino feliz de cancelar y reprogramar, arrepentirse, doble envío, relectura después de 204, conflicto que conserva día y semana, y cada rechazo de D5.
- `navegador-dias`: bordes de la ventana, salto por fecha, día marcado; y los tests de C-10 sin cambios.
- `pantalla-turno`: descriptores por vista (acciones según permisos, solo lectura, confirmación) con el adaptador de prueba de C-10.

## UI Mockups

- Design system revision: 2
- Decision: yes (2026-11-22)
- Mockups: `openspec/changes/c-11-gestion-turno-ui/mockups/` ([README](mockups/README.md), capturas en [previews/](mockups/previews/))
- Approval: approved (2026-11-22, by user) — incluye la revisión 2 de `reprogramar.html` (navegación de días alineada con C-10).
- Preview: rendered (2026-11-22, 390×844 y 1440×900)
- Visual verify: passed con API simulada (2026-11-22, `mockups/verify/`): turno activo, confirmación, reprogramar, horario tomado y solo lectura (cancelado y reprogramado) a 390×844 y 1440×900, sin desborde ni errores de consola; foco inicial en "No, volver" y `Escape` devuelve el foco a "Cancelar turno". `/reservar/demo` se ve igual que antes del refactor. Diferencias menores aceptadas: la semana arranca en `fechaMinima` (como C-10) y los días sin agenda los decide el servidor. Recorrido contra el backend real en perfil `demo` (2026-11-22): reserva, cambio de horario, link viejo, link nuevo y cancelación con emails reales; ver tareas 6.3 y 6.4.

Pantallas: `turno-activo.html`, `cancelar-confirmacion.html`, `reprogramar.html`, `solo-lectura.html` y `estados.html`. Las notas `.nota-mockup` no van a la UI real. **Gate**: superado; un cambio posterior a los mockups vuelve la aprobación a pending.

## Risks / Trade-offs

- **Refactor de C-10 a días antes de la demo** → regresión en la reserva. Mitigación: extracción pura, tests de C-10 sin cambios como red y control visual de `/reservar/demo` en el verify.
- **Estilos públicos atados a `.reserva-publica`** → nombre engañoso para la gestión. Mitigación: se reutiliza la clase como ámbito público y se anota en el design; renombrarla a un ámbito neutro queda para después de la demo.
- **Relectura después de 204** → si el GET falla, el paciente no ve el resultado. Mitigación: el `resultado` local ya permite mostrar "Turno cancelado"/"Cambiaste tu turno" y un "Reintentar" para releer.
- **Dos requests para reprogramar (perfil + disponibilidad)** → más latencia en celular. Mitigación: skeleton del selector y el perfil se pide una sola vez por visita.
- **Token en la URL** → historial y capturas del paciente. Mitigación: lo que controla la app (no guardarlo, no loguearlo, `no-referrer`); el resto está fuera del alcance de la UI (D5 de C-09).
