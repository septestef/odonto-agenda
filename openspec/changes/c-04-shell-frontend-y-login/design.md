# Design — C-04 `shell-frontend-y-login`

## Contexto

El frontend que dejó C-01 es apenas un `<main id="app">` con texto estático: no hay enrutamiento, cliente HTTP, estilos ni pantallas. Este change construye el esqueleto que consumen C-06, C-10, C-11, C-13, C-14 y C-15. La motivación y el alcance están en [proposal.md](proposal.md); los comportamientos observables en [specs/](specs/).

Restricciones que concretan el enfoque:

- TypeScript vanilla + Vite 8 + Vitest 5, Node 24. **Sin `any`**, sin dependencias nuevas, sin `jsdom` y sin librería de fechas. El `frontend/tsconfig.json` (líneas 18-24) ya viene con `strict`, `noUncheckedIndexedAccess`, `verbatimModuleSyntax`, `erasableSyntaxOnly`, `noUnusedLocals` y `noUnusedParameters`: no se usa `enum`, los imports de tipos van con `type` y ningún index se lee sin chequear que exista.
- Spring sirve el build de Vite como estático en el mismo origen; ya existe el fallback a `index.html` para rutas profundas. No se configura CORS.
- La política visual del proyecto es `system-and-mockups`, revisión 2: `design-system/tokens.css`, `design-system/brand.js` y `design-system/components.html` mandan por encima de cualquier otra guía de diseño. Los tres mockups de este change están aprobados y no se editan.
- El contrato de autenticación lo implementa C-03 en otra rama. Se leyó **sin modificarla** con `git show origin/feat/c-03-auth-sesion-odontologo:<path>`; no se hizo checkout, merge ni rebase.

## Objetivos y no objetivos

**Objetivos:**

- Dejar una tabla de rutas y un guard de sesión con una única fuente de verdad para las cuatro rutas de funcionalidad.
- Tener un cliente HTTP que resuelva CSRF, errores tipados y sesión vencida en un solo lugar.
- Proveer utilidades de fecha puras, testeables y explícitamente ancladas a `America/Argentina/Buenos_Aires`.
- Construir el layout y los componentes base con los tokens aprobados, sin HTML dinámico.
- Verificar todo lo anterior con Vitest corriendo en `node`, sin librerías de DOM.

**No objetivos (decisión de diseño, no alcance del proposal):**

- No se calcula la expiración de la sesión en el navegador; el aviso del mockup es ilustrativo y C-04 no lo implementa como regla.
- No se intenta recuperar sesiones ni reintentar pedidos automáticamente ante 401 o 403.
- No se construye lógica de agenda, turnos, configuración ni reserva: las cuatro rutas muestran un marcador.
- No se modifica el backend ni se ejecuta el smoke E2E del login dentro de este change.

## Decisiones

### 1. Router propio sobre la History API

Se implementa una tabla de rutas declarada con pattern matching simple para los parámetros `{slug}` y `{token}`, usando `history.pushState`, `replaceState` y el evento `popstate`. Cada ruta resuelve a un presentador puro que devuelve un descriptor; un adaptador inyectado convierte ese descriptor en nodos. La navegación nunca emite una carga de documento completo.

**Por qué:** el producto es vanilla TS y el roadmap prevé seis pantallas más; una tabla explícita es predecible y testeable sin DOM. El fallback a `index.html` de Spring ya resuelve los deep links, así que no hace falta un servidor de desarrollo con rewrite especial.

**Alternativas descartadas:** una librería de router (dependencia nueva y acoplamiento a un framework); hash routing (URLs feas y no coincide con los links de email `/turno/{token}`).

La decisión del guard es una función pura: recibe la ruta destino y el resultado de la verificación de sesión, y devuelve "mostrar", "redirigir a login con `redirect`" o "mostrar error de carga". El parámetro de destino se llama `redirect` y se valida estrictamente: solo rutas internas que empiecen con `/` y no con `//` ni con un esquema. Un destino inválido cae a `/agenda`, evitando open redirects.

