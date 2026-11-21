# Spec Delta — autenticacion-odontologo

## Purpose

Define cómo el odontólogo inicia y cierra sesión en OdontoAgenda: validación de sus credenciales con BCrypt, respuesta genérica ante credenciales inválidas y bloqueo temporal del login después de intentos fallidos repetidos (RN-AU-01, RN-AU-02, RN-AU-03).

## ADDED Requirements

### Requirement: Inicio de sesión con email y contraseña

`POST /api/auth/login` MUST aceptar `{email, password}` y, con credenciales válidas de un odontólogo activo, MUST responder 200 con los datos del odontólogo autenticado (sin el hash de la contraseña) y MUST dejar una sesión iniciada. El email MUST compararse sin distinguir mayúsculas ni espacios sobrantes.

#### Scenario: Credenciales válidas

- **WHEN** un odontólogo activo envía su email y su contraseña correctos a `POST /api/auth/login`
- **THEN** la respuesta es 200 con `id`, `nombre`, `apellido`, `email` y `slug`, y la sesión queda iniciada

#### Scenario: Email con mayúsculas o espacios

- **WHEN** el odontólogo ingresa su email con mayúsculas o con espacios al principio o al final, y la contraseña es correcta
- **THEN** la respuesta es 200 y la sesión queda iniciada igual

#### Scenario: La respuesta no expone el hash

- **WHEN** el login responde 200
- **THEN** el cuerpo no contiene `password_hash`, `passwordHash` ni ningún valor que empiece con `$2`

### Requirement: Respuesta genérica ante credenciales inválidas

Ante una contraseña incorrecta, un email inexistente o un odontólogo inactivo, el login MUST responder 401 con el código `RN-AU-01` y un mensaje idéntico en los tres casos, sin indicar cuál de las condiciones falló (RN-AU-01).

#### Scenario: Contraseña incorrecta

- **WHEN** se envía el email de un odontólogo activo con una contraseña incorrecta
- **THEN** la respuesta es 401 con `codigo` `RN-AU-01` y un mensaje genérico

#### Scenario: Email inexistente

- **WHEN** se envía un email que no corresponde a ningún odontólogo
- **THEN** la respuesta es 401 con el mismo `codigo` y el mismo `mensaje` que el caso de contraseña incorrecta

#### Scenario: Cuenta inactiva

- **WHEN** se envían las credenciales correctas de un odontólogo con `activo` en falso
- **THEN** la respuesta es 401 con el mismo `codigo` y el mismo `mensaje` que los otros dos casos

### Requirement: Contraseñas almacenadas con BCrypt

El sistema MUST validar la contraseña contra el hash BCrypt guardado, sin prefijo de algoritmo, y MUST NOT aceptar como válido un hash que no sea BCrypt (RN-AU-02).

#### Scenario: Hash BCrypt sin prefijo

- **WHEN** el odontólogo tiene un hash BCrypt sin el prefijo `{bcrypt}` y envía su contraseña correcta
- **THEN** el login responde 200

#### Scenario: Hash inválido

- **WHEN** el odontólogo tiene un valor que no es un hash BCrypt (por ejemplo, un marcador pendiente del seed) y envía cualquier contraseña
- **THEN** el login responde 401 y nunca inicia sesión

### Requirement: Validación de entrada del login

Un login con email mal formado, email vacío, contraseña vacía o contraseña que supere los **72 bytes UTF-8** MUST responder 400 con `codigo` `VALIDACION` y `detalles` con el campo y el mensaje del error, sin evaluar credenciales ni consumir intentos del bloqueo. El límite es de bytes y no de caracteres: una contraseña de 72 caracteres con `ñ` o tildes ocupa más de 72 bytes y MUST rechazarse antes de llegar a BCrypt, que no admite entradas mayores a 72 bytes.

#### Scenario: Email mal formado

- **WHEN** se envía `{email: "no-es-un-email", password: "secreta"}`
- **THEN** la respuesta es 400 con `codigo` `VALIDACION` y un detalle con `campo` `email`

#### Scenario: Contraseña vacía

- **WHEN** se envía `{email: "demo@example.com", password: ""}`
- **THEN** la respuesta es 400 con `codigo` `VALIDACION` y un detalle con `campo` `password`

