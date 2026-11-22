# configuracion-agenda Specification

## Purpose
Define el contrato autenticado y aislado con el que el odontólogo configura su agenda: el horario semanal, las prestaciones y los bloqueos, incluidas las reglas que protegen la disponibilidad y los turnos ya existentes.

## Requirements

### Requirement: Acceso autenticado y aislado de la configuración

Todo endpoint de configuración MUST exigir una sesión de odontólogo y, en las escrituras, el token CSRF `X-XSRF-TOKEN`. El dueño de los datos MUST salir siempre de la sesión: cualquier `odontologoId` recibido en el cuerpo, la query o los headers MUST ignorarse. Un recurso de otro odontólogo MUST responderse 404 `NO_ENCONTRADO`, con el mismo cuerpo que un recurso inexistente (RN-SE-01).

#### Scenario: Sin sesión

- **WHEN** se llama a `GET /api/horarios` sin sesión iniciada
- **THEN** la API responde 401 `NO_AUTENTICADO` en JSON, sin redirect ni HTML

#### Scenario: Escritura sin token CSRF

- **WHEN** un odontólogo con sesión hace `POST /api/prestaciones` sin `X-XSRF-TOKEN`
- **THEN** la API responde 403 `ACCESO_DENEGADO` y no se crea la prestación

#### Scenario: Identificador de odontólogo en el pedido

- **WHEN** un odontólogo autenticado envía `odontologoId` ajeno en el cuerpo o la query de un endpoint de configuración
- **THEN** la operación se ejecuta sobre sus propios datos y el valor recibido no tiene efecto

#### Scenario: Recurso ajeno e inexistente

- **WHEN** un odontólogo pide, modifica o borra una prestación o un bloqueo de otro odontólogo, o con un id que no existe
- **THEN** en ambos casos la API responde 404 `NO_ENCONTRADO` con el mismo cuerpo

### Requirement: Horario semanal con reemplazo atómico

`GET /api/horarios` MUST devolver todas las franjas del odontólogo ordenadas por día de la semana y hora de inicio. `PUT /api/horarios` MUST reemplazar el horario completo en una sola transacción: admitir varias franjas por día y días sin atención, y no dejar parte del horario anterior si el reemplazo falla (RN-AG-01).

#### Scenario: Consulta del horario

- **WHEN** el odontólogo tiene franjas el lunes de 09:00 a 13:00 y de 16:00 a 20:00, y ninguna el domingo
- **THEN** `GET /api/horarios` devuelve ambas franjas ordenadas por inicio y ninguna el domingo

#### Scenario: Reemplazo con varias franjas

- **WHEN** se hace `PUT /api/horarios` con dos franjas para el martes y una para el jueves
- **THEN** la respuesta es 200 con el horario guardado y una consulta posterior devuelve exactamente esas tres franjas

#### Scenario: Reemplazo vacío

- **WHEN** se hace `PUT /api/horarios` con una lista de franjas vacía
- **THEN** la respuesta es 200 y el odontólogo queda sin franjas de atención

#### Scenario: Reemplazo rechazado

- **WHEN** un `PUT /api/horarios` trae una franja inválida y es rechazado
- **THEN** el horario anterior sigue vigente completo

### Requirement: Franjas válidas y sin solapamiento

Las franjas MUST tener un día de semana ISO de 1 a 7, horas en formato `HH:mm` y una hora de inicio estrictamente anterior a la de fin. Dos franjas del mismo día MUST NOT solaparse. Un incumplimiento MUST responderse 422 con `codigo` `RN-AG-01` y `detalles` que identifiquen la franja o las franjas involucradas (RN-AG-01).

#### Scenario: Franja invertida

- **WHEN** se envía una franja de 13:00 a 09:00 o con inicio igual al fin
- **THEN** la API responde 422 `RN-AG-01` y `detalles` incluye el índice de esa franja

#### Scenario: Franjas solapadas

- **WHEN** se envían dos franjas el miércoles de 09:00 a 11:00 y de 10:00 a 12:00
- **THEN** la API responde 422 `RN-AG-01` y no se guarda ninguna de las dos

#### Scenario: Franjas contiguas y de días distintos

- **WHEN** se envían franjas de 09:00 a 11:00 y de 11:00 a 13:00 el mismo día, o la misma hora en días distintos
- **THEN** ambas se aceptan

### Requirement: Gestión de prestaciones con baja lógica

`GET /api/prestaciones` MUST devolver las prestaciones propias, activas e inactivas, ordenadas por nombre, con su `activa`. `POST` MUST crear y devolver 201; `PUT /api/prestaciones/{id}` MUST actualizar nombre y duración; `DELETE /api/prestaciones/{id}` MUST dar de baja la prestación sin borrarla, responder 204 y ser idempotente si ya estaba inactiva. Los turnos existentes MUST conservar su prestación y su duración original (RN-AG-04, RN-TU-02).

#### Scenario: Alta y consulta

- **WHEN** se crea una prestación "Limpieza" de 45 minutos y se consulta el listado
- **THEN** el alta responde 201 y el listado la incluye con `activa: true`

#### Scenario: Baja lógica

- **WHEN** se da de baja una prestación que ya tiene turnos reservados
- **THEN** el `DELETE` responde 204, la fila sigue existiendo con `activa: false` y los turnos siguen referenciándola con su duración original