### 2. Guard con `GET /api/auth/me` y caché en memoria

La cookie `JSESSIONID` es `HttpOnly`, así que el frontend no puede leerla ni decodificarla. El guard consulta `/api/auth/me` una vez y guarda el `OdontologoDto` en memoria. Esa caché se limpia ante 401 (`NO_AUTENTICADO`), ante la señal de sesión vencida publicada por el api-client y al cerrar sesión. Las rutas públicas nunca consultan `/me`.

Ante un error de red se muestra la vista de error de carga con reintento: no se asume sesión vencida solo porque no hubo respuesta. La decisión pura se complementa con una fábrica stateful inyectable, `crearGuard(fetch, observador)`, que conserva la caché entre navegaciones y expone una forma de descartarla; así se testea el caché sin módulos ni singletons.

**Alternativas descartadas:** guardar un flag de sesión en `localStorage` (se desincroniza del servidor y expone información); leer la cookie (imposible por `HttpOnly`).

### 3. Cliente HTTP con CSRF por cookie y errores tipados

Un único módulo envuelve `fetch` con `credentials: 'same-origin'`. Para `POST`/`PUT`/`PATCH`/`DELETE` lee la cookie `XSRF-TOKEN` y la reenvía como `X-XSRF-TOKEN`; `GET`/`HEAD` no la envían y `/api/auth/login` está exceptuado porque la API ignora CSRF en ese endpoint.

Las respuestas de error pasan por `interpretarErrorApi` de C-01, que ya produce `{codigo, mensaje, detalles?}` y solo omite la clave cuando el valor es `undefined` (`frontend/src/shared/error-api.ts:25`). El api-client normaliza además `null`, `[]` y `{}` como "sin detalles", quitando la clave antes de exponer el error. Se agregan dos casos que C-01 no cubre: `SIN_CONEXION` para falla de red y `NO_AUTENTICADO` como señal global de sesión vencida. La señal se observa con un callback inyectado `notificarSesionVencida: () => void`, que el api-client invoca en el 401 autenticado; el guard se suscribe a ese callback y descarta su caché. El 204 de logout se trata como éxito sin parsear cuerpo. El cliente no redirige: publica la señal y el router reacciona.

**Alternativas descartadas:** Axios o un wrapper con interceptores globales (dependencia nueva y comportamiento implícito); repetir el parseo de errores en cada pantalla (divergencia con C-01).

### 4. Fechas puras con zona explícita

Toda conversión usa `Intl.DateTimeFormat` con `timeZone: 'America/Argentina/Buenos_Aires'`. Las funciones de fecha local `YYYY-MM-DD`, validación y suma/resta de días reciben el instante o el "hoy" como parámetro. La función de rango horario recibe **dos objetos `Date`** ya resueltos en la zona de negocio (convertir un string ISO a `Date` es trabajo del adaptador, no de la utilidad). No se llama al reloj del navegador dentro de la lógica; si una vista necesita "hoy", lo inyecta el adaptador delgado, solo para mostrar.

**Alternativas descartadas:** `toLocaleString()` sin zona (depende del dispositivo); `date-fns`/`luxon` (dependencia nueva); `Date` en UTC para mostrar (rompe RN-AG-06).

### 5. UI construida con nodos y tokens, nunca con HTML dinámico

Los componentes base (botón, campo, alerta, modal `<dialog>`, toasts, layout) separan un presentador puro que devuelve descriptores serializables de un adaptador delgado que los convierte con `document.createElement` y asigna texto con `textContent`. Así se puede testear la estructura sin `jsdom`: los tests verifican los descriptores y el adaptador recibe un `document` falso mínimo inyectado (`createElement`, `textContent`, `append`). Los estilos viven en `frontend/src/estilos/app.css`, que importa `design-system/tokens.css` por un alias de Vite; el nombre de marca se aplica con `design-system/brand.js` y la constante equivalente de `frontend/src/shared/brand.ts`, y el logo/favicon salen de `design-system/assets/`. No se copian valores visuales: colores, espaciados, radios, fuentes y sombras salen de variables.

