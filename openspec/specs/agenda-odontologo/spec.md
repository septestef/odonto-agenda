# agenda-odontologo Specification

## Purpose
Permitir al odontólogo consultar sus turnos diarios y semanales con datos del paciente y la prestación, sin revelar agendas ajenas.

## Requirements

### Requirement: Consulta privada de agenda
`GET /api/turnos?desde=&hasta=` MUST requerir sesión con rol ODONTOLOGO y limitar todos los resultados al dueño de esa sesión. MUST ignorar identificadores de odontólogo recibidos en query, headers o cuerpo como fuente de pertenencia. MUST responder 200 JSON con array, `[]` si no hay turnos. GET MUST NOT requerir CSRF. Sin sesión MUST responder 401 `NO_AUTENTICADO`; con sesión sin rol permitido MUST responder 403 `ACCESO_DENEGADO`, con formato uniforme y sin redirect.

#### Scenario: Dos agendas con los mismos horarios
- **WHEN** A consulta un rango que tiene turnos de A y B e incluye `odontologoId` de B en query y header
- **THEN** recibe solo turnos de A, con totales implícitos y datos exclusivamente propios

#### Scenario: Agenda vacía
- **WHEN** un odontólogo con sesión válida consulta un rango válido sin turnos propios
- **THEN** recibe 200 con `[]`

#### Scenario: Lectura protegida
- **WHEN** se consulta sin sesión o con sesión que no tiene rol ODONTOLOGO
- **THEN** se recibe respectivamente 401 `NO_AUTENTICADO` o 403 `ACCESO_DENEGADO`, sin datos de agenda

### Requirement: Rango local semiabierto por intersección
`desde` y `hasta` MUST ser fechas ISO `YYYY-MM-DD` obligatorias. El rango MUST interpretarse como `[desde a inicio de día, hasta a inicio de día)` en zona de negocio Buenos Aires, con entre 1 y 31 días calendario. MUST incluir todo turno que cumpla `inicio < hasta && fin > desde`, sin reglas de anticipación 24 h ni horizonte de reserva 60 días para lectura. MUST admitir históricos.

#### Scenario: Vista diaria y cruce de medianoche
- **WHEN** se consulta desde 2026-11-25 hasta 2026-11-26 y un turno inicia el 24 a las 23:45 local y termina el 25 a las 00:15 local
- **THEN** se incluye ese turno; se excluye uno que termina exactamente el 25 a las 00:00 y uno que comienza exactamente el 26 a las 00:00

#### Scenario: Semana y conversión UTC
- **WHEN** se consulta desde 2026-11-23 hasta 2026-11-30 en Buenos Aires
- **THEN** el intervalo efectivo es `[2026-11-23T03:00:00Z,2026-11-30T03:00:00Z)` independientemente de la zona del sistema

#### Scenario: Lectura fuera de ventana de reserva
- **WHEN** se consulta un rango histórico válido o un rango válido a más de 60 días del presente
- **THEN** se responde 200 sin aplicar reglas de reserva de 24 h ni de 60 días

### Requirement: Validación del rango de agenda
Ausencia, vacío o formato/conversión inválidos de `desde` o `hasta` MUST responder 400 `SOLICITUD_INVALIDA`, mensaje `No pudimos interpretar la solicitud.`, sin detalles. Orden inválido MUST responder 400 `VALIDACION`, mensaje `Hay datos inválidos. Revisalos y probá de nuevo.`, detalle `hasta` / `Elegí una fecha posterior a desde.`; longitud mayor a 31 MUST usar el mismo código/mensaje con detalle `hasta` / `Elegí un rango de hasta 31 días.`. MUST evaluar orden antes que longitud.

#### Scenario: Límites de longitud
- **WHEN** se consultan rangos de 1, 31, 0, negativos y 32 días calendario
- **THEN** 1 y 31 se aceptan, 0/negativos se rechazan por orden y 32 por longitud con los detalles definidos

