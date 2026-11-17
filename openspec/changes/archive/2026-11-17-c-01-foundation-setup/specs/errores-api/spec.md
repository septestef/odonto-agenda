# Spec Delta — errores-api

## Purpose

Define el contrato único con el que la API REST de OdontoAgenda informa cualquier error, para que el frontend y los tests puedan reaccionar por código sin depender de textos ni de detalles internos del servidor.

## ADDED Requirements

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
Una ruta bajo `/api/**` que no existe MUST responder 404 con `codigo` `NO_ENCONTRADO`, y un método HTTP no soportado en una ruta existente MUST responder 405 con `codigo` `METODO_NO_PERMITIDO`, ambos en el formato uniforme.

#### Scenario: Ruta de API desconocida
- **WHEN** se hace `GET /api/no-existe`
- **THEN** la API responde 404 con `codigo` `NO_ENCONTRADO` en JSON (no la página de la SPA)

#### Scenario: Método no soportado
- **WHEN** se hace `DELETE` sobre una ruta de API que solo acepta `GET`
- **THEN** la API responde 405 con `codigo` `METODO_NO_PERMITIDO`

### Requirement: Error inesperado sin filtrar detalles internos
Ante cualquier error no previsto, la API MUST responder 500 con `codigo` `ERROR_INTERNO` y un mensaje genérico. La respuesta MUST NOT incluir stack traces, nombres de clases, SQL ni mensajes de excepción; el detalle MUST quedar registrado en el log del servidor.

#### Scenario: Excepción no controlada
- **WHEN** un endpoint lanza una excepción inesperada con un mensaje interno
- **THEN** la API responde 500 con `codigo` `ERROR_INTERNO`, el cuerpo no contiene ese mensaje interno ni un stack trace, y el error queda en el log
