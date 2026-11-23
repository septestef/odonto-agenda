# Proposal — C-06 `configuracion-agenda-ui`

## Why

C-05 ya publicó la API para configurar horarios, prestaciones y bloqueos, pero el odontólogo hoy no tiene forma de usarla: `/config` sigue siendo un marcador del shell (C-04). Sin esas tres pantallas no se puede cargar la agenda de la demo del 25/11 ni ver el efecto de RN-AG-01..04 desde la interfaz.

## What Changes

- Reemplazar el marcador de `/config` por la pantalla de **horario semanal**: siete días, varias franjas por día, días sin atención y guardado como reemplazo completo vía `PUT /api/horarios`.
- Agregar la pantalla de **prestaciones**: alta, edición y baja lógica (sin borrado físico), con `GET/POST/PUT/DELETE /api/prestaciones`; las dadas de baja se muestran diferenciadas y siguen conservando los turnos existentes.
- Agregar la pantalla de **bloqueos**: alta con rango de fecha y hora en zona `America/Argentina/Buenos_Aires`, listado y baja vía `GET/POST/DELETE /api/bloqueos`; si el bloqueo tiene turnos afectados, se listan con su estado y se ofrece la acción de cancelar.
- Validar en el cliente, antes de pedir: duración múltiplo de 15 (RN-AG-03), inicio estrictamente anterior al fin, franjas solapadas dentro del mismo día (RN-AG-01) y rango de bloqueo invertido. Mostrar los errores `{codigo, mensaje, detalles?}` del `@RestControllerAdvice` con el `mensaje` del backend y el foco en el campo correspondiente cuando haya `detalles`.
- Mantener el aislamiento de RN-SE-01: el `odontologo_id` nunca viaja en el cuerpo ni se toma del request; la UI solo consume la sesión y el `api-client` existente (CSRF y 401 ya resueltos por C-04).
- Crear los seis mockups HTML (tres pantallas × escritorio y móvil) sobre el design system aprobado, con estado **PENDIENTE DE APROBACIÓN** antes del apply.

**Fuera de alcance:** cancelar efectivamente los turnos afectados (la acción se muestra pero el pedido a `POST /api/turnos/{id}/cancelar` es de C-12); historia clínica y odontograma (C-17/C-18, post-demo); turno manual (C-15); cambios en la API de C-05, en el esquema o en el shell de C-04.

## Capabilities

### New Capabilities

- `configuracion-agenda-ui`: comportamiento observable de las tres pantallas de configuración del odontólogo, su validación en cliente, la carga y el guardado de horarios, prestaciones y bloqueos, el listado de turnos afectados y el manejo de errores de la API.

### Modified Capabilities

- `shell-frontend`: `/config` deja de ser un marcador genérico y pasa a mostrar la configuración real con sus propios pedidos a la API; `/agenda` y las rutas públicas conservan su comportamiento actual.

## Impact

- **Frontend:** nuevos módulos bajo `frontend/src/features/config/` (horarios, prestaciones y bloqueos, validadores y mapeo de franjas); reemplazo del marcador de `/config` en `frontend/src/main.ts`; reutilización de `shared/api-client.ts`, `shared/error-api.ts`, `shared/fechas.ts` y los componentes de `shared/ui/`.
- **Contratos consumidos (sin cambios):** `GET/PUT /api/horarios`, `GET/POST/PUT/DELETE /api/prestaciones`, `GET/POST/DELETE /api/bloqueos` de C-05, todos con sesión y header `X-XSRF-TOKEN` en escrituras.
- **Datos:** sin migraciones; los instantes se envían con offset y las horas de franja en `HH:mm`, sin confiar en la zona del dispositivo.
- **Diseño:** `design-system/tokens.css`, `design-system/components.html`, `design-system/brand.js` y `design-system/brand.md` (revisión 2 aprobada); los mockups viven en `openspec/changes/c-06-configuracion-agenda-ui/mockups/` y no se editan durante el apply.
- **Tests:** Vitest para validadores, mapeo de franjas, estados de carga/error y pruebas de render de las tres pantallas en 360 px y escritorio; sin `any` bajo `tsconfig` strict.
- **Dependencias:** C-04 (shell y cliente API) y C-05 (contratos) ya completados y archivados. Gobierno BAJO, pero el gate visual de mockup aprobado sigue siendo obligatorio.
