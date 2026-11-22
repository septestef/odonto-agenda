# Delta: Gestión del turno por token

## Purpose

Permitir que el paciente consulte, cancele o reprograme su turno sin cuenta desde el link recibido por email. Preservar la confidencialidad del link y la reserva original si la reprogramación no se puede confirmar.

## ADDED Requirements

### Requirement: Consulta pública de un único turno por token

`GET /api/public/turnos/{token}` MUST autorizar únicamente con el token de gestión del turno identificado por `SHA-256(token)`, sin sesión ni DNI. El 200 MUST incluir `inicio`, `fin` con offset de Buenos Aires, `estado`, `prestacion` (`id`, `nombre`), `odontologo` (`slug`, `nombre`, `apellido`, `direccionConsultorio` nullable) y `puedeCancelar`/`puedeReprogramar`, sin datos personales del paciente, ids internos de turno, paciente u odontólogo, ni datos de otros turnos. Las acciones MUST volver a validar sus condiciones aunque el GET las haya indicado como disponibles.

#### Scenario: Token válido y turno futuro
- **GIVEN** un turno futuro `RESERVADO`, con odontólogo y prestación activos y un destinatario de gestión admisible
- **WHEN** se consulta sin sesión con su token válido
- **THEN** la API devuelve 200 con el detalle mínimo y ambas acciones habilitadas, sin exponer el destinatario

#### Scenario: Link anterior, turno histórico o ya iniciado
- **WHEN** se consulta con el token correcto un turno `CANCELADO`, `ATENDIDO`, `AUSENTE` o con `inicio <= ahora`
- **THEN** la API devuelve 200 con sus propios datos y ambas acciones deshabilitadas; el link anterior no sigue ni identifica al sucesor

#### Scenario: Token mal formado o inexistente
- **WHEN** se invoca cualquiera de las rutas de gestión con un segmento de token mal formado, un hash en lugar del token o un token bien formado sin turno asociado
- **THEN** la API devuelve 404 `NO_ENCONTRADO` con el mismo cuerpo genérico, sin eco del segmento ni efectos sobre turnos o avisos; no se reemplazan las rutas existentes de C-07

#### Scenario: Disponibilidad para el slug turnos
- **GIVEN** un odontólogo activo con slug `turnos` y una prestación activa propia
- **WHEN** se hace `GET /api/public/turnos/disponibilidad?prestacionId=<id>&fecha=2026-11-27`
- **THEN** se obtiene la respuesta de disponibilidad de C-07 sin ambigüedad ni intento de consultar un turno por token

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

`POST /api/public/turnos/{token}/reprogramar` MUST aceptar solo `inicio` ISO 8601 con offset y resolver el odontólogo, el paciente y la prestación desde el turno original. El original MUST estar `RESERVADO` y no haber empezado. La prestación y el odontólogo MUST estar activos para moverlo; su baja no impide consultar o cancelar el original futuro. El destino MUST usar la duración vigente de la misma prestación y satisfacer exactamente los filtros de C-07, incluyendo la ocupación del original y la prohibición de elegir su mismo inicio (RN-TU-01, RN-TU-02, RN-TU-03, RN-TU-06, RN-TU-11 y RN-AG-05). La elegibilidad que muestra GET MUST deshabilitar reprogramar cuando el destinatario no es admisible o la prestación o el odontólogo están inactivos, sin revelar el motivo ni chequear disponibilidad puntual.

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

Reprogramar MUST cancelar el original como `PACIENTE` e insertar en la misma transacción un sucesor `RESERVADO` de origen `ONLINE`, con el mismo paciente, odontólogo y prestación, `reprogramado_desde_id` apuntando al original y un nuevo hash de un nuevo token de 32 bytes aleatorios en base64url. MUST responder 204 sin cuerpo ni `Location`. MUST NOT actualizar el contacto ni consumir otro cupo futuro (RN-PA-03). Si falla la operación, incluso después de escribir la cancelación, ambas escrituras MUST revertirse. Violar `turno_sin_solapamiento`, recibir `40P01` o no poder tomar un lock de la escritura MUST responder 409 `RN-TU-01` sin detalles internos (RN-GL-01).

#### Scenario: Reprogramación con el cupo completo
- **GIVEN** dos turnos futuros del mismo DNI y consultorio y un destino libre
- **WHEN** se reprograma uno de ellos con su token
- **THEN** queda el original `CANCELADO`, el sucesor vinculado con un hash distinto y exactamente dos reservas futuras; el token original solo permite consultar el original y el nuevo solo identifica el sucesor

