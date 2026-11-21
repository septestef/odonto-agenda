# modelo-datos Specification

## Purpose
Define el esquema relacional del negocio de OdontoAgenda, las restricciones de integridad que la base hace cumplir por sí misma, la auditoría de fechas de cada registro y la convención para versionar el esquema cuando varios changes avanzan en paralelo.

## Requirements

### Requirement: Tablas del modelo de negocio
Después de aplicar las migraciones, la base MUST tener las tablas `odontologo`, `horario_atencion`, `bloqueo`, `prestacion`, `paciente`, `turno` y `notificacion`, con las columnas, tipos y claves de `04_modelo_de_datos.md`. Toda tabla de negocio salvo `odontologo` y `notificacion` MUST referenciar a su odontólogo; `notificacion` MUST referenciar a su turno.

#### Scenario: Esquema completo tras migrar
- **WHEN** las migraciones se aplican sobre una base vacía
- **THEN** existen las 7 tablas con sus columnas, y cada tabla tiene `creado_en` y `actualizado_en` no nulas

#### Scenario: Referencia a un odontólogo inexistente
- **WHEN** se inserta una prestación, un paciente, un bloqueo, un horario o un turno que referencia un odontólogo que no existe
- **THEN** la base rechaza la inserción

### Requirement: Fechas y horas en UTC
Los instantes del negocio (inicio y fin de turnos y bloqueos, cancelación, envíos, auditoría) MUST persistirse como fecha y hora con zona, normalizados a UTC. Las franjas de atención MUST persistirse como hora local sin fecha.

#### Scenario: Instante persistido y leído
- **WHEN** se guarda un turno con inicio `2026-11-25T12:00:00Z` y se lo vuelve a leer
- **THEN** se obtiene exactamente el mismo instante, sin importar la zona horaria de la JVM

### Requirement: Restricciones de la agenda
La base MUST rechazar una franja de atención con día fuera de 1–7 (ISO, 1 = lunes) o con hora de inicio no anterior a la de fin, un bloqueo con inicio no anterior al fin y una prestación cuya duración no sea un múltiplo positivo de 15 minutos (RN-AG-03). El nombre de una prestación MUST ser único por odontólogo.

#### Scenario: Duración inválida
- **WHEN** se inserta una prestación con duración 0, negativa o 20 minutos
- **THEN** la base la rechaza

#### Scenario: Duración válida
- **WHEN** se inserta una prestación de 45 minutos
- **THEN** la base la acepta

#### Scenario: Franja invertida
- **WHEN** se inserta una franja de 13:00 a 09:00, o con día de semana 0 u 8
- **THEN** la base la rechaza

#### Scenario: Bloqueo vacío o invertido
- **WHEN** se inserta un bloqueo cuyo fin es igual o anterior a su inicio
- **THEN** la base lo rechaza

#### Scenario: Prestación repetida
- **WHEN** un odontólogo ya tiene la prestación "Consulta" y se inserta otra "Consulta" para el mismo odontólogo
- **THEN** la base la rechaza, pero acepta "Consulta" para otro odontólogo

### Requirement: Identidad del odontólogo
El email del odontólogo MUST ser único y su `slug` MUST ser único y cumplir el formato `^[a-z0-9-]+$`.

#### Scenario: Slug con formato inválido
- **WHEN** se inserta un odontólogo con slug `Dr Pérez`
- **THEN** la base lo rechaza

#### Scenario: Email repetido
- **WHEN** se inserta un odontólogo con un email que ya usa otro
- **THEN** la base lo rechaza

### Requirement: Paciente único por DNI dentro de cada odontólogo
El DNI del paciente MUST tener 7 u 8 dígitos y MUST ser único por odontólogo (RN-PA-01). El mismo DNI con dos odontólogos distintos MUST representar dos pacientes distintos.

#### Scenario: DNI repetido en el mismo odontólogo
- **WHEN** se inserta un paciente con un DNI que ya existe para ese odontólogo
- **THEN** la base lo rechaza

#### Scenario: Mismo DNI en otro odontólogo
- **WHEN** se inserta un paciente con un DNI que ya existe pero para otro odontólogo
- **THEN** la base lo acepta como un paciente distinto

#### Scenario: DNI con formato inválido
- **WHEN** se inserta un paciente con DNI `12A45678` o de 6 dígitos
- **THEN** la base lo rechaza

### Requirement: Valores cerrados de turnos y notificaciones
El estado del turno MUST ser `RESERVADO`, `CANCELADO`, `ATENDIDO` o `AUSENTE`; su origen `ONLINE` o `MANUAL`; quien canceló, si está informado, `PACIENTE` u `ODONTOLOGO`. El tipo de notificación MUST ser `CONFIRMACION`, `CANCELACION`, `REPROGRAMACION` o `RECORDATORIO`, y su estado `ENVIADO` o `ERROR`. Un turno MUST terminar después de empezar.

#### Scenario: Estado desconocido
- **WHEN** se inserta un turno con estado `PENDIENTE`
- **THEN** la base lo rechaza

#### Scenario: Turno con fin no posterior al inicio
- **WHEN** se inserta un turno cuyo fin es igual o anterior a su inicio
- **THEN** la base lo rechaza

### Requirement: Token de gestión guardado solo como hash
El turno MUST guardar únicamente el hash SHA-256 de su token de gestión, en hexadecimal de 64 caracteres, único entre todos los turnos (RN-SE-02). El esquema MUST NOT tener ninguna columna para el token en claro.

#### Scenario: Hash repetido
- **WHEN** se inserta un turno con un `token_hash` que ya tiene otro turno
- **THEN** la base lo rechaza

#### Scenario: Sin token en claro
- **WHEN** se inspeccionan las columnas de `turno`
- **THEN** la única columna relacionada con el token es `token_hash`

### Requirement: Fechas de auditoría con el reloj del servidor
Al crear un registro, la aplicación MUST completar `creado_en` y `actualizado_en` con el instante del reloj del servidor; al modificarlo, MUST actualizar solo `actualizado_en`. Con un reloj fijo en los tests, los valores MUST ser exactamente los del reloj.

#### Scenario: Alta con reloj fijo
- **WHEN** con el reloj fijado en `2026-11-25T12:00:00Z` se guarda una prestación nueva
- **THEN** `creado_en` y `actualizado_en` valen `2026-11-25T12:00:00Z`

#### Scenario: Modificación posterior
- **WHEN** con el reloj avanzado una hora se modifica esa prestación
- **THEN** `actualizado_en` vale `2026-11-25T13:00:00Z` y `creado_en` no cambia

### Requirement: Versionado de migraciones por change
Cada change MUST numerar sus migraciones con su propio número (`V2` para C-02, `V7` para C-07, y `V<NN>_<k>` si un change necesita más de una). Las versiones 900 en adelante MUST reservarse para datos de demo. La aplicación MUST aplicar una migración pendiente aunque su versión sea menor que la última ya aplicada.

#### Scenario: Migración que llega fuera de orden
- **WHEN** una base ya tiene aplicadas `V2` y `V7`, y se integra un change que trae `V5`
- **THEN** al arrancar, la aplicación aplica `V5` sin error y queda registrada en el historial
