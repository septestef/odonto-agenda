# fechas Specification

## Purpose
Define las utilidades de fecha y hora del frontend para mostrar turnos y días en hora de Argentina, sin librerías externas y sin dejar que el reloj del navegador decida reglas de negocio.

## Requirements

### Requirement: Interpretación en hora de Argentina
Las fechas DEBE (MUST) interpretarse y mostrarse en la zona horaria `America/Argentina/Buenos_Aires`, usando los mecanismos internacionales del entorno y sin librerías de fechas. Un instante persistido en UTC DEBE (MUST) convertirse a la hora local de Argentina antes de mostrarse.

#### Scenario: Instante UTC a hora local
- **WHEN** se formatea el instante `2026-11-19T13:30:00Z`
- **THEN** se obtiene `10:30` en hora de Argentina

#### Scenario: Independencia de la zona del dispositivo
- **WHEN** la misma función se ejecuta en un navegador configurado en otra zona horaria
- **THEN** el resultado es el mismo, porque la zona de negocio se explicita en cada operación

### Requirement: Fecha local y validación de `YYYY-MM-DD`
La aplicación DEBE (MUST) producir fechas locales con formato `YYYY-MM-DD` y DEBE (MUST) validar que una cadena tenga ese formato y represente una fecha real.

#### Scenario: Cruce de día en la conversión
- **WHEN** se obtiene la fecha local de `2026-11-20T02:30:00Z`
- **THEN** el resultado es `2026-11-19`, porque en Argentina todavía es 19 de noviembre

#### Scenario: Fecha inexistente
- **WHEN** se valida `2026-02-30`
- **THEN** se rechaza como inválida

#### Scenario: Formato inválido
- **WHEN** se valida `19/11/2026` o `2026-1-5`
- **THEN** se rechaza por no respetar `YYYY-MM-DD`

### Requirement: Aritmética de días y rangos horarios
La aplicación DEBE (MUST) sumar y restar días enteros respetando cambios de mes, año y bisiesto, y DEBE (MUST) formatear rangos horarios con dos dígitos para hora y minuto.

#### Scenario: Fin de mes
- **WHEN** se suma un día a `2026-11-30`
- **THEN** se obtiene `2026-12-01`

#### Scenario: Año bisiesto
- **WHEN** se suma un día a `2028-02-28`
- **THEN** se obtiene `2028-02-29`

#### Scenario: Rango horario
- **WHEN** se formatea un rango que empieza a las 10:30 y termina a las 11:15
- **THEN** se obtiene `10:30–11:15`, con guion medio y sin ajuste de zona

### Requirement: El "ahora" es una dependencia inyectada
Las operaciones de fecha DEBE (MUST) recibir el instante actual como parámetro cuando lo necesiten. Ninguna utilidad DEBE (MUST) consultar directamente el reloj del navegador para tomar decisiones; su uso se limita a mostrar información al usuario.

#### Scenario: Prueba con instante fijo
- **WHEN** se llama a una operación con un instante fijo inyectado
- **THEN** el resultado es siempre el mismo, sin importar cuándo se ejecute la prueba

#### Scenario: Sin reloj implícito
- **WHEN** se revisa el código de las utilidades de fecha
- **THEN** no hay lecturas directas del reloj del navegador dentro de la lógica de cálculo