#### Scenario: Contraseña de 72 caracteres con ñ

- **WHEN** se envía una contraseña de 72 caracteres que incluye una `ñ` y por lo tanto ocupa 73 bytes en UTF-8
- **THEN** la respuesta es 400 con `codigo` `VALIDACION` y un detalle con `campo` `password`, sin evaluar credenciales ni consumir intentos del bloqueo

#### Scenario: Contraseña de 72 bytes exactos

- **WHEN** se envía una contraseña de 72 bytes UTF-8 exactos (por ejemplo 72 caracteres ASCII)
- **THEN** la validación la acepta y el login evalúa las credenciales normalmente

### Requirement: Bloqueo del login tras intentos fallidos

Después de 5 intentos fallidos seguidos para un mismo email, el login MUST quedar bloqueado 15 minutos y MUST responder 429 con `codigo` `RN-AU-03` y el tiempo restante. Mientras dure el bloqueo MUST rechazar incluso las credenciales correctas (RN-AU-03).

#### Scenario: Cuarto intento fallido

- **WHEN** se acumulan 4 intentos fallidos seguidos para un email
- **THEN** el cuarto responde 401 `RN-AU-01` y el login todavía no está bloqueado

#### Scenario: Quinto intento fallido bloquea

- **WHEN** se produce el quinto intento fallido seguido para un mismo email
- **THEN** la respuesta es 429 con `codigo` `RN-AU-03` y el tiempo restante en `detalles`

#### Scenario: Credenciales correctas durante el bloqueo

- **WHEN** se envía la contraseña correcta mientras el email está bloqueado
- **THEN** la respuesta es 429 con `codigo` `RN-AU-03` y no se inicia sesión

#### Scenario: Fin del bloqueo

- **WHEN** transcurren los 15 minutos del bloqueo y se envía la contraseña correcta
- **THEN** la respuesta es 200 y la sesión queda iniciada

#### Scenario: Un login exitoso reinicia el contador

- **WHEN** se acumulan 3 intentos fallidos, después un login exitoso y después otro intento fallido
- **THEN** el intento posterior al login exitoso responde 401 `RN-AU-01` y el contador vuelve a empezar

#### Scenario: Emails distintos no comparten el bloqueo

- **WHEN** un email alcanza el bloqueo y otro email envía credenciales inválidas
- **THEN** el segundo email recibe 401 `RN-AU-01`, no 429

#### Scenario: Un email inexistente también se bloquea

- **WHEN** se repiten 5 intentos fallidos con un email que no existe
- **THEN** el quinto intento responde 429 `RN-AU-03`, igual que con un email existente, para no revelar qué cuentas existen

### Requirement: Cierre de sesión

`POST /api/auth/logout` con una sesión iniciada MUST invalidarla y MUST responder 204 sin cuerpo; los pedidos posteriores con la misma cookie MUST comportarse como sin sesión.

#### Scenario: Logout invalida la sesión

- **WHEN** un odontólogo autenticado hace `POST /api/auth/logout`
- **THEN** la respuesta es 204 y un `GET /api/auth/me` posterior con la misma sesión responde 401 `NO_AUTENTICADO`

#### Scenario: Logout sin sesión

- **WHEN** se hace `POST /api/auth/logout` sin sesión iniciada
- **THEN** la respuesta es 401 `NO_AUTENTICADO`

### Requirement: Consulta del odontólogo de la sesión

`GET /api/auth/me` MUST responder 200 con `id`, `nombre`, `apellido`, `email` y `slug` del odontólogo de la sesión, sin el hash de la contraseña.

#### Scenario: Sesión activa

- **WHEN** un odontólogo autenticado hace `GET /api/auth/me`
- **THEN** la respuesta es 200 con sus datos y sin `password_hash`

#### Scenario: Sin sesión

- **WHEN** se hace `GET /api/auth/me` sin sesión
- **THEN** la respuesta es 401 `NO_AUTENTICADO`

#### Scenario: Cuenta desactivada después de iniciar sesión

- **WHEN** el odontólogo de la sesión fue desactivado y hace `GET /api/auth/me`
- **THEN** la respuesta es 401 `NO_AUTENTICADO` y la sesión deja de servir para pedidos posteriores
