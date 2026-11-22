# Delta: Gestión del turno por token

## Purpose

Permitir que el paciente consulte, cancele o reprograme su turno sin cuenta desde el link recibido por email. Preservar la confidencialidad del link y la reserva original si la reprogramación no se puede confirmar.

## ADDED Requirements

### Requirement: Consulta pública de un único turno por token

`GET /api/public/turnos/{token}` MUST autorizar solo por `SHA-256(token)`, sin sesión ni DNI, y exponer solo el detalle mínimo del turno autorizado. MUST admitir como token de ruta un segmento completo de 43 caracteres ASCII base64url sin padding (`[A-Za-z0-9_-]{43}`). Las acciones MUST revalidar sus condiciones aunque el GET las haya indicado como disponibles.

#### Scenario: Token válido y turno futuro
- **GIVEN** un turno futuro `RESERVADO`, con odontólogo y prestación activos y un destinatario de gestión admisible
- **WHEN** se consulta sin sesión con su token válido
- **THEN** la API devuelve 200 con `inicio` y `fin` con offset de Buenos Aires, `estado`, `prestacion` (`id`, `nombre`), `odontologo` (`slug`, `nombre`, `apellido`, `direccionConsultorio` nullable) y `puedeCancelar`/`puedeReprogramar` verdaderos; no expone el destinatario ni otros datos del paciente, ids internos de turno, paciente u odontólogo, ni datos de otros turnos

#### Scenario: Link anterior, turno histórico o ya iniciado
- **WHEN** se consulta con el token correcto un turno `CANCELADO`, `ATENDIDO`, `AUSENTE` o con `inicio <= ahora`
- **THEN** la API devuelve 200 con sus propios datos y ambas acciones deshabilitadas; el link anterior no sigue ni identifica al sucesor

#### Scenario: Token mal formado o inexistente
- **WHEN** llega a MVC un GET con segmento mal formado o un hash, sin otro mapeo ni recurso real, un GET con token bien formado sin turno asociado, o un POST de gestión con entrada HTTP admisible y token inválido o desconocido
- **THEN** la API devuelve 404 `application/json` con el mismo cuerpo `{ "codigo": "NO_ENCONTRADO", "mensaje": "No encontramos lo que buscabas." }`, sin HTML de la SPA, eco del segmento ni efectos sobre turnos o avisos; no se alteran los rechazos previos a MVC

#### Scenario: Disponibilidad para el slug turnos
- **GIVEN** un odontólogo activo con slug `turnos` y una prestación activa propia
- **WHEN** se hace `GET /api/public/turnos/disponibilidad?prestacionId=<id>&fecha=2026-11-27`
- **THEN** se obtiene la respuesta de disponibilidad de C-07 sin ambigüedad ni intento de consultar un turno por token; sin los parámetros requeridos devuelve 400 `SOLICITUD_INVALIDA` de C-07

#### Scenario: Perfil y métodos de reserva para el slug turnos
- **GIVEN** un odontólogo activo con slug `turnos`
- **WHEN** se llama `GET /api/public/turnos`, `POST /api/public/turnos/turnos` con una reserva válida, o `GET /api/public/turnos/turnos`
- **THEN** responde respectivamente 200 con el perfil, 201 con el contrato de reserva y 405 `METODO_NO_PERMITIDO`; ninguna de esas rutas se trata como gestión por token

### Requirement: Cancelación del paciente antes del inicio

`POST /api/public/turnos/{token}/cancelar` MUST cambiar únicamente un turno `RESERVADO` con `ahora < inicio` a `CANCELADO`, con `cancelado_por = PACIENTE` y `cancelado_en` del reloj del servidor. MUST responder 204 sin cuerpo y liberar la ocupación al confirmar, sin regla de 24 h y sin depender de que el consultorio o la prestación aún se ofrezcan para nuevas reservas (RN-TU-05, IN-01).

#### Scenario: Cancelación a último momento
- **GIVEN** un turno `RESERVADO` a menos de 24 h de empezar
- **WHEN** se cancela con su token y confirma la transacción
- **THEN** devuelve 204, deja las marcas de cancelación y libera el intervalo; si otros filtros de C-07 impiden ofrecerlo, esos filtros no se suspenden

