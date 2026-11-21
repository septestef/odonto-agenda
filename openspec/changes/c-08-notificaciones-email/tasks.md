# Tareas: C-08 — Notificaciones por email

## 1. Gate de revisión y línea de base

- [ ] 1.1 Obtener la revisión explícita de Estefi (ALTO), confirmar P1–P6 del diseño y reconciliar propuesta, delta, diseño y tareas antes de implementar. Verificá que quede registrada la revisión y que exista autorización separada de Apply.
- [ ] 1.2 Con el entorno autorizado y listo, correr la suite backend actual unitaria y de integración contra PostgreSQL real (`turnotarde_test`); registrar su resultado y cualquier bloqueo sin omitir, borrar ni sustituir tests por H2. Durante la propuesta no se ejecutaron tests.

## 2. Contenido y transporte

- [ ] 2.1 En TDD (rojo → verde → refactor), crear los tests de contenido y las tres plantillas HTML UTF-8. Verificá en cada plantilla fecha/hora con `ZonaNegocio` (incluido cambio de día respecto a UTC), datos del snapshot y escape de texto/atributos; confirmación con prestación, dirección y un link válido, cancelación sin link y reprogramación con solo el nuevo. Cubrí base configurada con barra final, base inválida, caracteres inesperados en el token y ausencia de dirección física según P3; no uses la zona por defecto de la JVM.
- [ ] 2.2 Con tests primero, mapear destinatarios/remitente/asuntos según P2/P3 y validar exactamente un destinatario; cubrí email ausente, inválido, lista e intento de inyectar encabezados, sin fallback, copia ni consulta del contacto actual. Verificá con snapshots anterior/nuevo distintos que el candidato de reprogramación usa solo el nuevo y no expone datos del anterior.
- [ ] 2.3 Con tests de configuración primero, integrar `JavaMailSender`, STARTTLS requerido, verificación de servidor y timeouts finitos de conexión, lectura y escritura vía configuración del transporte, consumiendo `MAIL_*` sin valores secretos versionados. Agregar el doble del perfil de test y comprobar que captura mensajes/simula fallas sin red; verificar la rama elegida en P4 para SMTP incompleto, sin espera del request y sin datos sensibles. Documentá junto a este trabajo cuota aproximada, spam y cómo verificar de forma separada y autorizada el SMTP de demo y el acceso a `APP_BASE_URL` desde el dispositivo receptor.

## 3. Persistencia independiente y confidencialidad

- [ ] 3.1 En TDD, implementar el registrador por un bean transaccional separado (inyección por constructor, `REQUIRES_NEW`) reutilizando `Notificacion` y `NotificacionRepository`. En PostgreSQL real, verificá los tipos y FK correspondientes, auditoría `ENVIADO` con error nulo y `ERROR` con `enviado_en NOT NULL` en UTC, determinista con `Clock` y según P5. Verificá que `destinatario` sea el único dato personal directo; ningún token, link o cuerpo se guarda.
- [ ] 3.2 Con pruebas de salidas con datos centinela primero, mapear fallas de configuración, render, SMTP y escritura a diagnósticos/categorías seguras; comprobar que ni el registro ni los logs muestran DNI, nombres, direcciones, email, teléfono, credenciales, cuerpo, token, link, valores del evento o excepciones crudas (en la fila, solo el email requerido según P5). Para email ausente o inválido, verificá el comportamiento de P3 sin fabricar destinatario.

## 4. Consumo y ejecución asíncrona

- [ ] 4.1 En TDD, conectar `AFTER_COMMIT` sin fallback a un ejecutor dedicado y acotado, sin espera de cupo ni ejecución en el hilo llamador. En integración con PostgreSQL real y transporte falso, verificá con otra conexión que el turno está visible cuando empieza el correo, que en el worker no hay transacción activa y que el 201 exacto de C-07 llega con el transporte todavía bloqueado. Usá latches, barreras o drenaje controlado con tiempos máximos, nunca `sleep` ni depender de una carrera.
- [ ] 4.2 Con pruebas negativas primero, provocar rollback real **después de publicar** en una transacción válida, y probar en forma separada publicación sin transacción y rechazo de reserva 409 (`RN-TU-01`/`RN-PA-03`). Verificá sin carreras que no hay tarea, mensaje ni registro en ninguno de esos casos, usando un ejecutor controlable y esperando/drenando tareas antes de limpiar fixtures.
- [ ] 4.3 Con fixtures transaccionales (sin emisores de producción), verificar los eventos existentes de cancelación y reprogramación con commit y rollback: aviso/registro únicamente al confirmar, link ausente o nuevo según corresponda, FK correcta y sin cambiar estado ni datos fuera de la fixture. Probar que el cambio de contacto luego de publicar una reserva no cambia el destinatario.
- [ ] 4.4 En TDD, cubrir SMTP fallido con 201 intacto, turno `RESERVADO` y fila `ERROR`; saturación/rechazo del ejecutor de envío y también del ejecutor de auditoría según P1; SMTP aceptado con escritura fallida y timeout ambiguo según P6. Verificá que no hay envío inline, no hay reversión y no hay reenvío automático; si la fila no pudo escribirse, no se informa como guardada. Documentá la ventana post-commit y la falta de outbox y reintentos durables.

## 5. Regresión del sistema

- [ ] 5.1 Ejecutar la suite backend unitaria y de integración con PostgreSQL real y transporte falso, sin omitir tests. Confirmá que `ReservaOnlineIT`, `ReservaEventosIT`, `ReservasConcurrentesApiIT` y `ReservasConcurrentesIT` mantienen el contrato: mismo HTTP íntegro, 201 sin token ni hash, exactamente una reserva ganadora y un aviso para ella cuando hay cupo; la restricción `turno_sin_solapamiento` sigue siendo la fuente de verdad. Registrá resultados y bloqueos del entorno sin declarar verde lo que no se ejecutó.
