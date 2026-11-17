# Spec Delta — tiempo-servidor

## Purpose

Garantiza que toda decisión que depende de la hora use el reloj del servidor en UTC y que las fechas del negocio se interpreten y muestren en la zona horaria del consultorio, de forma reproducible en tests.

## ADDED Requirements

### Requirement: El "ahora" lo define el servidor
Toda lógica del backend que necesite la fecha u hora actual MUST obtenerla de un único reloj del servidor en UTC, provisto por la aplicación. Ninguna regla MUST depender de la hora enviada por el cliente ni de la zona horaria por defecto de la JVM o del sistema operativo.

#### Scenario: Reloj del servidor en UTC
- **WHEN** la aplicación arranca con la configuración por defecto
- **THEN** el reloj que reciben los componentes informa el instante actual en la zona UTC

#### Scenario: Zona por defecto de la JVM distinta
- **WHEN** la JVM corre con una zona por defecto distinta de UTC y de la del negocio
- **THEN** los instantes y conversiones del negocio dan el mismo resultado que con cualquier otra zona por defecto

### Requirement: Reloj reemplazable en tests
El reloj del servidor MUST poder reemplazarse por un reloj fijo en los tests, de modo que cualquier componente que dependa del "ahora" devuelva resultados deterministas.

#### Scenario: Reloj fijo
- **WHEN** un test fija el reloj en `2026-11-25T12:00:00Z`
- **THEN** los componentes que consultan el "ahora" obtienen exactamente ese instante

### Requirement: Prohibido leer la hora sin el reloj
El código de producción del backend MUST NOT obtener la hora actual sin pasar por el reloj inyectado (por ejemplo, llamando a `now()` sin reloj). La build MUST fallar si aparece una llamada así.

#### Scenario: Llamada directa a la hora del sistema
- **WHEN** una clase de producción llama a la hora del sistema sin usar el reloj inyectado
- **THEN** la verificación automática de la build falla e indica la clase infractora

### Requirement: Zona horaria del negocio
Las fechas y horas del negocio MUST interpretarse y mostrarse en la zona configurada por `APP_TIMEZONE`, con valor por defecto `America/Argentina/Buenos_Aires`, y MUST convertirse a instantes UTC para persistirse.

#### Scenario: Hora local a instante
- **WHEN** se interpreta el 2026-11-25 a las 09:00 en la zona del negocio por defecto
- **THEN** el instante resultante es `2026-11-25T12:00:00Z`

#### Scenario: Instante a hora local
- **WHEN** se muestra el instante `2026-11-25T23:30:00Z` en la zona del negocio por defecto
- **THEN** la fecha y hora local es el 2026-11-25 a las 20:30

#### Scenario: Fecha "de hoy" del negocio
- **WHEN** el reloj fijo marca `2026-11-26T01:00:00Z`
- **THEN** la fecha actual del negocio es el 2026-11-25