#### Scenario: Cancelación de un turno que empieza en este instante
- **WHEN** se intenta cancelar con el reloj del servidor en el instante de inicio o después
- **THEN** devuelve 409 `RN-TU-07`, sin cambios ni evento

### Requirement: Elegibilidad y horarios para mover el turno

`POST /api/public/turnos/{token}/reprogramar` MUST aceptar solo `inicio` ISO 8601 con offset. MUST derivar odontólogo, paciente y prestación del turno original, que debe estar `RESERVADO` y no haber empezado. La prestación y el odontólogo MUST estar activos para moverlo. Los indicadores de GET MUST deshabilitar reprogramar si alguno está inactivo o el buzón no es admisible, sin revelar el motivo ni chequear disponibilidad puntual.

#### Scenario: Duración y filtros de la misma prestación
- **WHEN** se pide mover un turno de una prestación cuya duración cambió
- **THEN** el original conserva su duración guardada y el destino usa la duración vigente de la misma prestación y exactamente los filtros de C-07, incluida la ocupación del original; no se admite el mismo inicio (RN-TU-01, RN-TU-02, RN-TU-03, RN-TU-06, RN-TU-11 y RN-AG-05)

#### Scenario: Bordes del destino con reloj fijo
- **WHEN** se pide un destino libre y válido exactamente a 24 h o en el último día local del horizonte
- **THEN** el borde es admisible; antes de 24 h (incluso un destino pasado) devuelve 422 `RN-TU-03`, y un día después del horizonte devuelve 422 `RN-TU-11`

#### Scenario: Inicio igual, cruce propio y grilla pública
- **GIVEN** que el inicio original está dentro de la ventana y la agenda vigente
- **WHEN** se pide el mismo instante con otro offset, un inicio distinto que se cruza con el original, o un inicio contiguo ofrecido por C-07
- **THEN** los dos primeros devuelven 409 `RN-TU-01` porque el original también ocupa lugar, y el contiguo es admisible; un inicio fuera de paso, franja, cierre o con cruce de bloqueo devuelve 422 `RN-AG-05`

#### Scenario: Prestación o consultorio dados de baja
- **WHEN** se intenta mover un turno futuro `RESERVADO` cuyo odontólogo o prestación no está activo
- **THEN** se devuelve 404 `NO_ENCONTRADO`; el GET conserva el detalle y permite cancelar antes del inicio

### Requirement: Un único sucesor o ningún cambio

Reprogramar MUST cancelar el original como `PACIENTE` e insertar un sucesor `RESERVADO/ONLINE` en la misma transacción, sin actualizar contacto ni consumir otro cupo (RN-PA-03). MUST responder 204 sin cuerpo ni `Location`. Ante cualquier falla, incluso tras escribir la cancelación, MUST revertir ambas escrituras.

#### Scenario: Reprogramación con el cupo completo
- **GIVEN** dos turnos futuros del mismo DNI y consultorio y un destino libre
- **WHEN** se reprograma uno de ellos con su token
- **THEN** queda el original `CANCELADO` y el sucesor del mismo paciente, odontólogo y prestación, con `reprogramado_desde_id` al original. El sucesor solo guarda el SHA-256 de su nuevo token de 32 bytes aleatorios en base64url sin padding, distinto del hash original. Quedan exactamente dos reservas futuras; el token original solo permite consultar el original y el nuevo solo identifica el sucesor

#### Scenario: Fallo del alta tras escribir la cancelación
- **WHEN** el alta falla por la constraint de exclusión luego de ejecutar el UPDATE del original
- **THEN** la API devuelve 409 `RN-TU-01`, el original sigue `RESERVADO` sin marcas de cancelación, y no queda sucesor, aviso ni registro del intento

#### Scenario: Mutaciones simultáneas del mismo original
- **WHEN** compiten dos reprogramaciones, o una cancelación y una reprogramación, del mismo turno `RESERVADO` antes de su inicio
- **THEN** exactamente una confirma y emite un evento; la otra, al ver la transición ganadora, devuelve 409 `RN-SE-02` si el original sigue siendo futuro o 409 `RN-TU-07` si ya empezó. Nunca hay dos sucesores ni se cancela el sucesor con el link anterior

