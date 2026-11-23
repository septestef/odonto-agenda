# Tareas

Aplican las reglas comunes de `design.md`. Guardá los resultados en `openspec/changes/c-16-cierre-demo-y-ensayo/evidencias/`; README contiene solo pasos y checklist de arranque.

## A — Arranque, datos y tests (Lauti)

Alcance: README, `.env.example`, `backend/**` (seed/configuración y tests necesarios), esta sección y `evidencias/` del change. Ver design D1-D6.

- [ ] 1.1 Ejecutar baseline pertinente antes de modificar comportamiento; registrar comandos, revisión, cantidades y resultados en `evidencias/`.
  Si falla, reportar el fallo preexistente y detener esa modificación para decidir cómo resolverlo.
- [ ] 1.2 Completar `.env.example` con todas las variables enumeradas en design D3 y verificar la herencia de `TEST_DB_PASSWORD`.
- [ ] 1.3 Ejecutar el reset local de `public` siguiendo design D2; comprobar que Flyway reconstruye el esquema y registrar el destino verificado.
- [ ] 1.4 Agregar una nueva V9xx libre en `backend/src/main/resources/db/demo` con tres o cuatro turnos sintéticos adicionales el 25/11, de mañana y tarde.
  Usar prestaciones distintas y al menos un `MANUAL`, sin solaparse entre sí ni con V900; conservar V900 inmutable y huecos libres el 26/27 para reservar en vivo.
  Validar carga limpia, turnos adicionales y huecos esperados mediante el IT de perfil demo con `Clock` fijo de design D4.
- [ ] 1.5 Arrancar desde cero con perfil demo y frontend incluido; confirmar Flyway, SPA y login en `http://localhost:8080`.
- [ ] 1.6 Ejecutar `./gradlew build` desde `backend/`; verificar unitarios, integración PostgreSQL real y frontend.
  Registrar comandos, revisión, cantidades y resultados en `evidencias/`, incluido `ReservasConcurrentesApiIT` con exactamente una ganadora.
- [ ] 1.7 Ejecutar `npm run lint`, `npm test` y `npm run build` desde `frontend/`; guardar comandos, revisión, cantidades y resultados en `evidencias/`.
- [ ] 1.8 Ensayar Gmail según design D6: verificar entrega/spam y enlace de gestión, cancelación y reprogramación con nuevo correo.
  Registrar el resultado y comprobar persistencia del turno ante fallo de envío.
- [ ] 1.9 Agregar `Correr la demo en la notebook` en README con pasos PowerShell exactos y checklist: PostgreSQL, entorno, reset y navegador.
  Verificar que la secuencia reproduzca el arranque ensayado, sin incorporar registros de resultados.
- [ ] 1.10 Revisar el diff contra el alcance A y entregar a Jero revisión candidata, evidencia y bloqueos; marcar solo tareas verificadas de A.

## B — Guion, smoke y verificación visual (Jero)

Crear la rama desde main actualizada con la propuesta integrada; ensayar una revisión que incluya A. Entregables: `guion-demo.md`, `evidencias/` y esta sección. Ver design D7.

- [ ] 2.1 Registrar la base integrada de la propuesta y confirmar que la revisión candidata del smoke incluye A y sus bloqueos resueltos o elevados.
- [ ] 2.2 Crear `guion-demo.md` para el miércoles 2026-11-25 con login/logout, configuración, reserva, correo, gestión, agenda, WhatsApp y manual.
  Trazar historias M, US-011 y US-015; declarar fuera US-013, US-014/C-21 y funcionalidades post-demo.
- [ ] 2.3 Ejecutar smoke del guion con fechas elegibles; guardar por paso revisión, contexto, esperado/observado y aprobado/fallo/bloqueado.
- [ ] 2.4 Ensayar dos reservas simultáneas del mismo horario con DNIs distintos; verificar una ganadora, un conflicto RN-TU-01 y una reserva en agenda.
  Vincular el resultado con el test de A, distinguiendo smoke manual de prueba automatizada.
- [ ] 2.5 Verificar escritorio y 360 px en navegador local; registrar herramienta, viewport y resultados. Sin automatización, declarar verificación manual.
- [ ] 2.6 Comparar pantallas/estados con mockups revisión 2 de C-04/C-06/C-10/C-11/C-13/C-14/C-15 y entregar matriz de evidencia y diferencias.
  Incluir el detalle de botones de escritorio C-15 y someter las diferencias a revisión.
- [ ] 2.7 Realizar ensayo final y consolidar evidencia A/B, alcance y pendientes; solicitar revisión final con suites en verde y cobertura real.
- [ ] 2.8 Entregar a Lauti revisión candidata, guion, evidencia, resultados y diferencias. Tras B y revisión aprobada, Lauti integra, marca CHANGES C-16 `[x]` y archiva.
