# Spec Delta — `turno-manual-api`

## Purpose

Define la carga autenticada de turnos por el odontólogo, sin la anticipación de 24 h de la reserva online, conservando el anti-solapamiento de la base, el upsert de paciente y el email de confirmación con el link de gestión.

## ADDED Requirements

### Requirement: Acceso autenticado al alta manual

`POST /api/turnos` MUST exigir sesión de odontólogo y CSRF válido, y MUST resolver el dueño desde la sesión, ignorando cualquier identificador recibido en el cuerpo o los headers (RN-SE-01). Sin sesión MUST responder 401 `NO_AUTENTICADO`; con CSRF inválido MUST responder 403 `ACCESO_DENEGADO`.

#### Scenario: Turno manual exitoso

- **GIVEN** el odontólogo `demo` con sesión iniciada, "Consulta" de 30 minutos y una franja el miércoles 2026-11-25 de 09:00 a 13:00 local, con el reloj en `2026-11-25T12:00:00Z`
- **WHEN** hace `POST /api/turnos` con `inicio` `2026-11-25T09:15:00-03:00` y datos válidos de `luis@example.com`
- **THEN** responde 201 con inicio `2026-11-25T09:15:00-03:00` y fin `2026-11-25T09:45:00-03:00`, y en la base queda un turno `RESERVADO` de origen `MANUAL`

#### Scenario: Identificador de odontólogo en el pedido

- **GIVEN** un odontólogo autenticado con sesión propia
- **WHEN** envía `odontologoId` de otro odontólogo en el cuerpo y en un header
- **THEN** el turno se crea para el odontólogo de la sesión y los identificadores recibidos no tienen efecto

#### Scenario: Falta de sesión o CSRF inválido

- **GIVEN** un pedido a `POST /api/turnos`
- **WHEN** no hay sesión o el token CSRF no coincide
- **THEN** la respuesta es 401 `NO_AUTENTICADO` o 403 `ACCESO_DENEGADO` según corresponda, en el formato uniforme, y no se crea ningún turno ni paciente

### Requirement: Cuerpo y respuesta del alta manual

El cuerpo MUST aceptar `prestacionId`, `inicio` con offset y los datos de contacto del paciente. La respuesta MUST ser 201 con el mismo contenido público que la reserva online: inicio y fin con offset, prestación, datos del consultorio y email al que se enviará el link, sin token ni hash.

#### Scenario: Respuesta sin secretos

- **GIVEN** un alta manual válida
- **WHEN** se recibe el 201
- **THEN** el cuerpo contiene inicio, fin, prestación, consultorio y email, y no contiene token, `token_hash` ni `email_gestion`

### Requirement: Sin regla de 24 horas para el odontólogo

El alta manual MUST permitir cualquier inicio futuro dentro de la agenda, incluso con menos de 24 h de anticipación (RN-TU-08). La regla de 24 h de RN-TU-03 MUST seguir aplicando a la reserva online del mismo horario.

#### Scenario: Horario de hoy dentro de las 24 horas

- **GIVEN** el reloj en `2026-11-25T12:00:00Z` (09:00 local) y una franja hasta las 13:00 local
- **WHEN** el odontólogo carga manualmente un turno el miércoles 2026-11-25 a las 09:15 local
- **THEN** responde 201 y el turno queda `RESERVADO`

#### Scenario: La reserva online sigue rechazando ese horario

- **GIVEN** el mismo consultorio, reloj y franja, y un turno manual ya creado a las 09:15 local
- **WHEN** un paciente consulta la disponibilidad pública o reserva online ese mismo inicio
- **THEN** el inicio no figura en la disponibilidad pública y la reserva online responde 422 `RN-TU-03` o 409 `RN-TU-01` según el estado del horario

### Requirement: Validación de la solicitud manual

