# Tareas: C-08 — Notificaciones por email

## 1. Gate de revisión y línea de base

- [x] 1.1 Registrar la revisión ALTO de Estefi (2026-11-21): P1–P6 cerradas; propuesta, delta, diseño y tareas reconciliados.
- [x] 1.2 Con el entorno autorizado y listo, correr la suite backend actual unitaria y de integración contra PostgreSQL real (`turnotarde_test`); registrar su resultado y cualquier bloqueo sin omitir, borrar ni sustituir tests por H2. Durante la propuesta no se ejecutaron tests.

**Línea de base:** PostgreSQL 19.0 autenticado (`current_database() = turnotarde_test`; `turno_sin_solapamiento` presente). Desde `backend/`, `.\gradlew.bat test integrationTest -PskipFrontend --rerun-tasks --console=plain`: 131 unitarios y 166 de integración, sin fallos ni omitidos; 6 tareas ejecutadas.

**Gate de Apply:** Apply autorizado por Estefi el 2026-11-21. Esta autorización se concedió por separado de la revisión de planificación y solo se registra en esta actualización.

## 2. Contenido y transporte

- [x] 2.1 En TDD (rojo → verde → refactor), crear los tests de contenido y las tres plantillas HTML UTF-8. Verificá en cada plantilla fecha/hora con `ZonaNegocio` (incluido cambio de día respecto a UTC), datos del snapshot y escape de texto/atributos; confirmación con prestación, dirección y un link válido, cancelación sin link y reprogramación con solo el nuevo. Cubrí base configurada con barra final, base inválida, caracteres inesperados en el token y ausencia de dirección física según P3; no uses la zona por defecto de la JVM.
- [x] 2.2 Con tests primero, mapear destinatarios/remitente/asuntos según P2/P3 y validar exactamente un destinatario; cubrí email ausente, inválido, lista e intento de inyectar encabezados, sin fallback, copia ni consulta del contacto actual. Verificá con snapshots anterior/nuevo distintos que el candidato de reprogramación usa solo el nuevo y no expone datos del anterior.
- [x] 2.3 Con tests de configuración primero, integrar `JavaMailSender`, STARTTLS requerido, verificación de servidor y timeouts finitos de conexión, lectura y escritura vía configuración del transporte, consumiendo `MAIL_*` sin valores secretos versionados. Agregar el doble del perfil de test y comprobar que captura mensajes/simula fallas sin red. Verificá que en `dev` y `test` la API conserva el 201, no espera al correo y, con configuración incompleta o inválida, el aviso admitido con destinatario válido queda en `ERROR`/`CONFIG_SMTP` sin conexión ni datos sensibles. Documentá junto a este trabajo cuota aproximada, spam y cómo verificar de forma separada y autorizada el SMTP de demo y el acceso a `APP_BASE_URL` desde el dispositivo receptor.
- [x] 2.4 Con tests de configuración primero, verificá en `demo` que el arranque falla si falta o es inválida cualquiera de `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` o `MAIL_FROM_NAME`. Comprobá que el diagnóstico de arranque nombra la variable, sin mostrar su valor ni otros valores sensibles; usá valores centinela, sin credenciales reales ni red.

## 3. Persistencia independiente y confidencialidad

- [x] 3.1 En TDD, implementar el registrador por un bean transaccional separado (inyección por constructor, `REQUIRES_NEW`) reutilizando `Notificacion` y `NotificacionRepository`. En PostgreSQL real, verificá los tipos y FK correspondientes, auditoría `ENVIADO` con error nulo y `ERROR` con `enviado_en NOT NULL` en UTC, determinista con `Clock` y según P5. Verificá que `destinatario` sea el único dato personal directo; ningún token, link o cuerpo se guarda.
- [x] 3.2 Con pruebas de salidas con datos centinela primero, verificá que las únicas categorías persistidas sean `CONFIG_SMTP`, `URL_INVALIDA`, `RENDER` y `SMTP_FALLO` según P5, y que el rechazo de despacho (P1), el destinatario ausente o inválido (P3) y la escritura fallida (P6) solo emitan diagnósticos sanitizados. Comprobá que ni el registro ni los logs muestran DNI, nombres, direcciones, email, teléfono, credenciales, cuerpo, token, link, valores del evento o excepciones crudas (en la fila, solo el email requerido según P5). Para email ausente o inválido, verificá que no hay envío ni intento de escritura.

