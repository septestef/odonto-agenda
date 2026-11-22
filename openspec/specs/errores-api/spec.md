# errores-api Specification

## Purpose
Define el contrato único con el que la API REST de OdontoAgenda informa cualquier error, para que el frontend y los tests puedan reaccionar por código sin depender de textos ni de detalles internos del servidor.

## Requirements

### Requirement: Forma uniforme del cuerpo de error
Toda respuesta de error de un endpoint bajo `/api/**` MUST tener `Content-Type: application/json` y un cuerpo JSON con `codigo` (string) y `mensaje` (string en español rioplatense) en el nivel superior. El campo `detalles` MUST aparecer solo cuando aporta información y MUST omitirse si está vacío. No se usa el formato `application/problem+json`.

#### Scenario: Error con detalles
- **WHEN** una operación de la API falla y el error trae información adicional
- **THEN** el cuerpo es `{ "codigo": "...", "mensaje": "...", "detalles": ... }` con `Content-Type: application/json`

#### Scenario: Error sin detalles
- **WHEN** una operación de la API falla sin información adicional
- **THEN** el cuerpo contiene `codigo` y `mensaje` y no contiene la clave `detalles`

### Requirement: Rechazo por regla de negocio
Cuando una regla de negocio rechaza una operación, la API MUST responder con el código de la regla en formato `RN-XX-NN` (según `05_reglas_de_negocio.md`), su mensaje y el estado HTTP que la regla define.

#### Scenario: Regla de negocio violada
- **WHEN** un servicio rechaza una operación por la regla `RN-TU-01` con estado 409
- **THEN** la API responde 409 con `{ "codigo": "RN-TU-01", "mensaje": "Ese horario acaba de ser tomado, elegí otro" }`

### Requirement: Errores de validación de entrada
Un request con datos que no pasan la validación o con un cuerpo JSON ilegible MUST responder 400. Para validación de campos el código MUST ser `VALIDACION` y `detalles` MUST listar cada campo inválido con su mensaje; para un cuerpo ilegible el código MUST ser `SOLICITUD_INVALIDA`.

#### Scenario: Campo obligatorio faltante
- **WHEN** se envía un request cuyo campo obligatorio `nombre` está vacío
- **THEN** la API responde 400 con `codigo` `VALIDACION` y `detalles` incluye un elemento con `campo` `nombre` y su `mensaje`

#### Scenario: JSON mal formado
- **WHEN** se envía un cuerpo que no es JSON válido a un endpoint que espera JSON
- **THEN** la API responde 400 con `codigo` `SOLICITUD_INVALIDA`

### Requirement: Recurso o ruta de API inexistente

Una ruta bajo `/api/**` que no existe MUST responder 404 con `codigo` `NO_ENCONTRADO` cuando el pedido trae una sesión de odontólogo válida, y MUST responder 401 con `codigo` `NO_AUTENTICADO` cuando no hay sesión, porque la autorización corre antes del ruteo y no se enumeran rutas para clientes anónimos. Un método HTTP no soportado en una ruta existente MUST responder 405 con `codigo` `METODO_NO_PERMITIDO`. Todas las respuestas usan el formato uniforme.

#### Scenario: Ruta de API desconocida

- **WHEN** un odontólogo autenticado hace `GET /api/no-existe`
- **THEN** la API responde 404 con `codigo` `NO_ENCONTRADO` en JSON (no la página de la SPA)

#### Scenario: Ruta de API desconocida sin sesión

- **WHEN** se hace `GET /api/no-existe` sin sesión iniciada
- **THEN** la API responde 401 con `codigo` `NO_AUTENTICADO` en JSON

#### Scenario: Método no soportado

- **WHEN** un odontólogo autenticado hace `DELETE` sobre una ruta de API que solo acepta `GET`
- **THEN** la API responde 405 con `codigo` `METODO_NO_PERMITIDO`

### Requirement: Error inesperado sin filtrar detalles internos
Ante cualquier error no previsto, la API MUST responder 500 con `codigo` `ERROR_INTERNO` y un mensaje genérico. La respuesta MUST NOT incluir stack traces, nombres de clases, SQL ni mensajes de excepción; el detalle MUST quedar registrado en el log del servidor.

#### Scenario: Excepción no controlada
- **WHEN** un endpoint lanza una excepción inesperada con un mensaje interno
- **THEN** la API responde 500 con `codigo` `ERROR_INTERNO`, el cuerpo no contiene ese mensaje interno ni un stack trace, y el error queda en el log

### Requirement: Conflicto de turnos en la base como RN-TU-01
Cuando una escritura de turnos falla en la base por la restricción `turno_sin_solapamiento`, por un deadlock o por no poder tomar un lock, la API MUST responder 409 con `{ "codigo": "RN-TU-01", "mensaje": "Ese horario acaba de ser tomado, elegí otro" }` (RN-GL-01). La respuesta MUST NOT incluir SQL, nombres de restricciones ni mensajes de la base.