`POST /api/turnos` MUST validar prestación, inicio con offset y los datos del paciente con las mismas reglas que la reserva online (RN-PA-04): DNI de 7 u 8 dígitos, nombre, apellido, teléfono E.164 y email válido. Un dato faltante o inválido MUST responder 400 con código `VALIDACION` y un detalle por campo, sin crear ni modificar nada.

#### Scenario: Datos del paciente inválidos

- **WHEN** se envía el formulario manual con DNI `12.345.678`, teléfono `3511234567` y email `luis@`
- **THEN** la API responde 400 con `codigo` `VALIDACION` y `detalles` incluye `dni`, `telefono` y `email`, sin crear paciente ni turno

#### Scenario: Inicio sin offset

- **WHEN** se envía `inicio` `2026-11-25T09:15:00` sin zona
- **THEN** la API responde 400 con `codigo` `VALIDACION` y no se crea el turno

#### Scenario: Prestación ajena o dada de baja

- **GIVEN** una prestación dada de baja o que pertenece a otro odontólogo
- **WHEN** el odontólogo intenta crear un turno manual con esa prestación
- **THEN** la API responde 404 `NO_ENCONTRADO` y no se crea ningún paciente ni turno

### Requirement: Horario dentro de la agenda y del horizonte

El servidor MUST rechazar con 422 `RN-AG-05` todo inicio que no sea un paso de 15 minutos dentro de una franja de atención, que no deje el turno completo dentro de la franja o que cruce un bloqueo (RN-AG-05). MUST rechazar con 422 `RN-TU-11` una fecha posterior al horizonte configurado (60 días por defecto). MUST rechazar con 422 `RN-TU-12` un inicio en el pasado o igual al ahora del servidor.

#### Scenario: Inicio fuera de paso o que cruza un bloqueo

- **GIVEN** una franja de 09:00 a 13:00 y un bloqueo de 10:00 a 11:00 local
- **WHEN** se intenta cargar manualmente un turno a las 09:10 o a las 09:45 de 30 minutos
- **THEN** la API responde 422 con código `RN-AG-05` y no se crea el turno

#### Scenario: Más allá del horizonte

- **GIVEN** el reloj en `2026-11-25T12:00:00Z` y el horizonte por defecto de 60 días
- **WHEN** se intenta cargar manualmente un turno el lunes 2027-01-25 a las 09:00 local
- **THEN** la API responde 422 con código `RN-TU-11`

#### Scenario: Inicio en el pasado

- **GIVEN** el reloj en `2026-11-25T12:00:00Z` (09:00 local)
- **WHEN** se intenta cargar manualmente un turno el miércoles 2026-11-25 a las 08:45 local
- **THEN** la API responde 422 con código `RN-TU-12` y no se crea el turno

### Requirement: Anti-solapamiento estricto en turnos manuales

Un turno manual MUST respetar la misma restricción de base de datos que la reserva online (RN-TU-01, DD-03). Si el intervalo `[inicio, fin)` se superpone con otro turno activo del mismo odontólogo, la API MUST responder 409 con código `RN-TU-01` y el mensaje “Ese horario acaba de ser tomado, elegí otro”, sin revelar datos de otros pacientes.

#### Scenario: Superposición con un turno existente

- **GIVEN** un turno `RESERVADO` de 09:00 a 09:30 local
- **WHEN** el odontólogo carga manualmente otro turno de 09:15 a 09:45 local
- **THEN** la API responde 409 `RN-TU-01` y en la base sigue habiendo un solo turno activo en ese rango

#### Scenario: Carrera entre dos altas manuales

- **GIVEN** un horario libre y dos solicitudes manuales autenticadas del mismo odontólogo con DNI distintos para ese inicio
- **WHEN** ambas llegan al mismo tiempo
- **THEN** exactamente una responde 201 y la otra responde 409 `RN-TU-01`, y en la base queda un único turno activo en ese horario

#### Scenario: Carrera manual contra reserva online

