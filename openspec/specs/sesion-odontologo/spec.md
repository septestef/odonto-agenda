# sesion-odontologo Specification

## Purpose
Define la sesión del odontólogo en OdontoAgenda: sesión HTTP del lado del servidor con cookie inaccesible para JavaScript, expiración por inactividad, protección CSRF y las reglas de autorización de `/api/**` con sus respuestas 401 y 403 en el formato uniforme de la API (RN-AU-04, DD-04).

## Requirements

### Requirement: Sesión de servidor con cookie HttpOnly

Al iniciar sesión, el sistema MUST crear una sesión del lado del servidor identificada por una cookie `JSESSIONID` con `HttpOnly` y `SameSite=Lax`, MUST aceptar el identificador solo por cookie (nunca por URL) y MUST NOT usar tokens JWT ni guardar credenciales en el cliente (DD-04).

#### Scenario: Cookie de sesión en el login

- **WHEN** un odontólogo inicia sesión con credenciales válidas
- **THEN** la respuesta incluye una cookie `JSESSIONID` con `HttpOnly` y `SameSite=Lax`

#### Scenario: Identificador solo por cookie

- **WHEN** un cliente intenta reutilizar un identificador de sesión pasado por parámetro de URL
- **THEN** el sistema no lo acepta como sesión válida

### Requirement: Rotación del identificador al autenticar

Al pasar de no autenticado a autenticado, el sistema MUST cambiar el identificador de sesión para impedir la fijación de sesión.

#### Scenario: El identificador cambia después del login

- **WHEN** un cliente llega al login con una sesión anónima ya creada y se autentica correctamente
- **THEN** la cookie `JSESSIONID` de la respuesta es distinta de la que traía antes del login

### Requirement: Expiración por inactividad de 8 horas

Una sesión sin actividad MUST dejar de ser válida a las 8 horas y MUST renovarse con cada pedido del odontólogo mientras esté activa (RN-AU-04).

#### Scenario: Configuración efectiva

- **WHEN** se inspecciona la configuración efectiva del servidor de aplicaciones
- **THEN** el tiempo de expiración de sesión es de 8 horas y la cookie de sesión está marcada `HttpOnly` y `SameSite=Lax`

#### Scenario: Sesión vencida

- **WHEN** un cliente usa una cookie de sesión que superó las 8 horas de inactividad
- **THEN** el sistema lo trata como sin sesión y responde 401 `NO_AUTENTICADO` en los endpoints protegidos

### Requirement: Protección CSRF con cookie y header

Todo pedido que modifica estado bajo `/api/**` MUST exigir el token CSRF enviado en el header `X-XSRF-TOKEN`, tomado de la cookie `XSRF-TOKEN`; quedan exentos `POST /api/auth/login` y `/api/public/**`. Los pedidos de lectura (`GET`, `HEAD`) MUST NOT exigirlo.

#### Scenario: Escritura sin token

- **WHEN** un odontólogo autenticado hace `POST /api/auth/logout` sin el header `X-XSRF-TOKEN`
- **THEN** la respuesta es 403 con `codigo` `ACCESO_DENEGADO` y la sesión sigue activa

#### Scenario: Escritura con token

- **WHEN** el mismo pedido se repite con el header `X-XSRF-TOKEN` con el valor de la cookie `XSRF-TOKEN`
- **THEN** la operación se ejecuta

#### Scenario: Login exento de CSRF

- **WHEN** se hace `POST /api/auth/login` con credenciales válidas y sin token CSRF
- **THEN** el login se procesa normalmente

#### Scenario: Cookie CSRF legible por el frontend

- **WHEN** el frontend obtiene un token CSRF
- **THEN** la cookie `XSRF-TOKEN` no es `HttpOnly` (el cliente tiene que poder leerla) y la cookie de sesión `JSESSIONID` sí lo es

### Requirement: Autorización de los endpoints de la API

`/api/public/**` y `POST /api/auth/login` MUST ser accesibles sin sesión; todo el resto de `/api/**` MUST exigir una sesión con el rol `ODONTOLOGO`. Los recursos estáticos del frontend y las rutas de la SPA MUST servirse sin sesión.

#### Scenario: Endpoint público sin sesión

- **WHEN** se llama a un endpoint bajo `/api/public/**` sin sesión
- **THEN** la autorización lo deja pasar (la respuesta depende de la lógica del endpoint, nunca es 401 por falta de sesión)

#### Scenario: Endpoint protegido sin sesión

- **WHEN** se llama a cualquier endpoint bajo `/api/**` fuera de los públicos y del login, sin sesión
- **THEN** la respuesta es 401 con `codigo` `NO_AUTENTICADO`

#### Scenario: Endpoint protegido con sesión

- **WHEN** un odontólogo autenticado llama a un endpoint bajo `/api/**`
- **THEN** la autorización lo deja pasar

#### Scenario: Frontend sin sesión

- **WHEN** se piden `/`, `/index.html`, un archivo de `/assets/**` o una ruta de la SPA como `/login` sin sesión
- **THEN** el contenido se sirve sin pedir autenticación

### Requirement: Respuestas 401 y 403 en el formato uniforme

Los rechazos por falta de sesión o por autorización MUST responder JSON con `Content-Type: application/json` y el cuerpo `{codigo, mensaje}`, sin redirects, sin HTML y sin filtrar detalles internos. El 401 de falta de sesión MUST usar el código `NO_AUTENTICADO` y el 403 MUST usar `ACCESO_DENEGADO`.

#### Scenario: 401 sin redirect

- **WHEN** un pedido sin sesión llega a un endpoint protegido
- **THEN** la respuesta es 401 en JSON con `codigo` `NO_AUTENTICADO` y no hay header `Location`

#### Scenario: 403 en JSON

- **WHEN** un pedido con sesión falla la verificación CSRF
- **THEN** la respuesta es 403 en JSON con `codigo` `ACCESO_DENEGADO` y un mensaje genérico
