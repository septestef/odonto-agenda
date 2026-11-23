# Spec Delta

## Purpose

Permitir al odontólogo cancelar turnos futuros propios con liberación atómica del horario, reintentos idempotentes y aviso posterior a la confirmación.

P4/P5/P7/P8/P9 aprobadas y apply autorizado explícitamente el 2026-11-22; primera etapa limitada a preparación/baseline según design.md. Se reutilizan RN-TU-07 y el fallback ERROR_INTERNO existentes, sin RN nueva, sin RN-TU-10 como rechazo ni extensión de RN-SE-02 (gestión por token) a la sesión privada.

## ADDED Requirements

### Requirement: Cancelación privada protegida
`POST /api/turnos/{id}/cancelar` MUST requerir sesión ODONTOLOGO y CSRF válido, sin definir body de entrada ni exigir Content-Type. id MUST ser int64 positivo. No convertible/fuera de int64 MUST responder 400 `SOLICITUD_INVALIDA` / `No pudimos interpretar la solicitud.`; no positivo MUST responder 400 `VALIDACION` / `Hay datos inválidos. Revisalos y probá de nuevo.` con detalle `id` / `Ingresá un identificador mayor a 0.`.

#### Scenario: Identificador inválido
- **WHEN** se pide id=0 o negativo, o un id no convertible a int64
- **THEN** se obtiene respectivamente VALIDACION con detalle id o SOLICITUD_INVALIDA sin detalles y no hay mutación

#### Scenario: Filtros de seguridad
- **WHEN** se cancela con sesión válida sin CSRF o con CSRF inválido
- **THEN** se recibe 403 ACCESO_DENEGADO sin mutación; sin sesión con CSRF válido se recibe 401 NO_AUTENTICADO; sin ambos puede preceder el rechazo CSRF 403, conforme a cadena existente

### Requirement: Pertenencia antes del estado
Superados filtros y validación, recurso ajeno e inexistente MUST responder idéntico 404 `NO_ENCONTRADO` / `No encontramos lo que buscabas.` antes de evaluar tiempo/estado. Identificadores de dueño en query, headers o body MUST NOT determinar la sesión ni la pertenencia.

#### Scenario: Ajeno indistinguible de inexistente
- **WHEN** A intenta cancelar un turno de B pasado o finalizado y luego un id inexistente, con entrada y credenciales válidas
- **THEN** ambas respuestas son el mismo 404 y ningún turno ni notificación cambia

#### Scenario: IDs inyectados
- **WHEN** A envía identificadores de B en query/header/body al cancelar un turno propio o ajeno
- **THEN** el resultado depende del dueño de sesión A y del turno de la ruta, nunca de los identificadores recibidos

### Requirement: Cancelación de un turno reservado futuro
Tras filtros, validación, pertenencia y lock, MUST capturarse un único ahora del servidor. Un RESERVADO con inicio > ahora MUST pasar a CANCELADO con `canceladoPor=ODONTOLOGO` y `canceladoEn` de ese mismo ahora, y responder 204 sin cuerpo tras commit. No aplica mínimo de 24 h ni máximo de 60 días para cancelar.

#### Scenario: Futuro cercano
- **WHEN** se cancela un RESERVADO que comienza en una hora
- **THEN** se recibe 204 vacío y se persiste CANCELADO por ODONTOLOGO con instante del servidor

### Requirement: Repetición idempotente
Un turno ya CANCELADO MUST responder 204 idempotente aun con inicio pasado, evaluado antes que el tiempo, conservando todos los datos y autor/fecha originales, sin evento. Ninguna respuesta de cancelación, incluidos errores, MUST exponer token/hash (`token_hash`/`tokenHash`) ni email de gestión (`email_gestion`/`emailGestion`), sus valores o ecos en detalles.

#### Scenario: Repetición incluso pasada
- **WHEN** se repite el POST de un turno ya cancelado por PACIENTE u ODONTOLOGO y su inicio ya pasó
- **THEN** se recibe 204 vacío sin reemplazar canceladoPor/canceladoEn ni generar otro evento/aviso

### Requirement: Rechazo de turnos ya iniciados
Cualquier estado distinto de CANCELADO con inicio <= ahora MUST responder 409 `RN-TU-07` / `No podés cancelar un turno que ya empezó.`, incluidos ATENDIDO/AUSENTE e igualdad. Esta regla se evalúa antes que la inconsistencia de ATENDIDO/AUSENTE con inicio > ahora. Rechazos, inconsistencias y repeticiones MUST dejar estado y metadatos intactos.

#### Scenario: Pasado e igualdad
- **WHEN** inicio de un RESERVADO, ATENDIDO o AUSENTE es anterior o igual al ahora evaluado bajo exclusión
- **THEN** se recibe 409 RN-TU-07, sin cancelación ni aviso

### Requirement: Inconsistencia interna de asistencia futura
Un ATENDIDO/AUSENTE propio con inicio > ahora posterior al lock MUST tratarse como inconsistencia interna del estado persistido, imposible por el flujo normal de marcado de asistencia, no como rechazo esperado de negocio. MUST responder mediante el fallback existente 500 application/json con exactamente `{"codigo":"ERROR_INTERNO","mensaje":"Ocurrió un error inesperado. Probá de nuevo en unos minutos."}`.

#### Scenario: ATENDIDO futuro inconsistente
- **WHEN** el dueño intenta cancelar ATENDIDO con inicio estrictamente futuro respecto del único ahora posterior al lock
- **THEN** recibe el 500 JSON genérico exacto, queda diagnóstico sanitizado en log y estado/metadatos siguen intactos, sin mutación, flush de actualización, evento, aviso, SMTP ni auditoría de notificación