#### Scenario: Entrada no interpretable
- **WHEN** falta alguna fecha o se envía vacía, con hora/offset o con fecha imposible
- **THEN** se responde 400 `SOLICITUD_INVALIDA` sin detalles

### Requirement: Forma de la respuesta de agenda
Cada elemento MUST contener exactamente `id`, `inicio`, `fin`, `estado`, `origen`, `paciente`, `prestacion`, `canceladoPor`, `canceladoEn` según esquema y ejemplo P2 de design.md. `paciente` MUST contener `id,dni,nombre,apellido,telefono,email`; email MUST ser el contacto mutable vigente de paciente, nunca el snapshot email_gestion. `prestacion` MUST contener `id,nombre,duracionMinutos`, con duración real entre inicio y fin.

#### Scenario: Duración de prestación editada
- **WHEN** un turno conserva 30 minutos entre inicio y fin pero su prestación vigente ahora dura 45
- **THEN** el DTO indica 30 minutos, el nombre vigente y el horario persistido; 12:00Z se muestra como 09:00-03:00 en Buenos Aires

### Requirement: Contenido y orden de la agenda
La agenda MUST incluir todos los estados y orígenes, incluso cancelados y prestaciones inactivas, ordenados por inicio ascendente y luego id ascendente. Fechas MUST ser ISO offset-date-time en zona de negocio; metadatos de cancelación MUST ser null cuando ausentes.

#### Scenario: Agenda con estados y orígenes mixtos
- **WHEN** hay turnos RESERVADO, CANCELADO, ATENDIDO y AUSENTE, de origen ONLINE y MANUAL, y dos comparten inicio
- **THEN** todos aparecen ordenados por inicio/id, con los campos contratados y sin secretos

### Requirement: Agenda sin secretos
La agenda MUST NOT exponer token, hash (`token_hash`/`tokenHash`), email de gestión (`email_gestion`/`emailGestion`), identificador de dueño, auditoría ni metadatos internos de persistencia, tampoco sus valores reservados en detalle anidado, listados o errores.

#### Scenario: Turno cancelado y contactos vigentes
- **WHEN** se lista un turno cancelado cuyo paciente cambió su email y difiere del email_gestion congelado del turno
- **THEN** se informa el contacto vigente y los metadatos originales de cancelación, sin token/hash ni email_gestion (claves o valores centinela distintos del contacto mutable)

### Requirement: Vista inicial y navegación de la agenda

La interfaz MUST exponer la ruta `/agenda` con un selector Día/Semana y navegación anterior, "Hoy" y siguiente. En la primera carga MUST elegir la vista diaria cuando el ancho del viewport sea menor a 720 px y la vista semanal cuando sea mayor o igual a 720 px. La fecha inicial MUST ser la fecha actual en `America/Argentina/Buenos_Aires`. La vista diaria MUST pedir `GET /api/turnos?desde=F&hasta=F+1`; la semanal MUST pedir `desde` igual al lunes de esa semana y `hasta` siete días después. Anterior/siguiente MUST desplazar un día o una semana según la vista activa; "Hoy" MUST volver a la fecha actual y MUST estar deshabilitado cuando ya está en ella. Cambiar de vista o de fecha MUST conservar la otra elección mientras la vista siga montada.

#### Scenario: Primera carga en celular

- **WHEN** se abre `/agenda` con un viewport de 390 px
- **THEN** se muestra la vista diaria de la fecha local de Argentina, con "Día" pulsado y la consulta `?desde=YYYY-MM-DD&hasta=YYYY-MM-DD+1`

#### Scenario: Primera carga en escritorio

- **WHEN** se abre `/agenda` con un viewport de 1440 px
- **THEN** se muestra la vista semanal que empieza el lunes local, con "Semana" pulsado y una consulta de siete días

#### Scenario: Navegación y vuelta a hoy

- **WHEN** se avanza un día o una semana y después se toca "Hoy"
- **THEN** el rango solicitado se desplaza en esa unidad y "Hoy" vuelve a la fecha actual del negocio, quedando deshabilitado

