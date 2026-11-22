# Tasks

## 1. Precondiciones y gate de diseño

- [ ] 1.1 Verificar que C-04 esté archivado y que existan el router, `shared/api-client.ts`, `shared/fechas.ts` y los componentes base; si falta, detener el apply y reportarlo (verificación: `git log`/`openspec list --specs` y lectura de `frontend/src`).
- [x] 1.2 Crear los mockups `mockups/reserva-seleccion.html`, `reserva-formulario.html`, `reserva-exito.html` y `reserva-conflicto.html` importando `design-system/tokens.css`, `brand.js` y `assets/logo.svg` (verificación: abrir cada archivo en 390×844 y 1440×900 y confirmar que no hay colores ni medidas hardcodeadas).
- [x] 1.3 Incluir en los mockups los estados de perfil, día sin horarios, error por campo, resumen, éxito, conflicto `RN-TU-01`, límite `RN-PA-03` y la vista a 360 px (verificación: checklist visual contra `design-system/components.html`).
- [x] 1.4 Obtener la aprobación explícita del usuario sobre los mockups antes de escribir código (verificación: confirmación del usuario; sin ella no se continúa).

## 2. Modelo y cliente de la API pública

- [ ] 2.1 Crear `frontend/src/features/reservar/modelo.ts` con los tipos de perfil, disponibilidad, horario, datos del paciente, errores y estados del flujo (verificación: `npm run build` compila sin `any`).
- [ ] 2.2 Implementar `api-publica.ts` con `fetch` inyectable, guardas sobre `unknown` para validar la forma de `PerfilPublico`, `DisponibilidadPublica` y `ReservaConfirmada`, y normalización de errores con `interpretarErrorApi` (verificación: tests de 200/201, 400, 404, 409, 500 y JSON inesperado pasan).
- [ ] 2.3 Verificar que un JSON inesperado no se propague sin tipar y que un error de red se convierta en un error genérico reintentable (verificación: test unitario dedicado).
- [ ] 2.4 Confirmar que ninguna llamada expone ni registra el token de gestión (verificación: revisar el módulo y los tests; no hay campo token en los tipos).

## 3. Validación local y armado de la solicitud

- [ ] 3.1 Implementar `validaciones.ts` con DNI `^[0-9]{7,8}$`, teléfono E.164, email, nombre y apellido obligatorios y ≤ 100 caracteres, devolviendo un mensaje por campo (verificación: tests de cada caso válido e inválido).
- [ ] 3.2 Implementar `solicitud.ts` para armar `{ prestacionId, inicio, dni, nombre, apellido, telefono, email }` con `prestacionId` numérico y el `inicio` con offset tal como llegó de la API (verificación: test que compara el objeto y confirma que el string de `inicio` no se re-serializa).
- [ ] 3.3 Cubrir con tests que los datos del paciente se recortan solo en espacios externos y que los campos inválidos impiden construir una solicitud válida (verificación: `npm test`).

## 4. Máquina de estados del flujo

- [ ] 4.1 Implementar `estado.ts` con los estados `cargando-perfil`, `eligiendo-prestacion`, `eligiendo-horario`, `completando-datos`, `enviando`, `exito`, `sin-horarios`, `conflicto`, `error-perfil` y `error-envio`, y las acciones correspondientes (verificación: tests de transiciones válidas e inválidas).
- [ ] 4.2 Implementar la conservación de datos ante `conflicto` y el reinicio del flujo con "Reservar otro turno" (verificación: tests de que el conflicto conserva los datos y limpia solo el horario).
- [ ] 4.3 Implementar el bloqueo de doble envío mientras el estado es `enviando` (verificación: test que una segunda acción `confirmar` no dispara otra solicitud).

## 5. Selector de horarios reutilizable

- [ ] 5.1 Implementar `shared/selector-horarios.ts` con `{ horarios, seleccionado, onSeleccionar, vacio }`, `aria-pressed` y estado vacío parametrizable (verificación: revisión de tipos y `npm run lint`; smoke manual en el navegador).
- [ ] 5.2 Verificar que el selector no conoce slugs, prestaciones ni fetch y que C-11/C-15 pueden reutilizarlo (verificación: revisar imports del módulo; solo depende del modelo de horario).

## 6. Pantalla `/reservar/{slug}`

- [ ] 6.1 Implementar `pantalla-reserva.ts` con carga del perfil, estados de carga y error 404 genérico (verificación: smoke con `demo` y con slug inexistente contra el backend en perfil `demo`).
- [ ] 6.2 Implementar la selección de prestación, la tira de días con piso `fechaMinima`, el `input type="date"` con `min`/`max` y la carga de disponibilidad (verificación: smoke de un día con horarios y de un día sin horarios).
- [ ] 6.3 Implementar el formulario de datos con validación local, errores por campo, resumen previo y envío al `POST` (verificación: smoke con datos inválidos y válidos; la API recibe una sola solicitud).
- [ ] 6.4 Implementar la pantalla de éxito con fecha, hora, prestación, consultorio y email, sin token, y el reinicio "Reservar otro turno" (verificación: smoke de una reserva real en `demo`).
- [ ] 6.5 Implementar el manejo de 409 `RN-TU-01` con mensaje "Ese horario acaba de ser tomado, elegí otro", refresco de disponibilidad y conservación de datos (verificación: reservar el mismo horario desde dos pestañas y comprobar la reacción).
- [ ] 6.6 Implementar los mensajes de `RN-PA-03`, `RN-TU-03`, `RN-TU-11`, `RN-AG-05`, `VALIDACION`, `NO_ENCONTRADO` y error de red/500 (verificación: tests de mapeo de códigos y smoke de al menos `RN-PA-03` con un DNI con dos turnos).
- [ ] 6.7 Aplicar `textContent` en todo dato de usuario/API, foco al título del paso, `aria-live`/`role="alert"` y layout de una columna con tokens (verificación: revisar el render y probar a 360 px sin desborde horizontal).

## 7. Integración y verificación

- [ ] 7.1 Registrar la ruta `/reservar/{slug}` en el router de C-04 y verificar que el fallback de la SPA la resuelva (verificación: abrir la URL directa en el build servido por Spring).
- [ ] 7.2 Correr `npm test`, `npm run lint` y `npm run build` en `frontend/` y dejarlos en verde (verificación: salida sin errores).
- [ ] 7.3 Hacer el recorrido E2E manual con backend `bootRun` en perfil `demo`: perfil → prestación → fecha → horario → datos → éxito → email recibido (verificación: checklist del recorrido completo).
- [ ] 7.4 Verificar que la UI nunca muestra el token, ni datos de otros pacientes, y que el link de gestión solo aparece por email (verificación: inspección visual y de red en el recorrido).
- [ ] 7.5 Comparar cada pantalla con su mockup aprobado y anotar diferencias (verificación: reporte breve de diferencias; las diferencias no aprobadas se corrigen).

## Workflow follow-up

- Archivar el change cuando los tests estén en verde y el usuario haya aprobado la UI final.
- Marcar `C-10` como `[x]` en `CHANGES.md`.
- Sincronizar la capability `reserva-publica-ui` a `openspec/specs/` al archivar.