Las reglas de ESLint que prohíben `innerHTML`, `outerHTML` e `insertAdjacentHTML` en `**/*.ts` se definen en un módulo `.ts` importable (`frontend/src/shared/seguridad-html.ts`) y se importan desde `frontend/eslint.config.js`: así `tsc --noEmit` también las verifica y no queda un `.js` sin tipos. Se mantiene la prohibición existente de `any`. Los literales sueltos que aparecen en los mockups (`2px`, valores de `opacity`, `letter-spacing`) son ilustrativos: en la UI real se usan variables de `tokens.css`.

**Alternativas descartadas:** template strings con `innerHTML` (riesgo XSS y violación de regla dura); copiar los tokens al CSS del frontend (duplicación y desincronización con la revisión 2 aprobada).

### 6. Tests con DOM falso inyectado, sin `jsdom`

La lógica de router, guard, api-client, fechas y presentadores de mensajes se extrae a funciones puras con dependencias inyectadas (`fetch` falso, reloj o temporizador falso, lector de cookie falso). Los adaptadores que tocan el DOM reciben un `document` falso mínimo con `createElement`, `textContent`, `append` y lo necesario para armar el árbol; Vitest sigue con `environment: 'node'` y no se instala nada.

`main.ts` no ejecuta `document.querySelector` a nivel de módulo: exporta `arrancar(app, adaptador)` y solo el punto de entrada real consulta el documento. En los tests se importa `arrancar` y se le pasan el nodo `app` y el adaptador falsos, de modo que el módulo es importable sin efectos colaterales. La verificación de que no se emite navegación de documento completo se hace con el adaptador de History inyectado; la revisión visual contra los mockups es manual y queda registrada en la tarea 7.2.

El estado de bloqueo del login se prueba con reloj/temporizador inyectado: `vi.useFakeTimers()` o la función pura `estaBloqueado(hasta, ahora)`, sin depender del reloj real.

**Alternativas descartadas:** instalar `jsdom` o `@testing-library/dom` (dependencias nuevas, además de que el proyecto explicitó no agregarlas); importar `main.ts` con efectos de módulo en los tests (rompe `environment: 'node'`).

## Contrato mínimo que C-04 consume de C-03

> Fuente: `git show` sobre `origin/feat/c-03-auth-sesion-odontologo`, commits `500f75f` (docs) y `e81bf0e` (feat). **Esa rama existe pero no está mergeada en `main` y está ramada de un `main` viejo**: el diff contra `origin/main` muestra borrados de notificaciones y de los changes C-07/C-08, así que no es seguro mergearla tal cual. Solo se leyó. Si C-03 se rebasea contra `main`, este contrato debe reverificarse antes del E2E.

### Endpoints

| Método y ruta | Autenticación | CSRF | Respuesta |
|---|---|---|---|
| `POST /api/auth/login` | pública | ignorado por la API | `200` + `OdontologoDto`; `400` `VALIDACION`; `401` `RN-AU-01`; `429` `RN-AU-03` |
| `GET /api/auth/me` | requiere `ROLE_ODONTOLOGO` | no aplica en lectura | `200` + `OdontologoDto`; `401` `NO_AUTENTICADO` |
| `POST /api/auth/logout` | requiere sesión | **requerido** | `204` sin cuerpo |

### Formatos

