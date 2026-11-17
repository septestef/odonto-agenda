# entrega-frontend Specification

## Purpose
Define cómo el backend sirve la SPA en el mismo origen que la API (sin CORS) y cómo se resuelven las rutas del navegador, tanto en la demo como en desarrollo con el servidor de Vite.

## Requirements

### Requirement: SPA servida desde el mismo origen
En la aplicación empaquetada, el backend MUST servir el build del frontend en `/` y sus archivos estáticos desde el mismo origen que la API, sin requerir configuración de CORS.

#### Scenario: Página de inicio
- **WHEN** se hace `GET /` a la aplicación empaquetada
- **THEN** responde 200 con el `index.html` del build del frontend

#### Scenario: Archivo estático existente
- **WHEN** se pide un archivo estático que existe en el build (por ejemplo, un `.js` de `assets/`)
- **THEN** responde 200 con ese archivo

### Requirement: Fallback de rutas de la SPA
Una ruta que no empieza con `/api/` y no corresponde a un archivo estático existente MUST responder 200 con `index.html`, para que el router del frontend la resuelva. Las rutas bajo `/api/**` MUST NOT caer en este fallback.

#### Scenario: Ruta profunda de la SPA
- **WHEN** se hace `GET /turno/abc123` (ruta del frontend, sin archivo estático)
- **THEN** responde 200 con el contenido de `index.html`

#### Scenario: Ruta de API inexistente
- **WHEN** se hace `GET /api/no-existe`
- **THEN** no se devuelve `index.html`, sino el error 404 `NO_ENCONTRADO` en JSON

### Requirement: Desarrollo con proxy a la API
En desarrollo, el servidor del frontend MUST reenviar las peticiones a `/api` al backend en `http://localhost:8080`, de modo que el código del frontend use siempre rutas relativas `/api/...`.

#### Scenario: Llamada a la API desde el servidor de desarrollo
- **WHEN** con el servidor de desarrollo del frontend corriendo, el navegador pide `/api/no-existe`
- **THEN** la petición llega al backend y se recibe su respuesta 404 en JSON

### Requirement: Interpretación tipada de errores en el frontend
El frontend MUST convertir el cuerpo de una respuesta de error de la API a una estructura tipada con `codigo`, `mensaje` y `detalles` opcional, y MUST tratar cualquier cuerpo que no cumpla ese formato como un error genérico con un mensaje en español rioplatense, sin propagar valores sin tipar.

#### Scenario: Cuerpo de error válido
- **WHEN** la API devuelve `{ "codigo": "RN-TU-01", "mensaje": "Ese horario acaba de ser tomado, elegí otro" }`
- **THEN** el frontend obtiene un error con ese `codigo` y ese `mensaje`

#### Scenario: Cuerpo de error inesperado
- **WHEN** la respuesta de error no es JSON o le falta `codigo` o `mensaje`
- **THEN** el frontend obtiene un error genérico con `codigo` `ERROR_DESCONOCIDO` y un mensaje genérico
