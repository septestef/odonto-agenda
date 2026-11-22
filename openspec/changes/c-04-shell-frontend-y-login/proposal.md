# Proposal — C-04 `shell-frontend-y-login`

## Why

El frontend que dejó C-01 es un `<main id="app">` vacío: no hay router, no hay cliente de API, no hay estilos ni una sola pantalla. Sin ese esqueleto no se puede construir ninguna de las seis pantallas que siguen (C-06, C-10, C-11, C-13, C-14, C-15), y el odontólogo no tiene forma de entrar al producto. C-04 cierra la Fase 1 del roadmap y es la base de toda la UI pre-demo del 2026-11-25.

## What Changes

- **Router propio** (`frontend/src/router.ts`) sobre la History API, con rutas `/login`, `/agenda`, `/config`, `/reservar/{slug}`, `/turno/{token}` y una vista de "no encontrado". El fallback a `index.html` que ya existe en Spring (capability `entrega-frontend`) resuelve las rutas profundas.
- **Guard de sesión** en el router: las rutas del odontólogo consultan `GET /api/auth/me` (con caché en memoria) y, sin sesión, redirigen a `/login` conservando el destino pedido. Las rutas públicas del paciente (`/reservar/{slug}`, `/turno/{token}`) nunca piden sesión.
- **`shared/api-client.ts`**: `fetch` con `credentials: 'same-origin'`, reenvío del header `X-XSRF-TOKEN` leído de la cookie en los métodos que lo necesitan, y errores tipados que reutilizan `interpretarErrorApi` (C-01). Suma un error de red (`SIN_CONEXION`) y un aviso global de sesión vencida.
- **`shared/fechas.ts`**: formateo y aritmética de fechas en `America/Argentina/Buenos_Aires` con `Intl`, sin librerías de fechas. El "ahora" del navegador nunca decide una regla de negocio: solo se usa para mostrar.
- **Componentes base** (`shared/ui/`): botón, campo de formulario, alerta, modal de confirmación (`<dialog>`), toasts y layout (app bar con logo, navegación Agenda/Configuración, cerrar sesión, salto al contenido). Todos construidos con las variables de `design-system/tokens.css` y los patrones de `design-system/components.html` (revisión 2).
- **Estilos**: `frontend/src/estilos/app.css` importa `tokens.css` desde `design-system/` (única fuente de valores visuales) y define la estructura con esas variables. No se copian valores a mano.
- **`features/auth`**: página de login (US-001) con email y contraseña, error genérico ante 401, estado de bloqueo con el tiempo restante ante 429, botón con estado de envío y cierre de sesión desde el app bar.
- **Regla de seguridad del frontend**: todo texto que venga del usuario o de la API se inserta con `textContent`; se agrega una regla de ESLint que prohíbe `innerHTML`, `outerHTML` e `insertAdjacentHTML`.
- **Tests Vitest**: resolución de rutas y decisión del guard, `api-client` (CSRF, mapeo de errores, falla de red, cuerpo vacío) y utilidades de fechas (cruce de día, rangos horarios, validación de `YYYY-MM-DD`).
- **Mockups aprobados** (política `system-and-mockups`): login en escritorio y en móvil, y el layout base con sus estados de ejemplo, en `openspec/changes/c-04-shell-frontend-y-login/mockups/`.

**Fuera de alcance**: las pantallas reales de agenda, configuración, reserva pública y gestión por token, que son de C-13, C-06, C-10 y C-11 respectivamente; acá las cuatro rutas apuntan a un único marcador genérico sin lógica. También quedan fuera cualquier endpoint o cambio de backend (C-03 es otro change) y el smoke E2E del login contra el backend real.

**Diferido explícitamente**: `C-03` (`auth-sesion-odontologo`) todavía no existe, así que el login no se puede probar de punta a punta. Este change se implementa y se verifica con Vitest y `fetch` mockeado; el E2E de login (y la verificación de que el contrato de `/api/auth/me`, 401 y 429 coincide) queda para cuando C-03 esté implementado, como parte de C-16 `cierre-demo-y-ensayo`.

## Capabilities

### New Capabilities

- `shell-frontend`: router propio sobre History API, tabla de rutas, guard de sesión con `GET /api/auth/me`, layout con app bar y navegación, y vista de ruta inexistente.
- `api-client`: cliente HTTP del frontend con CSRF por cookie, credenciales del mismo origen y errores tipados `{codigo, mensaje, detalles?}` más los casos de red y de sesión vencida.
- `login-odontologo-ui`: el lado frontend de US-001: formulario de login, mensaje genérico de credenciales inválidas, estado de bloqueo con tiempo restante, redirección al destino y cierre de sesión.
- `fechas`: utilidades de fecha y hora del frontend interpretadas en `America/Argentina/Buenos_Aires` (formateo, fecha local `YYYY-MM-DD`, aritmética de días y validación), con el "ahora" del navegador limitado a la presentación.

### Modified Capabilities

<!-- Ninguna: `entrega-frontend`, `errores-api`, `tiempo-servidor` y `configuracion-entorno` no cambian sus requisitos. El api-client consume `interpretarErrorApi` tal como lo define `entrega-frontend`. -->

## Impact

- **Código nuevo (solo frontend)**: `frontend/src/router.ts`, `frontend/src/main.ts`, `frontend/src/estilos/app.css`, `frontend/src/shared/{api-client,fechas,csrf,brand,sesion}.ts`, `frontend/src/shared/ui/*`, `frontend/src/features/auth/*`, `frontend/src/vistas/marcador.ts` (marcador genérico para las cuatro rutas de feature), `frontend/src/vistas/no-encontrado.ts`, `frontend/src/vistas/error-carga.ts`, y sus `*.test.ts`.
- **Configuración**: `frontend/vite.config.ts` (alias y acceso de lectura a `design-system/` para dev y build), `frontend/eslint.config.js` (prohibición de `innerHTML`), `frontend/tsconfig.json` (paths del alias si hacen falta). `frontend/index.html` suma la hoja de estilos y el favicon del logo.
- **Dependencias**: ninguna nueva. No se agrega `jsdom` ni librería de fechas: los módulos con DOM quedan finos y la lógica testeable se extrae a funciones puras con dependencias inyectadas.
- **Contrato con C-03 (pendiente)**: `GET /api/auth/me` (forma de la sesión), `POST /api/auth/login`, `POST /api/auth/logout`, el 401 genérico y el 429 con tiempo restante. C-04 define el mínimo que consume y lo deja documentado en `design.md`; si C-03 devuelve otra forma, se ajusta el tipo en un solo lugar.
- **Changes siguientes**: C-06, C-10, C-11, C-13, C-14 y C-15 consumen el router, el api-client, las fechas y los componentes base; cualquier cambio en sus firmas impacta en seis changes.
- **Riesgo de demo**: si C-03 se atrasa, la demo no puede loguearse aunque C-04 esté completo. C-04 no puede mitigarlo: solo deja el frontend listo y verificado con mocks.
