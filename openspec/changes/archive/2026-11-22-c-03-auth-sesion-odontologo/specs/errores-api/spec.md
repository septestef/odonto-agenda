# Spec Delta — errores-api

## MODIFIED Requirements

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

## ADDED Requirements

### Requirement: Errores de autenticación y autorización

Los rechazos por falta de sesión (401) y por autorización o CSRF (403) MUST usar el mismo formato uniforme `{codigo, mensaje, detalles?}` con `Content-Type: application/json`, MUST NOT ser redirects ni HTML, y MUST NOT exponer detalles internos. El 401 por falta de sesión MUST usar `NO_AUTENTICADO` y el 403 MUST usar `ACCESO_DENEGADO`; los rechazos de credenciales y de bloqueo del login usan `RN-AU-01` y `RN-AU-03`.

#### Scenario: Falta de sesión

- **WHEN** un pedido sin sesión llega a un endpoint protegido
- **THEN** la respuesta es 401 en JSON con `codigo` `NO_AUTENTICADO` y sin header `Location`

#### Scenario: CSRF inválido

- **WHEN** un pedido que modifica estado falla la verificación CSRF
- **THEN** la respuesta es 403 en JSON con `codigo` `ACCESO_DENEGADO`

#### Scenario: Credenciales inválidas

- **WHEN** el login recibe credenciales inválidas
- **THEN** la respuesta es 401 en JSON con `codigo` `RN-AU-01` y un mensaje genérico
