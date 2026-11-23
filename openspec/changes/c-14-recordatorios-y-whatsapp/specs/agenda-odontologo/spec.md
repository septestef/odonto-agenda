# Spec Delta — US-015: Recordatorio manual por WhatsApp

## ADDED Requirements

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

Solo un turno propio `RESERVADO` con `inicio > ahora` del servidor, de cualquier origen, MUST habilitar la generación si el teléfono vigente del paciente es internacional E.164 válido con código de país explícito y la dirección vigente del consultorio está informada. El éxito MUST devolver 200 JSON `{ "url": "https://wa.me/{telefono}?text={mensaje}" }` con `Cache-Control: no-store`: host y esquema fijos, teléfono en dígitos ASCII sin `+`, texto codificado una vez en UTF-8. El mensaje MUST contener fecha y hora en `America/Argentina/Buenos_Aires`, prestación y dirección; MUST NOT incluir nombre del paciente, DNI, email, otros datos personales del paciente ni credenciales de gestión. El sistema MUST NOT adivinar país, normalizar un número nacional a internacional ni sustituir teléfono o dirección.

#### Scenario: Texto con caracteres especiales y cambio de fecha
- **WHEN** el turno inicia a `2026-11-27T01:30:00Z`, el teléfono internacional válido está informado y la prestación o la dirección contiene tildes, `&`, `+`, `#` o `%`
- **THEN** la URL apunta únicamente a `https://wa.me/` con el número sin `+` en el path y un solo `text`; al decodificarlo una vez se lee el mensaje en español para el 26/11 a las 22:30 con los caracteres originales, sin parámetros adicionales, PII extra ni token

#### Scenario: Estado y comienzo del turno
- **WHEN** el dueño consulta un turno cancelado, atendido, ausente o un reservado con `inicio <= ahora`, aunque tenga datos correctos
- **THEN** se devuelve 409 `{ "codigo": "RN-NO-05", "mensaje": "Solo podés preparar un WhatsApp para un turno reservado que todavía no empezó." }` sin URL

#### Scenario: Contacto o dirección no utilizables
- **WHEN** el turno propio reservado futuro tiene teléfono ausente, local o internacional inválido, o tiene teléfono válido pero dirección nula, vacía o en blanco
- **THEN** se devuelve 422 `RN-NO-05`, sin URL, con `El paciente no tiene un teléfono internacional válido.` si falla el teléfono, o `Falta la dirección del consultorio.` si solo falta dirección; no se bloquea Cancelar por esos datos

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