### Requirement: Contenido visible de cada turno

Para cada turno devuelto, la interfaz MUST mostrar el horario local de inicio y fin, nombre y apellido del paciente, nombre de la prestación, su `duracionMinutos` real, el estado y el origen (`ONLINE`/`MANUAL`) con etiqueta textual, nunca solo con color. Un turno `CANCELADO` MUST mostrarse tachado y con `canceladoPor` cuando esté disponible. Los turnos con inicio anterior o igual al "ahora" visual MUST verse atenuados y MUST NO ofrecer la acción Cancelar. La interfaz MUST NO mostrar tokens, hashes, `email_gestion`, identificador del odontólogo ni metadatos de auditoría.

#### Scenario: Turno online cancelado por el paciente

- **WHEN** la agenda devuelve un turno `CANCELADO` con `canceladoPor = PACIENTE` y origen `ONLINE`
- **THEN** la fila aparece tachada, con la etiqueta "Cancelado", "canceló el paciente" y la etiqueta "Online"

#### Scenario: Turno en curso

- **WHEN** el "ahora" visual cae dentro del intervalo de un turno `RESERVADO`
- **THEN** el turno se ve atenuado, se marca como en curso y no expone el botón Cancelar

#### Scenario: Datos sensibles del contrato

- **WHEN** una respuesta incluyera claves o valores de token, hash o email de gestión
- **THEN** la interfaz no los renderiza en la lista, el detalle, los diálogos ni los avisos de error

### Requirement: Franjas, huecos, bloqueos y línea "ahora"

La interfaz MUST combinar los turnos con los horarios y bloqueos del odontólogo para mostrar las franjas de atención agrupadas por período, los huecos "libre" entre eventos dentro de una franja y el texto "fuera del horario de atención" entre franjas. Los huecos MUST NO presentarse como horarios reservables. Los bloqueos MUST visualizarse con su `motivo` y con sus `turnosAfectados` marcados, sin cancelarlos automáticamente. Cuando el rango visible incluye la fecha actual, la interfaz MUST dibujar la línea "ahora" en la posición horaria correspondiente.

#### Scenario: Día con franjas, bloqueo y huecos

- **WHEN** el odontólogo atiende 09:00–13:00 y 16:00–19:00, hay un bloqueo 11:00–12:00 con un turno adentro y no hay turnos a las 10:15
- **THEN** la vista muestra ambas franjas, el hueco "libre", el bloqueo con su aviso, el turno afectado marcado y "13:00 – 16:00 · fuera del horario de atención"

#### Scenario: Semana con turnos dentro de bloqueos

- **WHEN** dos turnos de la semana se superponen con bloqueos
- **THEN** la grilla los marca con el borde de aviso y muestra un resumen que invita a revisarlos o cancelarlos, sin modificar su estado

### Requirement: Detalle del turno

Al elegir un turno, la interfaz MUST abrir su detalle: panel fijo en la vista diaria de escritorio, hoja desde abajo en celular y cajón a la derecha en la vista semanal de escritorio. El detalle MUST mostrar nombre y apellido, DNI, teléfono y email del paciente, nombre y duración de la prestación, horario local, estado, origen y `canceladoPor`/`canceladoEn` cuando corresponda, usando solo campos del contrato de C-12. Si el contrato no expone alguno de esos campos, la fila correspondiente MUST omitirse sin cambiar la estructura visual. El detalle MUST NO exponer secretos de gestión.

#### Scenario: Detalle con todos los campos contratados

- **WHEN** se toca un turno `RESERVADO` de origen `ONLINE` desde la vista diaria en escritorio
- **THEN** se abre el panel fijo con paciente, DNI, teléfono, email, prestación y duración, horario, estado y origen

#### Scenario: Contrato con menos datos del paciente

- **WHEN** el DTO del turno no trae teléfono del paciente
- **THEN** no se muestra esa fila y el resto del panel conserva su estructura y acciones

### Requirement: Estados de carga, vacío y error de la agenda

