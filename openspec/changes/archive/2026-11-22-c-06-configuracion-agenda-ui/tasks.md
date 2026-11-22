# Tasks — C-06 `configuracion-agenda-ui`

> Orden de ejecución: el apply NO arranca hasta que el usuario apruebe explícitamente los seis mockups. Cada bloque de lógica sigue TDD estricto: **RED** (test que falla) → **GREEN** (código mínimo) → **TRIANGULACIÓN** (caso borde) → **REFACTOR** (limpieza sin cambiar comportamiento). Nunca se escribe código de producción antes de ver el test fallar. Sin `any` bajo `tsconfig` strict: usar `unknown` y estrechamiento explícito.

## 1. Gate visual y estructura del módulo

- [x] 1.1 Confirmar con el usuario la aprobación explícita de los seis mockups de `openspec/changes/c-06-configuracion-agenda-ui/mockups/` (horarios/prestaciones/bloqueos × escritorio/móvil) y verificar con `git diff` que ninguno fue modificado desde la propuesta; sin esta aprobación el apply se detiene.
- [x] 1.2 Crear la carpeta `frontend/src/features/config/` con `modelos.ts`, `validadores.ts`, `franjas.ts`, `instantes.ts`, `contratos.ts`, `api-config.ts`, `errores.ts`, `config.ts` y `vistas/`, y verificar que el tipo `tsc --noEmit` actual sigue pasando sin cambios de comportamiento.
- [x] 1.3 Verificar que `frontend/src/estilos/app.css` ya importa `design-system/tokens.css` y documentar en `design.md` (sin editar mockups) que los estilos nuevos solo usan `var(--...)`.

## 2. Modelos y validadores puros (TDD)

- [x] 2.1 **RED:** escribir `validadores.test.ts` con franja invertida (13:00–09:00), horas iguales, día 0 y 8, formato `HH:mm` inválido, duración 0/20/30/45/60 y rango de bloqueo invertido; verificar que todos fallan porque `validadores.ts` no existe.
- [x] 2.2 **GREEN:** implementar `validadores.ts` con `validarFranjas`, `validarDuracion` y `validarRangoBloqueo` que devuelven errores por campo en español rioplatense con voseo; verificar que los tests de 2.1 pasan.
- [x] 2.3 **TRIANGULACIÓN:** agregar a `validadores.test.ts` casos de franjas solapadas (09:00–11:00 vs 10:00–12:00), franjas contiguas (09:00–11:00 vs 11:00–13:00) y la misma franja en días distintos; verificar que las solapadas se rechazan con `RN-AG-01` y que contiguas y días distintos pasan.
- [x] 2.4 **REFACTOR:** extraer la lógica de solapamiento y el formato de mensajes a funciones internas sin cambiar resultados; verificar que todo `validadores.test.ts` sigue en verde y que no hay `any`.

## 3. Mapeo de franjas e instantes (TDD)

- [x] 3.1 **RED:** escribir `franjas.test.ts` con agrupación de los siete días ISO, dos franjas el lunes, domingo vacío, orden por `inicio` y round-trip lista→semana→lista; verificar que falla porque `franjas.ts` no existe.
- [x] 3.2 **GREEN:** implementar `franjas.ts` con agrupación y aplanado inverso sin ids ni `odontologoId`; verificar que los tests de 3.1 pasan.
- [x] 3.3 **TRIANGULACIÓN:** agregar franjas desordenadas en la respuesta y un día con tres franjas; verificar que el orden es estable y que el `PUT` resultante conserva todas.
- [x] 3.4 **RED:** escribir `instantes.test.ts` con armado y visualización de instantes con offset para 2026-11-27 10:00–12:00, cruce de mes/año y 23:59; verificar que falla porque `instantes.ts` no existe.
- [x] 3.5 **GREEN:** implementar `instantes.ts` apoyándose en `shared/fechas.ts`, calculando el offset de `America/Argentina/Buenos_Aires` para cada fecha sin usar la zona del dispositivo; verificar que los tests de 3.4 pasan.
- [x] 3.6 **TRIANGULACIÓN:** agregar caso de cambio de horario y verificar que el offset no se fija en `-03:00` de forma hardcoded; verificar que los tests siguen en verde.
- [x] 3.7 **REFACTOR:** unificar las funciones de formato de franjas e instantes; verificar que ambos archivos de test permanecen en verde.

