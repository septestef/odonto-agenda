# Spec Delta — aislamiento-odontologo

## Purpose

Define el mecanismo por el que toda operación del odontólogo queda acotada a sus propios datos: el `odontologo_id` se toma siempre de la sesión y un recurso de otro odontólogo se responde 404 para no confirmar que existe (RN-SE-01, DD-01). Es el contrato que reutilizan C-05, C-12 y C-17.

## ADDED Requirements

### Requirement: El dueño del recurso sale de la sesión

Toda operación autenticada MUST resolver el odontólogo dueño a partir de la sesión y MUST ignorar cualquier identificador de odontólogo que llegue en el cuerpo, los parámetros o los headers del pedido (RN-SE-01).

#### Scenario: Identificador de odontólogo en el pedido

- **WHEN** un odontólogo autenticado envía en el cuerpo o en la query un identificador de odontólogo distinto del suyo
- **THEN** la operación se ejecuta sobre los datos del odontólogo de la sesión y el identificador recibido no tiene efecto

#### Scenario: Sesión sin odontólogo

- **WHEN** una operación que necesita el odontólogo de la sesión se ejecuta sin sesión iniciada
- **THEN** la respuesta es 401 `NO_AUTENTICADO` y nunca un error 500

### Requirement: Recurso de otro odontólogo responde 404

Cuando un odontólogo pide un recurso que pertenece a otro odontólogo, el sistema MUST responder 404 con `codigo` `NO_ENCONTRADO`, el mismo estado y el mismo cuerpo que para un recurso inexistente, y MUST NOT responder 403 ni revelar la existencia del recurso (RN-SE-01).

#### Scenario: Recurso ajeno

- **WHEN** el odontólogo A pide un recurso que pertenece al odontólogo B
- **THEN** la respuesta es 404 `NO_ENCONTRADO`

#### Scenario: Recurso inexistente

- **WHEN** el odontólogo A pide un recurso con un identificador que no existe
- **THEN** la respuesta es 404 `NO_ENCONTRADO` con el mismo cuerpo que en el caso de recurso ajeno

#### Scenario: Recurso propio

- **WHEN** el odontólogo A pide un recurso propio
- **THEN** la respuesta es 200 con el recurso