Mientras se cargan los datos, la interfaz MUST mostrar un estado de carga. Si no hay horarios cargados, MUST mostrar el estado "Todavía no cargaste tus horarios" con acceso a la configuración. Si hay franjas pero no hay turnos en el rango, MUST mostrar "No tenés turnos este día" (o el equivalente semanal). Si el día no tiene franjas, MUST mostrar que no se atiende ese día. Ante fallo de red, 500 `ERROR_INTERNO` o cualquier respuesta inesperada del rango, MUST mostrar "No pudimos cargar la agenda" con una acción Reintentar; los 400 del contrato nunca deben aparecer con rangos generados por la interfaz y, si llegaran, MUST tratarse como ese estado de error.

#### Scenario: Sin horarios cargados

- **WHEN** `GET /api/horarios` devuelve una lista vacía
- **THEN** la agenda muestra el estado "Todavía no cargaste tus horarios" con un enlace a `/config`

#### Scenario: Fallo al cargar el rango

- **WHEN** `GET /api/turnos` falla por red o responde 500 `ERROR_INTERNO`
- **THEN** se muestra el aviso "No pudimos cargar la agenda", el botón Reintentar y no se dibuja una agenda parcial

### Requirement: US-015 — Consulta privada y de solo lectura para WhatsApp

`GET /api/turnos/{id}/whatsapp` MUST requerir sesión con rol `ODONTOLOGO`, sin requerir CSRF, y resolver por `id` y dueño de la sesión, nunca por identificadores de dueño recibidos. MUST devolver 401 `NO_AUTENTICADO` sin sesión, 403 `ACCESO_DENEGADO` sin rol y el mismo 404 `NO_ENCONTRADO` para recurso ajeno o inexistente antes de evaluar estado o contacto. Para `id` inválido, MUST usar las convenciones C-12 de int64 positivo. El GET MUST NOT mutar datos, crear notificaciones, consultar WhatsApp ni emitir mensajes.

#### Scenario: Dueño de sesión y CSRF
- **WHEN** el dueño consulta un turno propio elegible con sesión válida y sin header ni cookie CSRF, incluso si declara otro dueño en query o headers
- **THEN** recibe 200 y no cambia ningún turno, paciente ni registro de notificación

#### Scenario: Ajeno indistinguible de inexistente
- **WHEN** un odontólogo autenticado consulta un turno ajeno aunque esté cancelado o tenga datos faltantes, y luego un id inexistente
- **THEN** ambas consultas reciben el mismo 404 JSON `NO_ENCONTRADO` sin revelar estado ni contacto

#### Scenario: Credenciales e identificadores no admisibles
- **WHEN** se consulta sin sesión, con sesión sin el rol, o con una sesión válida y un id no convertible/fuera de rango int64 o no positivo
- **THEN** se recibe respectivamente 401 `NO_AUTENTICADO`, 403 `ACCESO_DENEGADO`, 400 `SOLICITUD_INVALIDA` o 400 `VALIDACION` con detalle `id` según C-12, sin efectos

### Requirement: US-015 — Elegibilidad y composición segura del mensaje manual

Solo un turno propio `RESERVADO` con `inicio > ahora` del servidor, de cualquier origen, MUST habilitar la generación (RN-NO-06) si el teléfono vigente del paciente cumple el mismo formato E.164 que valida la reserva (`^\+[1-9][0-9]{7,14}$`) y la dirección vigente del consultorio está informada (RN-NO-07). El éxito MUST devolver 200 JSON `{ "url": "https://wa.me/{telefono}?text={mensaje}" }` con `Cache-Control: no-store`: host y esquema fijos, teléfono en dígitos ASCII sin `+`, texto codificado una vez en UTF-8. El mensaje MUST contener fecha y hora en `America/Argentina/Buenos_Aires`, prestación y dirección; MUST NOT incluir nombre del paciente, DNI, email, otros datos personales del paciente ni credenciales de gestión. El sistema MUST NOT adivinar país, normalizar un número nacional a internacional ni sustituir teléfono o dirección.