#### Scenario: Prestación dada de baja fuera de la reserva pública

- **WHEN** se consulta `GET /api/public/{slug}` después de dar de baja una prestación
- **THEN** esa prestación no aparece entre las prestaciones ofrecidas

### Requirement: Duración de prestación múltiplo de 15

La duración de una prestación MUST ser un múltiplo positivo de 15 minutos. Un valor que no cumple la regla MUST responderse 422 con `codigo` `RN-AG-03`; los turnos ya existentes MUST NOT modificarse cuando cambia la duración de la prestación (RN-AG-03, RN-TU-02).

#### Scenario: Duración inválida

- **WHEN** se crea o actualiza una prestación con duración 20 minutos
- **THEN** la API responde 422 `RN-AG-03` con un mensaje en español rioplatense

#### Scenario: Duración válida

- **WHEN** se crea o actualiza una prestación con duración 30, 45 o 60 minutos
- **THEN** la operación se acepta y la duración queda guardada

### Requirement: Nombre de prestación único por odontólogo

El nombre de cada prestación MUST ser único dentro del odontólogo. Un nombre repetido para el mismo odontólogo MUST responderse 400 con `codigo` `VALIDACION` y un detalle en el campo `nombre`, sin revelar SQL ni nombres de restricciones. El mismo nombre en otro odontólogo MUST aceptarse.

#### Scenario: Nombre repetido

- **WHEN** un odontólogo crea o renombra una prestación al nombre de otra prestación suya
- **THEN** la API responde 400 `VALIDACION` con `detalles` en `nombre`

#### Scenario: Mismo nombre en otra cuenta

- **WHEN** otro odontólogo crea una prestación con el mismo nombre
- **THEN** la operación se acepta

### Requirement: Bloqueos con rango válido

`GET /api/bloqueos` MUST devolver los bloqueos propios ordenados por inicio. `POST /api/bloqueos` MUST crear un bloqueo con inicio y fin en instantes con offset y `inicio < fin`; `DELETE /api/bloqueos/{id}` MUST borrarlo y responder 204. Un rango invertido MUST responderse 400 `VALIDACION` con un detalle en `fin`. Dos bloqueos pueden solaparse (RN-AG-02).

#### Scenario: Alta de bloqueo

- **WHEN** se crea un bloqueo con inicio y fin con offset de la zona del negocio y un motivo opcional
- **THEN** la API responde 201 con el bloqueo guardado y sus instantes con offset

#### Scenario: Rango invertido

- **WHEN** se envía un bloqueo cuyo fin es igual o anterior al inicio
- **THEN** la API responde 400 `VALIDACION` con `detalles` en el campo `fin`

#### Scenario: Borrado ajeno

- **WHEN** un odontólogo intenta borrar el bloqueo de otro odontólogo
- **THEN** la API responde 404 `NO_ENCONTRADO` y el bloqueo ajeno sigue existiendo

### Requirement: Crear un bloqueo nunca cancela turnos

Crear un bloqueo MUST NOT cancelar ni modificar los turnos que caen en su rango. La respuesta del alta y cada bloqueo devuelto por `GET /api/bloqueos` MUST incluir `turnosAfectados`: los turnos propios en estado `RESERVADO`, `ATENDIDO` o `AUSENTE` cuyo intervalo se superpone con el bloqueo, con id, inicio, fin, estado, nombre del paciente y nombre de la prestación. El rango bloqueado MUST dejar de ofrecerse en la disponibilidad pública (RN-AG-02).

#### Scenario: Bloqueo con turnos existentes

- **WHEN** se crea un bloqueo sobre un rango que contiene un turno `RESERVADO`
- **THEN** la respuesta 201 lista ese turno en `turnosAfectados` y el turno sigue `RESERVADO` y ocupando su horario

#### Scenario: Bloqueo sin turnos

- **WHEN** se crea un bloqueo en un rango sin turnos activos
- **THEN** `turnosAfectados` es una lista vacía

#### Scenario: Bloqueo y disponibilidad pública

- **WHEN** se consulta la disponibilidad pública de un horario cubierto por un bloqueo
- **THEN** ese horario no se ofrece, aunque exista un turno cancelado en el rango

### Requirement: Errores uniformes de la configuración

Toda respuesta de error de estos endpoints MUST usar `{ "codigo", "mensaje", "detalles"? }` con `Content-Type: application/json`. Los rechazos por regla de negocio MUST usar `RN-AG-01` o `RN-AG-03`; las validaciones estructurales `VALIDACION`; la falta de sesión `NO_AUTENTICADO`; el CSRF inválido `ACCESO_DENEGADO`; el recurso ajeno o inexistente `NO_ENCONTRADO`. El cuerpo MUST NOT incluir SQL, nombres de restricciones ni mensajes internos.

#### Scenario: Regla de negocio rechazada

- **WHEN** se rechaza una franja solapada o una duración de 20 minutos
- **THEN** la respuesta trae `codigo` `RN-AG-01` o `RN-AG-03` respectivamente, sin SQL ni nombres de constraints

#### Scenario: Cuerpo ilegible

- **WHEN** se envía un cuerpo JSON inválido a un endpoint de configuración
- **THEN** la API responde 400 `SOLICITUD_INVALIDA` con el formato uniforme
