# Delta: Modelo de datos

## MODIFIED Requirements

### Requirement: Tablas del modelo de negocio
Después de aplicar las migraciones, la base MUST tener las tablas `odontologo`, `horario_atencion`, `bloqueo`, `prestacion`, `paciente`, `turno` y `notificacion`, con las columnas, tipos y claves de `04_modelo_de_datos.md`. Toda tabla de negocio salvo `odontologo` y `notificacion` MUST referenciar a su odontólogo; `notificacion` MUST referenciar a su turno. Además, `turno` MUST tener `email_gestion varchar(255)` nullable para conservar el destinatario de gestión del turno, separado de `paciente.email`. La migración MUST dejar nulo el valor de turnos legados, sin reconstruirlo a partir del paciente ni de notificaciones.

#### Scenario: Esquema completo tras migrar
- **WHEN** las migraciones se aplican sobre una base vacía
- **THEN** existen las 7 tablas con sus columnas, y cada tabla tiene `creado_en` y `actualizado_en` no nulas

#### Scenario: Referencia a un odontólogo inexistente
- **WHEN** se inserta una prestación, un paciente, un bloqueo, un horario o un turno que referencia un odontólogo que no existe
- **THEN** la base rechaza la inserción

#### Scenario: Migración con turnos existentes
- **WHEN** se migra una base con turnos legados, pacientes con email y con o sin filas de notificación
- **THEN** los turnos conservan estado, hash y vínculos, y todos esos `email_gestion` siguen nulos; no se crea ni se altera ningún destinatario histórico