- Request de login: `{"email": "ana@consultorio.com", "password": "..."}`. El servidor hace `strip()` del email; valida email y contraseña de hasta 72 **bytes** UTF-8.
- `OdontologoDto` (respuesta de login y `me`): `{ "id": number, "nombre": string, "apellido": string, "email": string, "slug": string }`. Nunca incluye hash de contraseña.
- Error uniforme: `{ "codigo": string, "mensaje": string, "detalles"?: unknown }`. C-01 (`frontend/src/shared/error-api.ts:25`) solo omite la clave cuando es `undefined`; el api-client de C-04 normaliza además `null`, `[]` y `{}` como "sin detalles" y quita la clave. `problemdetails` está desactivado.
- `400` de validación: `codigo: "VALIDACION"` y `detalles: [{ "campo": "email" | "password", "mensaje": string }]`.
- `401` de credenciales: `codigo: "RN-AU-01"`, mensaje `El email o la contraseña no son correctos.` — idéntico para email inexistente, contraseña incorrecta y cuenta inactiva.
- `401` de sesión: `codigo: "NO_AUTENTICADO"`, mensaje `Tu sesión no está iniciada o expiró. Volvé a ingresar.` También lo emite `/me` cuando el odontólogo está inactivo (y invalida la sesión).
- `429` de bloqueo (5º fallo, o cualquier intento durante el bloqueo de 15 min): `codigo: "RN-AU-03"`, mensaje `Demasiados intentos fallidos. Probá de nuevo en unos minutos.`, `detalles: { "reintentarEnSegundos": 900, "bloqueadoHasta": "<Instant ISO-8601 en UTC>" }`. C-04 usa `reintentarEnSegundos` como fuente de verdad del contador y calcula los minutos con `ceil(segundos / 60)`: 900 s = 15 min, y 841 o 899 s también dan 15 min. `bloqueadoHasta` es informativo. El "14 minutos" del mockup es ilustrativo para 840 s y no se copia a la UI real.
- `403` por CSRF faltante en escritura protegida: `codigo: "ACCESO_DENEGADO"`.

### Sesión y CSRF

- Cookie de sesión `JSESSIONID`: `HttpOnly`, `SameSite=Lax`, `tracking-modes: cookie`, inactividad de 8 h, con rotación del id al autenticar.
- Cookie CSRF `XSRF-TOKEN`: legible por JavaScript (no `HttpOnly`). El header a reenviar es `X-XSRF-TOKEN`.
- `/api/auth/login` y `/api/public/**` están excluidos de CSRF; el resto de `/api/**` exige rol y, en escrituras, el header.

### Consecuencia para el testing

El login E2E contra el backend real **queda para C-16** (`cierre-demo-y-ensayo`), hasta que C-03 se rebasee contra `main`. C-04 se verifica con Vitest y `fetch` mockeado usando este contrato como fixture. Los tipos del DTO y del detalle de bloqueo se concentran en un solo módulo: si el contrato cambia, se ajusta ahí.

## UI Mockups

Política `system-and-mockups`, revisión 2, aprobados por el usuario antes del apply:

- [mockups/login-escritorio.html](mockups/login-escritorio.html) — acceso en 1440×900, con estados de 401 y 429.
- [mockups/login-movil.html](mockups/login-movil.html) — acceso en 390×844, con el mismo mensaje genérico y el estado de bloqueo.
- [mockups/layout-base.html](mockups/layout-base.html) — app bar, navegación, salto al contenido, alertas, toasts y marcador móvil.

Los mockups están aprobados y **no se editan**. Solo el shell y el login son alcance real de C-04: el resto de `layout-base.html` (el aviso de las 20:00, los turnos, los badges de estado, los toasts y el estado vacío) es showcase de changes futuros (C-10, C-11, C-13, C-14) y el implementador **no debe copiarlo** a esta entrega. `design-system/components.html` (revisión 2) no define modal ni toast: si se implementan, se derivan de los tokens del design system, no de una pieza ya existente.

