## Purpose

Define los datos iniciales que tiene la aplicación cuando corre con el perfil `demo` (un odontólogo con su agenda y turnos de ejemplo) y cómo se fija su contraseña sin que quede escrita en el repositorio.

## ADDED Requirements

### Requirement: Datos de demo solo con el perfil demo
Con el perfil `demo` activo, las migraciones MUST cargar un odontólogo con slug `demo`, su horario de lunes a viernes de 9 a 13 y de 16 a 20 (hora del consultorio), las prestaciones Consulta (30 min), Limpieza (45 min), Extracción (60 min) y Arreglo de caries (45 min), pacientes ficticios y turnos de ejemplo. Sin ese perfil, MUST NOT cargarse ningún dato de demo.

#### Scenario: Arranque con perfil demo
- **WHEN** la aplicación arranca con el perfil `demo` sobre una base vacía y `DEMO_PASSWORD` definida
- **THEN** existe el odontólogo `demo` con 10 franjas de atención y las 4 prestaciones activas

#### Scenario: Arranque sin perfil demo
- **WHEN** la aplicación arranca sin el perfil `demo` sobre una base vacía
- **THEN** las tablas de negocio quedan vacías

#### Scenario: Segundo arranque
- **WHEN** la aplicación se reinicia con el perfil `demo` sobre una base que ya tiene los datos de demo
- **THEN** no se duplica ningún registro

### Requirement: Turnos de ejemplo en la semana de la demo
Los datos de demo MUST incluir turnos de ejemplo entre el lunes 23 y el viernes 27 de noviembre de 2026, dentro del horario de atención y sin superponerse entre sí, incluyendo al menos un turno cancelado. MUST quedar horarios libres el jueves 26 y el viernes 27 para poder reservar en vivo durante la demo.

#### Scenario: Agenda de la semana de la demo
- **WHEN** se consultan los turnos del odontólogo `demo` del 23 al 27/11/2026
- **THEN** hay turnos activos y al menos uno cancelado, todos dentro de las franjas de atención

#### Scenario: Pacientes ficticios
- **WHEN** se revisan los pacientes de demo
- **THEN** sus emails usan el dominio reservado `example.com` y ningún dato corresponde a una persona real

### Requirement: Contraseña de demo desde el entorno
La contraseña del odontólogo de demo MUST tomarse de `DEMO_PASSWORD` y MUST guardarse solo como hash BCrypt. Ningún archivo versionado MUST contener esa contraseña ni un hash utilizable de ella. Con el perfil `demo`, si `DEMO_PASSWORD` falta o está vacía, la aplicación MUST NOT arrancar e MUST indicar qué variable falta.

#### Scenario: Contraseña aplicada al arrancar
- **WHEN** la aplicación arranca con el perfil `demo` y `DEMO_PASSWORD` definida
- **THEN** el odontólogo `demo` tiene un hash BCrypt que verifica esa contraseña

#### Scenario: Cambio de contraseña entre arranques
- **WHEN** se reinicia la aplicación con otro valor de `DEMO_PASSWORD`
- **THEN** el hash guardado pasa a verificar el valor nuevo y deja de verificar el anterior

#### Scenario: Falta la variable
- **WHEN** la aplicación arranca con el perfil `demo` y sin `DEMO_PASSWORD`
- **THEN** el arranque falla con un mensaje que nombra `DEMO_PASSWORD`, sin mostrar ningún secreto

#### Scenario: Antes de aplicar la contraseña
- **WHEN** se carga el seed y todavía no se aplicó `DEMO_PASSWORD`
- **THEN** el hash guardado no verifica ninguna contraseña