## 4. Consumo y ejecución asíncrona

- [x] 4.1 En TDD, conectar `AFTER_COMMIT` sin fallback al único ejecutor dedicado (dos workers, cola de 32 y rechazo explícito, sin `CallerRunsPolicy`), sin espera de cupo ni ejecución en el hilo llamador. En integración con PostgreSQL real y transporte falso, verificá con otra conexión que el turno está visible cuando empieza el correo, que en el worker no hay transacción activa y que el 201 exacto de C-07 llega con el transporte todavía bloqueado. Usá latches, barreras o drenaje controlado con tiempos máximos, nunca `sleep` ni depender de una carrera.
- [x] 4.2 Con pruebas negativas primero, provocar rollback real **después de publicar** en una transacción válida, y probar en forma separada publicación sin transacción y rechazo de reserva 409 (`RN-TU-01`/`RN-PA-03`). Verificá sin carreras que no hay tarea, mensaje ni registro en ninguno de esos casos, usando un ejecutor controlable y esperando/drenando tareas antes de limpiar fixtures.
- [x] 4.3 Con fixtures transaccionales (sin emisores de producción), verificar los eventos existentes de cancelación y reprogramación con commit y rollback: aviso/registro únicamente al confirmar, link ausente o nuevo según corresponda, FK correcta y sin cambiar estado ni datos fuera de la fixture. Probar que el cambio de contacto luego de publicar una reserva no cambia el destinatario.
- [x] 4.4 En TDD, cubrí una falla SMTP no ambigua con 201 intacto, turno `RESERVADO` y fila `ERROR`/`SMTP_FALLO`. Forzá saturación y rechazo del ejecutor: verificá que el request no se bloquea, no hay envío (ni en el hilo llamador) ni intento de escritura o fila en base; solo hay un log sanitizado de categoría cerrada (P1). Probá por separado SMTP aceptado con escritura fallida y timeout con aceptación incierta (P6), ambos sin reenvío ni reintento. En el primero queda solo un diagnóstico sanitizado, sin confirmar una fila ni intentar otra escritura. Para el timeout de un aviso admitido con destinatario válido, verificá en PostgreSQL real que se guarda una fila `ERROR`/`SMTP_FALLO` en una transacción independiente `REQUIRES_NEW`, con el instante UTC del intento tomado del `Clock` inyectado en `enviado_en`. Verificá que el turno nunca se revierte. Documentá la ventana post-commit y la falta de outbox y reintentos durables.

## 5. Regresión del sistema

- [x] 5.1 Ejecutar la suite backend unitaria y de integración con PostgreSQL real y transporte falso, sin omitir tests. Confirmá que `ReservaOnlineIT`, `ReservaEventosIT`, `ReservasConcurrentesApiIT` y `ReservasConcurrentesIT` mantienen el contrato: mismo HTTP íntegro, 201 sin token ni hash, exactamente una reserva ganadora y un aviso para ella cuando hay cupo; la restricción `turno_sin_solapamiento` sigue siendo la fuente de verdad. Registrá resultados y bloqueos del entorno sin declarar verde lo que no se ejecutó.

**Verificación final:** `.\gradlew.bat build --rerun-tasks --console=plain` desde `backend/`, con frontend: 12 tareas
ejecutadas, 178 unitarios y 179 de integración en PostgreSQL real, sin fallos ni omitidos. Regresión C-07:
`ReservaOnlineIT` 20, `ReservaEventosIT` 5, `ReservasConcurrentesApiIT` 10 y `ReservasConcurrentesIT` 5.
`.\node_modules\.bin\vitest.cmd run` desde `frontend/`: 11/11. El primer build agotó las conexiones de PostgreSQL;
cerrar los contextos nuevos de C-08 al terminar sus clases liberó los pools en la segunda corrida completa.