#### Scenario: Fallo del alta tras escribir la cancelación
- **WHEN** el alta falla por la constraint de exclusión luego de ejecutar el UPDATE del original
- **THEN** la API devuelve 409 `RN-TU-01`, el original sigue `RESERVADO` sin marcas de cancelación, y no queda sucesor, aviso ni registro del intento

#### Scenario: Mutaciones simultáneas del mismo original
- **WHEN** compiten dos reprogramaciones, o una cancelación y una reprogramación, del mismo turno `RESERVADO` antes de su inicio
- **THEN** exactamente una confirma y emite un evento, la otra devuelve 409 `TURNO_SOLO_LECTURA` al ver la transición ganadora; nunca hay dos sucesores ni se cancela el sucesor con el link anterior

#### Scenario: Dos originales o una reserva compiten por el destino
- **WHEN** dos originales distintos se mueven al mismo horario, o una reprogramación compite con una reserva válida de C-07 por el mismo destino
- **THEN** exactamente una escritura gana el destino, la otra devuelve 409 `RN-TU-01` y el original de una reprogramación perdedora queda intacto; solo hay aviso de la operación ganadora

### Requirement: Destinatario de gestión estable y fallback cerrado

El destinatario de gestión MUST salir del snapshot del turno, copiado al sucesor. Para permitir reprogramar MUST ser un único buzón admisible según las reglas vigentes de C-08, no simplemente un valor no nulo. Ausencia o inadmisibilidad MUST impedir reprogramar antes de escribir con 409 `REPROGRAMACION_NO_DISPONIBLE`. MUST NOT inferirse el destinatario del `paciente.email` actual, de `notificacion`, de otro turno ni del request. Si la cancelación se confirma sin buzón admisible, MUST emitir el evento de cancelación sin destinatario utilizable y C-08 MUST omitir correo y auditoría del aviso.

#### Scenario: Nuevo contacto del mismo DNI
- **WHEN** otra reserva cambia el contacto del paciente y luego el titular usa el link del turno anterior para moverlo
- **THEN** el destinatario persistido y el email del link nuevo siguen siendo los del turno anterior, nunca los del contacto recién ingresado

#### Scenario: Legado sin snapshot o email aceptado solo por C-07
- **WHEN** se consulta, intenta reprogramar y después cancela un turno futuro con snapshot ausente o no admisible para C-08
- **THEN** GET permite solo cancelar, reprogramar devuelve 409 `REPROGRAMACION_NO_DISPONIBLE` sin cambios ni evento, y cancelar devuelve 204 sin SMTP ni fila de notificación

### Requirement: Precedencia y confidencialidad de la API

Para una solicitud que llega a gestión, MUST aplicarse primero la validación HTTP del cuerpo de reprogramación (400 `VALIDACION` para `inicio` faltante/nulo, 400 `SOLICITUD_INVALIDA` para JSON ilegible, sin offset o con campos extra y 415 para tipo no soportado). Con solicitud admisible, MUST resolver el token (404), rechazar las mutaciones del original ya iniciado (409 `RN-TU-07`) o no `RESERVADO` (409 `TURNO_SOLO_LECTURA`), y en reprogramación verificar activos, destinatario y reglas de agenda en ese orden. Respuestas MUST usar el formato de error uniforme sin eco de valores ni causa de base.

El token en claro y el link completo MUST NOT aparecer en respuestas, redirecciones, logs, trazas ni persistencia. El token nuevo en claro MUST salir únicamente por email; la API y el documento que se sirve en `/turno/{token}` MUST evitar almacenamiento en caché y no enviar la URL como referrer.

#### Scenario: Errores simultáneos
- **WHEN** se manda un cuerpo inválido a un token desconocido, un destino inválido para un original ya empezado con cuerpo bien formado, o se actúa sobre un cancelado antes de su inicio
- **THEN** se priorizan respectivamente 400, 409 `RN-TU-07` y 409 `TURNO_SOLO_LECTURA`, sin efectos ni eco de valores

#### Scenario: Respuestas y diagnósticos sin credencial
- **WHEN** se ejercitan consulta, cancelación, reprogramación y errores con tokens centinela
- **THEN** no hay token, hash o link en cuerpos ni `Location`, ni secreto en logs, auditoría o persistencia; las respuestas de gestión y el documento de link emiten `Cache-Control: no-store` y `Referrer-Policy: no-referrer`