### Requirement: Inconsistencia sin efectos
Ante la inconsistencia de asistencia futura MUST NOT reparar/normalizar automáticamente, mutar, realizar flush de actualización, construir/publicar evento ni generar aviso, intento SMTP o auditoría de notificación. Estado y todos los metadatos MUST permanecer intactos.

#### Scenario: AUSENTE futuro inconsistente
- **WHEN** el dueño intenta cancelar AUSENTE con inicio estrictamente futuro respecto del único ahora posterior al lock
- **THEN** recibe el mismo 500 JSON genérico, sin detalles internos ni datos sensibles en respuesta o log y sin reparación, cambios persistidos, flush de actualización, evento o notificación

### Requirement: Diagnóstico sanitizado de la inconsistencia
La inconsistencia MUST dejar diagnóstico local sanitizado de marcador fijo y enum permitido, sin ids, datos personales, emails, tokens/hashes ni volcado de entidad o excepción con campos. La excepción propagada al advice MUST tener mensaje fijo sin datos ni causas sensibles, porque el fallback privado registra la excepción completa; MUST NOT modificarse advice ni infraestructura/logging compartidos.

#### Scenario: Log de inconsistencia sin datos sensibles
- **WHEN** se produce la inconsistencia de ATENDIDO o AUSENTE futuro al cancelar
- **THEN** el log contiene solo el marcador fijo y el enum, y la excepción registrada por el advice tiene mensaje fijo, sin ids, emails, tokens/hashes ni causas con campos

### Requirement: Lock de fila y protocolo de cancelación
La cancelación MUST seguir el protocolo de cancelación C-09 adaptando autorización a id + dueño de sesión: primera materialización por consulta bloqueante solo de fila Turno con FOR UPDATE, sin joins ni entidad previamente cacheada; luego capturar un único ahora, revalidar estado/tiempo, mutar, hacer flush y publicar. MUST NOT pedir después lock/upsert de paciente ni advisory, tampoco para construir el aviso.

#### Scenario: Gestión por token gana el original
- **WHEN** cancelar o reprogramar por token confirma primero sobre el mismo original antes de que C-12 obtenga su fila bloqueada
- **THEN** C-12 observa CANCELADO y responde 204 idempotente, sin modificar autor/fecha ni sucesor ni publicar otro evento

#### Scenario: Cancelación privada gana el original
- **WHEN** C-12 confirma primero mientras cancelar o reprogramar por token espera sobre el mismo original
- **THEN** C-09 revalida bajo su lock y rechaza según su contrato con RN-SE-02 si sigue futuro o RN-TU-07 si empezó, sin segundo cambio/evento; la carrera no invierte locks de paciente/advisory y fila

### Requirement: Liberación atómica del horario
La cancelación MUST ser transaccional y mantener la garantía de exclusión de turnos. El horario MUST quedar libre para otras transacciones al commit, sujeto a las demás reglas de reserva.

#### Scenario: Liberación efectiva
- **WHEN** confirma la cancelación y luego otra transacción reserva ese horario cumpliendo las reglas restantes
- **THEN** la constraint permite el nuevo turno y el original sigue CANCELADO

#### Scenario: Rollback real
- **WHEN** se modifica estado y se publica el evento, pero después la transacción se revierte
- **THEN** el turno conserva RESERVADO, sigue ocupando horario y no hay entrega AFTER_COMMIT, correo ni fila de notificación

### Requirement: Cancelaciones concurrentes del mismo turno
Dos POST privados concurrentes sanos del mismo turno MUST producir 204/204, exactamente una transición y un evento; el segundo MUST revalidar estado después de esperar. Si la espera lleva al inicio del turno, un RESERVADO todavía no cancelado MUST rechazarse según RN-TU-07 usando el reloj posterior a la espera.

#### Scenario: Dos solicitudes del mismo turno
- **WHEN** dos transacciones autenticadas cancelan concurrentemente el mismo RESERVADO futuro y no hay fallas técnicas
- **THEN** ambas responden 204, hay una sola transición con un solo evento y, con consumidor/transporte admitidos, un solo intento de aviso

#### Scenario: El reloj avanza durante la espera
- **WHEN** una cancelación espera exclusión sobre un turno todavía RESERVADO y la obtiene cuando ya comenzó
- **THEN** responde RN-TU-07 y no usa el ahora anterior a la espera

### Requirement: Fallas técnicas de lock en cancelación
Fallas técnicas de lock MUST responder 409 RN-TU-01 según contrato compartido vigente, sin cambios confirmados ni aviso; otros fallos inesperados MUST conservar contrato 500 ERROR_INTERNO.

#### Scenario: Lock no disponible
- **WHEN** la cancelación privada recibe 40P01, 55P03 o una falla de lock pesimista, incluso al cerrar la transacción
- **THEN** responde 409 RN-TU-01 sin transición confirmada ni aviso; una falla no clasificada responde 500 ERROR_INTERNO

### Requirement: Cancelación independiente del email
La transición MUST NOT depender de un email: se notifica exclusivamente a turno.email_gestion admisible y, si es null/no admisible, se emite sin email utilizable y C-08 descarta sin SMTP ni auditoría (delta eventos-turno).

#### Scenario: Sin email de gestión utilizable
- **WHEN** se cancela un RESERVADO futuro propio cuyo email_gestion es null o no admisible
- **THEN** responde 204, el turno queda CANCELADO y el evento se emite sin email utilizable