## 4. Contratos de API y errores (TDD)

- [x] 4.1 **RED:** escribir `contratos.test.ts` con respuestas bien formadas de horarios, prestaciones (activas e inactivas) y bloqueos con `turnosAfectados`, más DTOs con tipos inesperados; verificar que falla porque `contratos.ts` no existe.
- [x] 4.2 **GREEN:** implementar `contratos.ts` que estrecha `unknown` a los DTOs de `modelos.ts` con errores comprensibles; verificar que los tests de 4.1 pasan.
- [x] 4.3 **TRIANGULACIÓN:** agregar `detalles` ausente y `turnosAfectados` vacío y poblado con estado `RESERVADO`; verificar que el mapeo conserva paciente, prestación, horario y estado.
- [x] 4.4 **RED:** escribir `errores.test.ts` para `RN-AG-01` con `detalles` en `franjas[1]`, `RN-AG-03` en `duracionMinutos`, `VALIDACION`, `NO_ENCONTRADO`, `NO_AUTENTICADO`, `ACCESO_DENEGADO`, `SIN_CONEXION` y un `mensaje` con `<script>`; verificar que falla porque `errores.ts` no existe.
- [x] 4.5 **GREEN:** implementar `errores.ts` que devuelve alerta y error por campo sin interpretar HTML, con el mismo mensaje para ajeno e inexistente; verificar que los tests de 4.4 pasan.
- [x] 4.6 **REFACTOR:** centralizar la búsqueda de campo en `detalles`; verificar que `contratos.test.ts` y `errores.test.ts` siguen en verde.
- [x] 4.7 Implementar `api-config.ts` con las rutas `GET/PUT /api/horarios`, `GET/POST/PUT/DELETE /api/prestaciones` y `GET/POST/DELETE /api/bloqueos`, sin `odontologoId` en cuerpo ni query; verificar con un test de `api-config.test.ts` que inspecciona cuerpo, query, `credentials: 'same-origin'` y `X-XSRF-TOKEN` en escrituras.

## 5. Vista de Horarios (TDD)

- [x] 5.1 **RED:** escribir `vistas/horarios.test.ts` con carga de dos franjas el lunes y domingo vacío, guardado que envía un único `PUT` con `{franjas}`, error 422 `RN-AG-01` que conserva el borrador y mensaje "Cambios guardados"; verificar que falla porque `vistas/horarios.ts` no existe.
- [x] 5.2 **GREEN:** implementar `vistas/horarios.ts` con estados `cargando | listo | guardando | error`, siete días y botón "Guardar horario"; verificar que los tests de 5.1 pasan.
- [x] 5.3 **TRIANGULACIÓN:** agregar validación local de franjas solapadas que no emite pedido, marca `aria-invalid="true"` en ambas y lleva el foco al primer control inválido; verificar que el test pasa con las franjas del mockup de escritorio y que el texto del error coincide con el mockup.
- [x] 5.4 **REFACTOR:** extraer el render de una franja a una función reutilizable; verificar que `horarios.test.ts` sigue en verde y que los textos visibles coinciden con `mockups/horarios-escritorio.html` y `mockups/horarios-movil.html`.

## 6. Vista de Prestaciones (TDD)