La implementación DEBE respetar los textos visibles de los mockups que sí son alcance ("Ingresá a tu agenda", "El email o la contraseña no son correctos.", "Demasiados intentos. Probá de nuevo en N minutos.", "Saltar al contenido") y sus estados de foco. Dos valores son ilustrativos y NO se copian: el "14 minutos" del mockup (la UI real calcula con `ceil`, así que 841/899 s dan 15 min) y el "Salir" del mockup móvil (la UI real dice "Cerrar sesión", como exige la spec).

## Estrategia de tests y verificación

- **Safety net inicial**: `frontend/src/shared/error-api.test.ts` ya existe y debe seguir en verde antes de tocar nada (tarea 1.1, excepción del protocolo RED).
- **Comando principal**: `npm --prefix frontend test` (ejecuta `vitest run`). Verificaciones complementarias: `npm --prefix frontend run lint` y `npm --prefix frontend run build`.
- **Sin `jsdom`**: `environment: 'node'`. Las dependencias del navegador se inyectan: `fetch` falso con respuestas controladas, lector de cookies falso, `history`/`location` falsos, reloj/temporizador falso y un `document` falso mínimo para los adaptadores. `arrancar(app, adaptador)` permite importar el punto de entrada sin efectos de módulo.
- **Cobertura por capability**: rutas y decisión del guard (`shell-frontend`); CSRF, mapeo de errores, red y sesión vencida (`api-client`); textos de 401/429, redirect validado y bloqueo con segundos parciales (`login-odontologo-ui`); zona horaria, cruce de día, validación y aritmética (`fechas`).
- **TDD estricto**: cada comportamiento se escribe primero como test que falla (RED), se implementa lo mínimo (GREEN), se triangula con **al menos dos casos adicionales** por comportamiento, se refactoriza con tests en verde y se vuelve a ejecutar la suite completa. Las únicas excepciones al RED son la tarea 1.1 (safety net: se ejecuta antes de escribir producción) y la tarea 7.2 (verificación final, con revisión manual de los mockups). El detalle está en [tasks.md](tasks.md).

## Riesgos y compensaciones

- [C-03 está desactualizado respecto de `main` y su contrato puede cambiar al rebasearlo] → Tipos del DTO y del 429 centralizados en un módulo; E2E diferido a C-16; reverificar este apartado antes del ensayo.
- [La cookie de sesión es `HttpOnly` y no puede leerse desde JS] → El guard decide con `/api/auth/me`, nunca con lectura de cookies.
- [Un 403 por CSRF puede confundirse con un rechazo de negocio] → Se mapea `ACCESO_DENEGADO` a un error visible con invitación a reintentar; nunca se reintenta automáticamente.
- [La cuenta regresiva usa el reloj del cliente y puede desincronizarse] → Solo es presentación; la fuente de verdad es `reintentarEnSegundos` y no se reanuda ningún pedido solo por vencimiento local.
- [Sin `jsdom`, los componentes con DOM quedan sin test automático en un entorno DOM real] → Lógica extraída a funciones puras y adaptadores con `document` falso inyectado; revisión visual manual en 7.2 y smoke en C-16; no se recortan tests de la lógica.
- [Vite necesita leer `design-system/` fuera de la raíz de `frontend/`] → Alias explícito y `server.fs.allow` apuntando a la raíz del repo, preservando el `server.proxy` existente; verificar que `npm --prefix frontend run build` siga copiando los tokens.
- [Texto del mockup sobre hora de cierre de sesión] → No se implementa: sería una regla de negocio calculada con la hora del navegador, prohibida por RN-GL-02.

## Plan de aplicación y rollback

No hay migración de datos: el change solo agrega código frontend y configuración de build/lint.

1. Verificar la safety net (`npm --prefix frontend test`) antes de escribir producción.
2. Aplicar por capas, en este orden: configuración y marca (1.2–1.4) → fechas → api-client → router/guard → componentes y layout → login. Cada capa con tests en verde.
3. Ejecutar test, lint y build. El build sigue sin tocar backend ni migraciones.
4. Rollback: revertir los commits del change; no hay estado persistente que limpiar.