- **GIVEN** un horario libre y dos solicitudes simultáneas: un alta manual y una reserva online del mismo odontólogo para ese inicio
- **WHEN** ambas llegan al mismo tiempo
- **THEN** exactamente una responde 201 y la otra responde 409 `RN-TU-01`, y en la base queda un único turno activo en ese horario

### Requirement: Paciente identificado por DNI en el alta manual

El alta manual MUST crear el paciente si no existe para ese DNI y odontólogo, y si existe MUST actualizar nombre, apellido, teléfono y email con lo ingresado (RN-PA-01, RN-PA-02). Si la operación falla, el contacto MUST quedar como estaba.

#### Scenario: DNI repetido actualiza el contacto

- **GIVEN** un paciente con DNI `12345678` y email `viejo@example.com` del odontólogo `demo`
- **WHEN** se carga manualmente un turno con ese DNI y email `nuevo@example.com`
- **THEN** sigue habiendo un solo paciente para `demo`, su email es `nuevo@example.com` y el turno queda asociado a él con `email_gestion` `nuevo@example.com`

#### Scenario: Rechazo sin efectos secundarios

- **GIVEN** un paciente existente con email `viejo@example.com`
- **WHEN** el alta manual falla por solapamiento y responde 409
- **THEN** el email del paciente sigue siendo `viejo@example.com`, no se publica ningún evento y no se envía ningún correo

### Requirement: Token y confirmación por email del turno manual

El alta manual MUST generar un token de 32 bytes, guardar solo su hash SHA-256 y publicar `TurnoReservado` con origen `MANUAL` dentro de la transacción. El paciente MUST recibir el mismo email de confirmación con `/turno/{token}` que en la reserva online (RN-NO-01). El token en claro MUST NOT aparecer en la respuesta ni en la base.

#### Scenario: Email de confirmación del turno manual

- **GIVEN** el transporte de correo de pruebas y un alta manual válida
- **WHEN** se confirma la transacción
- **THEN** se publica un único `TurnoReservado` con origen `MANUAL` y token en claro, y el paciente recibe un email de confirmación cuyo cuerpo contiene un link `/turno/{token}` que permite consultar el turno

#### Scenario: Token ausente en la respuesta

- **GIVEN** un alta manual confirmada
- **WHEN** se inspeccionan la respuesta 201 y la fila del turno
- **THEN** ninguna contiene el token en claro y la columna `token_hash` tiene 64 caracteres hexadecimales

### Requirement: Límite de turnos futuros por DNI

El alta manual MUST NOT aplicar el límite de dos turnos futuros por DNI (RN-PA-03), porque el odontólogo conoce al paciente. Los turnos manuales MUST contarse igual para alcanzar ese límite cuando el mismo DNI reserva online después.

#### Scenario: Tercer turno manual para el mismo DNI

- **GIVEN** un DNI con dos turnos manuales `RESERVADO` futuros con `demo`
- **WHEN** el odontólogo carga manualmente un tercer turno futuro para ese DNI
- **THEN** responde 201 y no se devuelve el código `RN-PA-03`

#### Scenario: El turno manual cuenta para la reserva online

- **GIVEN** un DNI con dos turnos manuales `RESERVADO` futuros con `demo`
- **WHEN** ese mismo DNI intenta reservar online un tercer turno con `demo`
- **THEN** la reserva online responde 409 con código `RN-PA-03`

### Requirement: Aislamiento entre odontólogos

Las operaciones manuales autenticadas MUST limitarse a los datos del odontólogo de la sesión. Un prestación, paciente o turno de otro odontólogo MUST NOT ser visible ni modificable: la API MUST responder 404 con código `NO_ENCONTRADO` y el mismo mensaje genérico.

#### Scenario: Prestación de otro odontólogo

- **GIVEN** el odontólogo `demo` autenticado y una prestación activa del odontólogo `otro`
- **WHEN** `demo` intenta cargar un turno manual con esa prestación
- **THEN** responde 404 `NO_ENCONTRADO` sin revelar a quién pertenece la prestación
