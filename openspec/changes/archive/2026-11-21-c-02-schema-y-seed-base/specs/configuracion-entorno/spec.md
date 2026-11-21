## MODIFIED Requirements

### Requirement: Esquema gestionado por migraciones
Al arrancar, la aplicación MUST aplicar las migraciones versionadas pendientes y MUST validar que el mapeo de entidades coincide con el esquema, sin crear ni modificar tablas automáticamente desde el mapeo.

#### Scenario: Base vacía
- **WHEN** la aplicación arranca contra una base vacía
- **THEN** registra el historial de migraciones, crea las tablas de negocio a partir de las migraciones versionadas y arranca con el mapeo de entidades validado contra ellas

#### Scenario: Mapeo que no coincide
- **WHEN** una entidad mapeada no tiene su tabla en el esquema migrado
- **THEN** la aplicación no arranca e informa la discrepancia