#### Scenario: Violación de la restricción anti-solapamiento
- **WHEN** un endpoint que escribe turnos recibe de la base una violación de exclusión de `turno_sin_solapamiento`
- **THEN** la API responde 409 con código `RN-TU-01` y el cuerpo no contiene `turno_sin_solapamiento` ni texto de la base

#### Scenario: Deadlock o falla de lock
- **WHEN** un endpoint que escribe turnos recibe de la base un deadlock o una falla al tomar un lock
- **THEN** la API responde 409 con código `RN-TU-01`

#### Scenario: Otra violación de integridad
- **WHEN** un endpoint recibe de la base una violación de integridad que no es de `turno_sin_solapamiento`
- **THEN** la API no la presenta como `RN-TU-01` y responde 500 con código `ERROR_INTERNO`

### Requirement: Errores de autenticación y autorización
Los rechazos por falta de sesión (401) y por autorización o CSRF (403) MUST usar el mismo formato uniforme `{codigo, mensaje, detalles?}` con `Content-Type: application/json`, MUST NOT ser redirects ni HTML, y MUST NOT exponer detalles internos. El 401 por falta de sesión MUST usar `NO_AUTENTICADO` y el 403 MUST usar `ACCESO_DENEGADO`; los rechazos de credenciales y de bloqueo del login usan `RN-AU-01` y `RN-AU-03`.

#### Scenario: Falta de sesión
- **WHEN** un pedido sin sesión que no requiere CSRF o ya lo supera llega a un endpoint protegido
- **THEN** la respuesta es 401 en JSON con `codigo` `NO_AUTENTICADO` y sin header `Location`

#### Scenario: Credenciales inválidas
- **WHEN** el login recibe credenciales inválidas
- **THEN** la respuesta es 401 en JSON con `codigo` `RN-AU-01` y un mensaje genérico

### Requirement: Precedencia entre CSRF y sesión
La cadena existente MUST conservar su precedencia: un pedido sin sesión que supera CSRF o no lo requiere alcanza autorización y recibe 401; si falla CSRF antes, MUST responder 403 sin ejecutar la operación, aun cuando tampoco tenga sesión. MUST NOT garantizarse 401 universal para pedidos que incumplen ambas condiciones.

#### Scenario: CSRF inválido
- **WHEN** un pedido que modifica estado falla la verificación CSRF
- **THEN** la respuesta es 403 en JSON con `codigo` `ACCESO_DENEGADO`, aunque además no tenga sesión, sin mutar datos

### Requirement: Respuestas C-12 sin secretos de gestión
Las respuestas de agenda y su detalle anidado, listados de pacientes, cancelación privada y errores de estos endpoints MUST NOT exponer token/hash ni email de gestión, bajo nombres `token_hash`/`tokenHash` o `email_gestion`/`emailGestion`, ni sus valores por eco en mensajes/detalles. Los DTOs privados MUST limitarse a los campos de P2/P3; el email mutable autorizado de paciente no representa ni reemplaza email_gestion.

#### Scenario: Contacto mutable distinto del destinatario de gestión
- **WHEN** la agenda y los pacientes muestran paciente.email `actual@example.test` y el turno guarda un email_gestion centinela diferente y un token_hash centinela
- **THEN** las respuestas exponen solo el contacto mutable contratado y ninguno de los campos ni valores reservados, tampoco en detalles anidados o errores de cancelación

#### Scenario: Inconsistencia futura con respuesta y diagnóstico sanitizados
- **WHEN** tras pertenencia, lock y reloj se observa ATENDIDO o AUSENTE con inicio > ahora al cancelar privadamente
- **THEN** el fallback existente responde 500 application/json con solo `{ "codigo": "ERROR_INTERNO", "mensaje": "Ocurrió un error inesperado. Probá de nuevo en unos minutos." }`; el diagnóstico local y la excepción completa registrada por el advice no contienen datos personales, emails, tokens/hashes, entidades ni causas con campos, y no hay mutación, flush de actualización, evento ni notificación; no se modifica el contrato compartido ni se usa una RN como rechazo de esta inconsistencia

### Requirement: Conflictos de cancelación privada
La cancelación privada exitosa MUST conservar 204 vacío. La traducción de sus conflictos MUST reutilizar el RN-TU-01 y mensaje vigentes, sin SQL/causas internas ni nuevo código técnico.

#### Scenario: Fallo de lock en cancelación sin datos internos
- **WHEN** la cancelación privada recibe 40P01, 55P03 o falla de lock pesimista equivalente, incluso al cerrar la transacción
- **THEN** responde 409 con solo `{ "codigo": "RN-TU-01", "mensaje": "Ese horario acaba de ser tomado, elegí otro" }`, sin secretos ni transición confirmada ni aviso; las fallas no clasificadas siguen siendo ERROR_INTERNO
