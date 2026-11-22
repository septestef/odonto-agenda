# api-client Specification

## Purpose
Establece cómo el frontend llama a la API del mismo origen: qué credenciales envía, cómo reenvía la protección CSRF y cómo convierte respuestas y fallas de red en errores tipados que el resto de la aplicación puede mostrar.

## Requirements

### Requirement: Pedidos al mismo origen con credenciales de sesión
Todo pedido a la API DEBE (MUST) realizarse al mismo origen del documento, sin configuración de CORS, e incluir las credenciales de la sesión. El frontend NO DEBE (MUST) construir ni almacenar tokens de sesión propios: la cookie de sesión la administra el navegador.

#### Scenario: Pedido autenticado
- **WHEN** se consulta un endpoint bajo `/api/` con una sesión iniciada
- **THEN** el pedido se envía con `credentials: 'same-origin'` y el navegador adjunta la cookie de sesión

#### Scenario: Sin configuración de dominios externos
- **WHEN** se construye la URL de un pedido
- **THEN** se usa una ruta relativa al origen y no una URL absoluta ni un dominio configurado por entorno

### Requirement: Reenvío del token CSRF desde la cookie
En los métodos que escriben (`POST`, `PUT`, `PATCH`, `DELETE`), el cliente DEBE (MUST) leer la cookie legible `XSRF-TOKEN` y reenviar su valor en el header `X-XSRF-TOKEN`. En los métodos de lectura (`GET`, `HEAD`) NO DEBE (MUST) enviar ese header. El inicio de sesión es público y NO DEBE (MUST) exigirlo, porque la API lo excluye de la protección CSRF.

#### Scenario: Escritura protegida
- **WHEN** se envía un `POST /api/auth/logout` con la cookie `XSRF-TOKEN=abc`
- **THEN** el pedido incluye `X-XSRF-TOKEN: abc` junto con la cookie de sesión

#### Scenario: Escritura sin cookie CSRF disponible
- **WHEN** se intenta una escritura protegida y no existe la cookie `XSRF-TOKEN`
- **THEN** no se inventa un valor para el header y la respuesta 403 con `ACCESO_DENEGADO` se convierte en el error tipado correspondiente

#### Scenario: Lectura sin header CSRF
- **WHEN** se envía `GET /api/auth/me`
- **THEN** el pedido no incluye el header `X-XSRF-TOKEN`

#### Scenario: Inicio de sesión sin header CSRF
- **WHEN** se envía `POST /api/auth/login`
- **THEN** el pedido no incluye el header `X-XSRF-TOKEN` y la API acepta la credencial

### Requirement: Errores tipados, cuerpo vacío y falla de red
Las respuestas con error DEBE (MUST) convertirse en un error con `{codigo, mensaje, detalles?}`, reutilizando la interpretación de errores ya existente. Una respuesta sin cuerpo (por ejemplo, el 204 de cierre de sesión) NO DEBE (MUST) tratarse como error. Una falla de red sin respuesta HTTP DEBE (MUST) exponerse con el código `SIN_CONEXION` y un mensaje en español rioplatense.

#### Scenario: Error de regla de negocio
- **WHEN** la API responde 429 con `{codigo: "RN-AU-03", mensaje: "...", detalles: {...}}`
- **THEN** el error expone ese código, ese mensaje y esos detalles sin perderlos

#### Scenario: Cuerpo de error inválido
- **WHEN** la API responde 500 con un cuerpo que no tiene `codigo` y `mensaje` como textos
- **THEN** se expone el error genérico `ERROR_DESCONOCIDO` con un mensaje comprensible

#### Scenario: Respuesta sin contenido
- **WHEN** `POST /api/auth/logout` responde 204 sin cuerpo
- **THEN** la operación se considera exitosa y no se intenta interpretar un cuerpo vacío

#### Scenario: Falla de red
- **WHEN** el navegador no puede completar el pedido y no hay respuesta HTTP
- **THEN** se expone un error `SIN_CONEXION` con un mensaje que invita a revisar la conexión e intentar de nuevo

### Requirement: Aviso de sesión vencida
Cuando una respuesta de la API indica sesión vencida (401 con `NO_AUTENTICADO`), el cliente DEBE (MUST) publicar una señal de sesión vencida para que la capa de navegación pueda limpiar la caché y redirigir al inicio de sesión. NO DEBE (MUST) redirigir por su cuenta, para no acoplarse al router.

#### Scenario: Respuesta 401 autenticada
- **WHEN** cualquier pedido autenticado responde 401 con `NO_AUTENTICADO`
- **THEN** se publica la señal de sesión vencida y se expone el error tipado al llamador

#### Scenario: Respuesta 401 de credenciales del login
- **WHEN** `POST /api/auth/login` responde 401 con `RN-AU-01`
- **THEN** NO se publica la señal de sesión vencida, porque todavía no hay sesión que haya expirado