#### Scenario: Dos originales o una reserva compiten por el destino
- **WHEN** dos originales distintos se mueven al mismo horario, o una reprogramación compite con una reserva válida de C-07 por el mismo destino
- **THEN** exactamente una escritura gana el destino, la otra devuelve 409 `RN-TU-01` y el original de una reprogramación perdedora queda intacto; solo hay aviso de la operación ganadora

### Requirement: Conflictos de escritura de turnos

Una violación de la constraint `turno_sin_solapamiento`, un deadlock `40P01` o una falla de lock de la escritura MUST devolver 409 `RN-TU-01` sin detalles internos (RN-GL-01).

#### Scenario: PostgreSQL rechaza una escritura de gestión
- **WHEN** PostgreSQL devuelve `23P01` de esa constraint, `40P01` o falla al tomar un lock de la escritura
- **THEN** responde 409 con solo `codigo` y `mensaje` de `RN-TU-01`, sin cambios confirmados ni detalles de base

### Requirement: Destinatario de gestión estable y fallback cerrado

El destinatario MUST salir del snapshot del turno y copiarse al sucesor, nunca de `paciente.email`, `notificacion`, otro turno ni el request. Reprogramar MUST requerir un único buzón admisible según C-08, no solo un valor no nulo. Si falta o no es admisible, MUST rechazarse antes de escribir o generar token con 409 `RN-SE-04`.

#### Scenario: Nuevo contacto del mismo DNI
- **WHEN** otra reserva cambia el contacto del paciente y luego el titular usa el link del turno anterior para moverlo
- **THEN** el destinatario persistido y el email del link nuevo siguen siendo los del turno anterior, nunca los del contacto recién ingresado

#### Scenario: Legado sin snapshot o email aceptado solo por C-07
- **WHEN** se consulta, intenta reprogramar y después cancela un turno futuro con snapshot ausente o no admisible para C-08
- **THEN** GET habilita solo la cancelación, reprogramar devuelve 409 `{ "codigo": "RN-SE-04", "mensaje": "No podés reprogramar este turno desde el link." }` sin cambios ni evento, y cancelar devuelve 204 y emite el evento sin destinatario utilizable; C-08 no intenta SMTP ni registrar una notificación

### Requirement: Precedencia de rechazos de gestión

Una solicitud que llega a gestión MUST aplicar en orden: validación HTTP del cuerpo de reprogramación, resolución del token (404), `inicio <= ahora` (409 `RN-TU-07`, sin importar el estado), original futuro no `RESERVADO` (409 `RN-SE-02`) y, solo al reprogramar, activos, destinatario y agenda. Los rechazos MUST usar el JSON uniforme, sin eco de valores ni causas de base; los 409 de negocio llevan solo `{codigo, mensaje}`.

#### Scenario: Cuerpo de reprogramación no admisible
- **WHEN** falta `inicio` o es nulo, el JSON es ilegible, no trae offset o contiene campos extra, o el tipo de contenido no está soportado
- **THEN** devuelve respectivamente 400 `VALIDACION`, 400 `SOLICITUD_INVALIDA` o 415 `SOLICITUD_INVALIDA`, antes de buscar el token

#### Scenario: Errores simultáneos
- **WHEN** se manda un cuerpo inválido a un token desconocido, un destino inviable con cuerpo admisible para un original ya iniciado aunque esté cancelado, o se actúa sobre un cancelado antes de su inicio
- **THEN** se priorizan respectivamente 400, 409 `RN-TU-07` y 409 `RN-SE-02`, sin efectos ni eco de valores

### Requirement: Confidencialidad de la API de gestión

El token en claro y el link completo MUST NOT aparecer en respuestas, redirecciones, logs, trazas ni persistencia. El token nuevo en claro MUST salir únicamente por email; la API y el documento servido en `/turno/{token}` MUST evitar caché y no enviar la URL como referrer.

#### Scenario: Respuestas y diagnósticos sin credencial
- **WHEN** se ejercitan consulta, cancelación, reprogramación y errores con tokens centinela
- **THEN** no hay token, hash o link en cuerpos ni `Location`, ni secreto en logs, auditoría o persistencia; las respuestas de gestión y el documento de link emiten `Cache-Control: no-store` y `Referrer-Policy: no-referrer`
