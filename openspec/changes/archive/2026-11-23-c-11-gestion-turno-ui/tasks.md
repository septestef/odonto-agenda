# Tasks

## 1. Precondiciones y gate de diseño

- [x] 1.1 Verificar que C-09 y C-10 estén archivados y que existan `features/reservar/api-publica.ts`, `shared/selector-horarios.ts`, `shared/error-api.ts` y la ruta `turno` en `router.ts` (verificación: `openspec list --specs` muestra `gestion-turno-token` y `reserva-publica-ui`; lectura de `frontend/src`).
- [x] 1.2 Alinear `mockups/reprogramar.html` con la navegación de días aprobada en C-10 y regenerar `previews/reprogramar-390.png` y `reprogramar-1440.png` (verificación: el render a 390×844 y 1440×900 no tiene scroll horizontal y "Semana siguiente" se deshabilita en la semana del 17 al 23 de enero).
- [x] 1.3 Obtener la aprobación explícita de la revisión 2 de los mockups y registrar `Approval: approved` en `design.md`, este archivo y `mockups/README.md` (verificación: aprobación del usuario en el chat o el PR; sin ella no se continúa). Estado: **Approval: approved (2026-11-22, by user)**. Criterio de aceptación: [mockups/](mockups/) y [mockups/previews/](mockups/previews/).

## 2. Navegación de días compartida

- [x] 2.1 Extraer `shared/navegador-dias.ts` desde `features/reservar/pantalla-reserva.ts` con `primeraSemana`, `semanaDe`, `moverSemana` y `navegadorDias` (con `diaMarcado` opcional) según D3 (verificación: tests nuevos de bordes de la ventana, salto por fecha y día marcado).
- [x] 2.2 Hacer que `pantalla-reserva.ts` use el módulo extraído sin cambiar su salida (verificación: los tests existentes de C-10 pasan sin modificar sus expectativas; si alguno necesita cambiar, detener y consultar).

## 3. Cliente de gestión

- [x] 3.1 Crear `features/turno/modelo.ts` con `TurnoGestion`, vistas, estado y acciones del flujo (verificación: `npm run build` compila sin `any`).
- [x] 3.2 Implementar `features/turno/api-gestion.ts` con `consultar`, `cancelar` y `reprogramar`, `fetch` inyectable, guardas sobre `unknown` y `interpretarErrorApi` (verificación: tests de 200 válido e inválido, 204, 400, 404, 409, 422, 500 y red).
- [x] 3.3 Validar la forma del token (43 caracteres base64url) antes de llamar y confirmar que ninguna URL sale de `/api/public/` ni el token aparece en errores (verificación: tests dedicados).
- [x] 3.4 Verificar que el cuerpo de `reprogramar` lleva el `inicio` con el texto exacto recibido (verificación: test que compara el string).

## 4. Máquina de estados

- [x] 4.1 Implementar `features/turno/estado.ts` con las vistas y transiciones de D2 (verificación: tests de camino feliz de cancelar y reprogramar, y de arrepentirse).
- [x] 4.2 Implementar la relectura después de 204 y el `resultado` local para el título de solo lectura (verificación: tests de "Turno cancelado" y "Cambiaste tu turno" con el turno releído).
- [x] 4.3 Implementar el bloqueo de doble envío en cancelar y reprogramar (verificación: test de que una segunda acción no dispara otra solicitud).
- [x] 4.4 Implementar las reacciones por código de D5, incluido `RN-TU-01` con refresco que conserva día y semana, y la distinción entre error de red y 404 (verificación: un test por fila de la tabla de D5).

## 5. Pantalla `/turno/{token}`

- [x] 5.1 Implementar `pantalla-turno.ts` con carga, turno activo (acciones según `puedeCancelar`/`puedeReprogramar`), link inválido y error de carga (verificación: tests de descriptores por vista con el adaptador de prueba).
- [x] 5.2 Implementar la confirmación de cancelación como hoja inferior en celular y diálogo en escritorio, con `role="dialog"`, foco al título, `Escape` y devolución del foco (verificación: tests de descriptor y smoke manual).
- [x] 5.3 Implementar "Cambiar horario" con `navegadorDias` (día del turno marcado), `selector-horarios` de C-10, comparación "Antes / Nuevo" y "Confirmar cambio" (verificación: tests de descriptor y smoke contra el backend en perfil `demo`).
- [x] 5.4 Implementar la vista de solo lectura para cancelado, reprogramado y pasado, con "Reservar otro turno" a `/reservar/{slug}` (verificación: tests de descriptor de las tres variantes).
- [x] 5.5 Agregar a `estilos/app.css`, bajo `.gestion-turno`, las piezas nuevas del mockup solo con tokens (verificación: `tokens.test.ts` en verde y sin literales nuevos fuera de los admitidos).
- [x] 5.6 Aplicar `textContent` en todo dato, `aria-live`/`role="alert"`, foco al título por vista y layout sin desborde a 360 px (verificación: revisión del render y prueba a 360 px).

## 6. Integración y verificación

- [x] 6.1 Registrar la vista en `main.ts` para la ruta `turno` en lugar de `vistaMarcador` (verificación: test de `main` que resuelve `/turno/{token}` a la pantalla nueva).
- [x] 6.2 Correr `npm test`, `npm run lint` y `npm run build` en `frontend/` (verificación: salida en verde).
- [x] 6.3 Recorrido E2E manual con backend `bootRun` en perfil `demo`: reservar, abrir el link del email, cambiar horario, abrir el link viejo (solo lectura), abrir el link nuevo y cancelar (verificación: checklist del recorrido). Hecho el 2026-11-22 con emails reales: reserva → link → cambio al jueves 26/11 10:30 (POST 204 + relectura) → link viejo en solo lectura "Cancelado" → link nuevo activo → `Escape` cierra la confirmación → cancelación (POST 204 + relectura, "Cancelaste tu turno"). La paciente también canceló un turno anterior desde su navegador: se vio en solo lectura.
- [x] 6.4 Verificar que el token no queda en `localStorage`/`sessionStorage`, la consola ni solicitudes fuera de `/api/public/turnos/` (verificación: inspección de almacenamiento y red durante 6.3). Resultado: almacenamiento y cookies sin token, sin recursos externos, consola sin errores, el token no aparece en el log del backend; documento `/turno/{token}` y API con `Cache-Control: no-store` y `Referrer-Policy: no-referrer`.
- [x] 6.5 Comparar cada pantalla con su mockup aprobado a 390×844 y 1440×900, guardar capturas en `mockups/verify/` y controlar que `/reservar/demo` se vea igual que antes del refactor (verificación: reporte de diferencias; las no aprobadas se corrigen). Hecho con la API simulada en Playwright; capturas en [mockups/verify/](mockups/verify/).

## Workflow follow-up

- Archivar el change cuando los tests estén en verde y el usuario apruebe la UI final.
- Marcar `C-11` como `[x]` en `CHANGES.md`.
- Sincronizar la capability `gestion-turno-ui` a `openspec/specs/` al archivar.