#### Scenario: Texto con caracteres especiales y cambio de fecha
- **WHEN** el turno inicia a `2026-11-27T01:30:00Z`, el teléfono internacional válido está informado y la prestación o la dirección contiene tildes, `&`, `+`, `#` o `%`
- **THEN** la URL apunta únicamente a `https://wa.me/` con el número sin `+` en el path y un solo `text`; al decodificarlo una vez se lee el mensaje en español para el 26/11 a las 22:30 con los caracteres originales, sin parámetros adicionales, PII extra ni token

#### Scenario: Estado y comienzo del turno
- **WHEN** el dueño consulta un turno cancelado, atendido, ausente o un reservado con `inicio <= ahora`, aunque tenga datos correctos
- **THEN** se devuelve 409 `{ "codigo": "RN-NO-06", "mensaje": "Solo podés preparar un WhatsApp para un turno reservado que todavía no empezó." }` sin URL

#### Scenario: Contacto o dirección no utilizables
- **WHEN** el turno propio reservado futuro tiene teléfono ausente o que no cumple el formato E.164 de la reserva (por ejemplo `3511234567`), o tiene teléfono válido pero dirección nula, vacía o en blanco
- **THEN** se devuelve 422 `RN-NO-07`, sin URL, con `El paciente no tiene un teléfono internacional válido.` si falla el teléfono, o `Falta la dirección del consultorio.` si solo falta dirección; no se bloquea Cancelar por esos datos

### Requirement: US-015 — Acción secundaria local al detalle de agenda

El detalle de C-13 en Día y Semana MUST ofrecer «WhatsApp» como acción secundaria de un reservado futuro según el ahora visual. Si falta el teléfono o su forma visiblemente no es internacional, MUST mostrar la acción no disponible con explicación; el servidor sigue siendo autoridad para teléfono, dirección y elegibilidad. Inicio/pasado o estados no elegibles MUST omitir WhatsApp. Cancelar MUST conservar su visibilidad, contrato y confirmación independientes.

Con una acción elegible, la interfaz MUST preparar como máximo una apertura nueva por gesto vigente y un GET por acción en curso. MUST abrir solo la URL validada de `wa.me`, sin vínculo de `opener` ni referrer del consultorio; MUST NOT guardar el link en almacenamiento persistente ni enviar el mensaje automáticamente. Si la apertura está bloqueada, la interfaz MUST ofrecer un enlace manual validado solo para el detalle vigente. Si la preparación falla, el turno cambia o se abandona ese detalle, MUST descartar la apertura pendiente y no reutilizar respuestas tardías.

#### Scenario: Acción en Día y Semana
- **WHEN** se elige un turno elegible en la hoja móvil a 390 px o en el detalle de escritorio a 1440 px, en Día o Semana, y se activa WhatsApp
- **THEN** el mensaje queda preparado en otra pestaña sin envío, la agenda conserva el detalle sin mutación y Cancelar sigue funcionando por separado

#### Scenario: Apertura bloqueada
- **WHEN** el navegador bloquea la apertura y la consulta vigente devuelve una URL válida
- **THEN** se ofrece «Abrir WhatsApp» como enlace manual en el detalle, sin reintentar la apertura por sí sola

#### Scenario: Doble clic y respuesta tardía
- **WHEN** se activa dos veces mientras el GET está pendiente y se cierra o cambia el detalle antes de que responda
- **THEN** solo se efectúa un GET y se descarta la apertura pendiente; la respuesta no abre otra pestaña, no muestra un enlace ni cambia el nuevo detalle

#### Scenario: Error o respuesta inválida
- **WHEN** falla red, vence la sesión, el turno dejó de ser elegible o el JSON no contiene una URL válida del host permitido
- **THEN** no se navega ni se envía nada; se conserva el manejo de sesión y, si el detalle sigue vigente, se muestra un aviso local sin exponer URL, teléfono ni datos del error sin filtrar
