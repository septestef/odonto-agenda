# Spec Delta — configuracion-entorno

## Purpose

Establece cómo se configura y arranca OdontoAgenda: secretos y parámetros solo por variables de entorno, esquema de base gestionado por migraciones versionadas y arranque seguro contra PostgreSQL.

## ADDED Requirements

### Requirement: Configuración por variables de entorno
La aplicación MUST leer la conexión a la base, el SMTP, la URL base, la zona horaria, el horizonte de reserva, la contraseña de demo y el perfil activo desde las variables de entorno documentadas en `08_arquitectura_propuesta.md`. Un archivo `.env` local, si existe, MUST poder aportar esas variables sin versionarse.

#### Scenario: Variables desde el entorno
- **WHEN** la aplicación arranca con `DB_URL`, `DB_USER` y `DB_PASSWORD` definidas en el entorno
- **THEN** se conecta a esa base con esas credenciales

#### Scenario: Variables desde `.env`
- **WHEN** las variables no están en el entorno pero sí en un `.env` local
- **THEN** la aplicación las toma de ese archivo

### Requirement: Secretos fuera del repositorio
El repositorio MUST incluir un `.env.example` con todas las variables y sin valores reales para las sensibles, y MUST ignorar `.env`. Ningún archivo versionado MUST contener contraseñas reales, y los valores sensibles MUST NOT escribirse en los logs.

#### Scenario: Plantilla de variables
- **WHEN** se revisa `.env.example`
- **THEN** contiene todas las variables de la KB (y las de la base de test) y las sensibles están vacías

#### Scenario: `.env` ignorado
- **WHEN** existe un `.env` en la raíz del repo
- **THEN** el control de versiones lo ignora

### Requirement: Esquema gestionado por migraciones
Al arrancar, la aplicación MUST aplicar las migraciones versionadas pendientes y MUST validar que el mapeo de entidades coincide con el esquema, sin crear ni modificar tablas automáticamente desde el mapeo.

#### Scenario: Base vacía
- **WHEN** la aplicación arranca contra una base vacía
- **THEN** registra el historial de migraciones y arranca sin crear tablas de negocio

#### Scenario: Mapeo que no coincide
- **WHEN** una entidad mapeada no tiene su tabla en el esquema migrado
- **THEN** la aplicación no arranca e informa la discrepancia

### Requirement: Tests de integración contra PostgreSQL real
Los tests de integración MUST correr contra una base PostgreSQL local dedicada (`turnotarde_test` por defecto, configurable por variables de entorno) y MUST NOT usar bases embebidas. Si la base de test no está disponible, los tests MUST fallar con un mensaje que indique cómo crearla, nunca saltearse.

#### Scenario: Base de test disponible
- **WHEN** se corren los tests de integración con la base `turnotarde_test` creada
- **THEN** el contexto de la aplicación levanta contra esa base y los tests pasan

#### Scenario: Base de test ausente
- **WHEN** se corren los tests de integración y la base de test no existe o no acepta la conexión
- **THEN** los tests fallan con un mensaje que nombra la base y remite a las instrucciones de creación