- [x] 6.1 **RED:** escribir `vistas/prestaciones.test.ts` con alta de "Limpieza" de 45 minutos, edición de duración con aviso de turnos existentes, baja confirmada que deja la fila como "Dada de baja" y baja cancelada sin `DELETE`; verificar que falla porque `vistas/prestaciones.ts` no existe.
- [x] 6.2 **GREEN:** implementar `vistas/prestaciones.ts` con listado de activas e inactivas, formulario de `nombre`/`duracionMinutos` y confirmación de baja; verificar que los tests de 6.1 pasan.
- [x] 6.3 **TRIANGULACIÓN:** agregar duración 20 minutos rechazada localmente con `RN-AG-03` y mensaje "La duración tiene que ser un múltiplo de 15 minutos.", y verificar que no se envía pedido y que no aparece acción de reactivar; verificar que coincide con `mockups/prestaciones-escritorio.html`.
- [x] 6.4 **REFACTOR:** compartir el render de errores por campo con la vista de horarios; verificar que `prestaciones.test.ts` y `horarios.test.ts` siguen en verde.

## 7. Vista de Bloqueos (TDD)

- [x] 7.1 **RED:** escribir `vistas/bloqueos.test.ts` con alta del 27/11 de 10:00 a 12:00, bloqueo con un turno `RESERVADO` en `turnosAfectados` y aviso "Crear un bloqueo no cancela tus turnos."; verificar que falla porque `vistas/bloqueos.ts` no existe.
- [x] 7.2 **GREEN:** implementar `vistas/bloqueos.ts` con formulario de fecha/hora/motivo, listado y baja confirmada; verificar que los tests de 7.1 pasan.
- [x] 7.3 **TRIANGULACIÓN:** agregar rango invertido rechazado sin `POST` y la acción "Cancelá el turno" que no envía pedido y conserva el estado original; verificar que los textos y el orden coinciden con `mockups/bloqueos-escritorio.html` y `mockups/bloqueos-movil.html`.
- [x] 7.4 **REFACTOR:** extraer el render de un turno afectado; verificar que `bloqueos.test.ts` permanece en verde.

## 8. Cableado de `/config` y conservación del shell (TDD)

- [x] 8.1 **RED:** escribir `config.test.ts` con las tres secciones navegables, sección activa marcada para lectores de pantalla y falla de red en una sección sin descartar las otras; verificar que falla porque `config.ts` no existe.
- [x] 8.2 **GREEN:** implementar `config.ts` como fachada que compone las tres vistas y coordina la selección de sección; verificar que los tests de 8.1 pasan.
- [x] 8.3 **RED:** escribir `main.config.test.ts` con `/config` montando la configuración real, `aria-current="page"` en Configuración y `/agenda` siguiendo con marcador sin pedidos propios; verificar que falla porque `main.ts` todavía despacha `vistaMarcador` para `/config`.
- [x] 8.4 **GREEN:** cambiar el despacho de `/config` en `frontend/src/main.ts` para usar `config.ts`, sin tocar el guard, las rutas públicas ni `layout.ts`; verificar que `main.config.test.ts`, `main.test.ts` y `router.test.ts` pasan.
- [x] 8.5 **TRIANGULACIÓN:** agregar navegación por teclado entre secciones y salto al contenido; verificar que el foco y `aria-current` se comportan igual que en C-04.
- [x] 8.6 **REFACTOR:** eliminar imports sin usar y verificar que `npm test` y `npx tsc --noEmit` pasan en el frontend sin `any`.

## 9. Verificación de integración (final, sin tests nuevos de unidades)

- [x] 9.1 Verificar con `npm test` que toda la suite del frontend pasa, incluidos los tests nuevos de validadores, franjas, instantes, contratos, errores, api-config y las tres vistas.
- [x] 9.2 Verificar con `npx tsc --noEmit` que `tsconfig` strict compila sin errores y sin `any`, y con `npm run build` que Vite genera el bundle sin warnings de estilos literales.
- [x] 9.3 Comparar cada pantalla real contra su mockup aprobado del mismo viewport (1440×900 y 360×844) y verificar que coinciden textos visibles, estados y orden de bloques; documentar cualquier diferencia como desvío, sin editar los mockups.
- [x] 9.4 Verificar con `git diff -- openspec/changes/c-06-configuracion-agenda-ui/mockups/` que los seis mockups siguen idénticos a la versión aprobada y con `git status` que no se modificó ningún artefacto de otro change.
