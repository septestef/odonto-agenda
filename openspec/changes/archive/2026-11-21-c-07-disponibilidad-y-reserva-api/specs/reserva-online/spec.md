## ADDED Requirements

### Requirement: Perfil público del odontólogo
`GET /api/public/{slug}` MUST responder 200, sin sesión, con nombre, apellido y dirección del consultorio, las prestaciones activas (id, nombre, duración) y las fechas mínima y máxima reservables calculadas con el reloj del servidor. MUST NOT incluir email, contraseña ni prestaciones dadas de baja. En los escenarios, salvo aclaración, el reloj marca 2026-11-25T12:00:00Z (09:00 local), el horizonte es de 60 días y `demo` atiende de lunes a viernes de 09:00 a 13:00 con "Consulta" de 30 minutos.

#### Scenario: Perfil con prestaciones activas
- **GIVEN** el odontólogo `demo` con "Consulta" activa y "Blanqueamiento" dada de baja
- **WHEN** se hace `GET /api/public/demo`
- **THEN** la respuesta es 200, lista "Consulta" con 30 minutos, no lista "Blanqueamiento" y no contiene el email del odontólogo

#### Scenario: Ventana de fechas reservables
- **WHEN** se hace `GET /api/public/demo`
- **THEN** la fecha mínima reservable es 2026-11-26 y la máxima es 2027-01-24

#### Scenario: Slug inexistente u odontólogo inactivo
- **WHEN** se hace `GET /api/public/no-existe`, o `GET` del slug de un odontólogo inactivo
- **THEN** la API responde 404 con código `NO_ENCONTRADO` y el mismo mensaje en ambos casos

### Requirement: Validación de los datos de la reserva
`POST /api/public/{slug}/turnos` MUST exigir prestación, inicio con offset, DNI de 7 u 8 dígitos, nombre, apellido, teléfono en formato E.164 y email válido (RN-PA-04). Un dato faltante o inválido MUST responder 400 con código `VALIDACION` y un detalle por cada campo inválido, sin crear nada.

#### Scenario: DNI con formato inválido
- **WHEN** se reserva con DNI `12.345.678`
- **THEN** la API responde 400 con código `VALIDACION` y `detalles` incluye el campo `dni`

#### Scenario: Teléfono que no es E.164
- **WHEN** se reserva con teléfono `3511234567`
- **THEN** la API responde 400 con código `VALIDACION` y `detalles` incluye el campo `telefono`

#### Scenario: Email inválido y nombre vacío
- **WHEN** se reserva con email `luis@` y nombre vacío
- **THEN** la API responde 400 con `detalles` que incluye los campos `email` y `nombre`, y no se crea ni modifica ningún paciente ni turno

#### Scenario: Inicio sin offset
- **WHEN** se reserva con inicio `2026-11-27T09:00:00` (sin zona)
- **THEN** la API responde 400 y no se crea ningún turno

### Requirement: Reserva exitosa
Una reserva válida MUST crear un turno `RESERVADO` de origen `ONLINE`, con fin igual al inicio más la duración vigente de la prestación (RN-TU-02, RN-TU-04), y MUST responder 201 con inicio, fin, prestación, datos públicos del consultorio y el email al que se enviará el link.

#### Scenario: Reserva de un horario libre
- **WHEN** se reserva "Consulta" el viernes 2026-11-27 a las 09:00 con datos válidos
- **THEN** la API responde 201 con inicio `2026-11-27T09:00:00-03:00` y fin `2026-11-27T09:30:00-03:00`, y en la base queda un turno `RESERVADO`, origen `ONLINE`, de 12:00Z a 12:30Z

### Requirement: Token de gestión solo por email
Cada reserva MUST generar un token de 32 bytes aleatorios criptográficamente seguros, codificado en base64url, y MUST guardar solo su hash SHA-256 en hexadecimal (RN-SE-02). El token en claro MUST NOT aparecer en la respuesta HTTP, en la base ni en los logs.

#### Scenario: Respuesta sin token
- **WHEN** se completa una reserva
- **THEN** el cuerpo 201 no contiene el token ni su hash, y el turno guardado tiene un `token_hash` de 64 caracteres hexadecimales

#### Scenario: Tokens distintos por turno
- **WHEN** se completan dos reservas en horarios distintos
- **THEN** los dos turnos tienen `token_hash` distintos

### Requirement: El inicio respeta las reglas de agenda
Antes de reservar, el sistema MUST revalidar el inicio con las mismas reglas de la disponibilidad y rechazar con 422: `RN-TU-03` si es anterior a ahora + 24 h, `RN-TU-11` si supera el horizonte y `RN-AG-05` si no es un inicio ofrecido de una franja o cruza un bloqueo. Si se superpone con un turno activo MUST responder 409 `RN-TU-01`.

#### Scenario: Menos de 24 horas de anticipación
- **GIVEN** el reloj del servidor en `2026-11-25T15:00:00Z` (12:00 local)
- **WHEN** se reserva el jueves 2026-11-26 a las 11:45, dentro de la franja pero a 23 h 45 min
- **THEN** la API responde 422 con código `RN-TU-03` y no se crea el turno

#### Scenario: Fuera del horizonte
- **WHEN** se reserva el lunes 2027-01-25 a las 09:00
- **THEN** la API responde 422 con código `RN-TU-11`

#### Scenario: Fuera de franja o fuera de paso
- **WHEN** se reserva el viernes 2026-11-27 a las 12:45 (terminaría 13:15) o a las 09:10
- **THEN** la API responde 422 con código `RN-AG-05`

#### Scenario: Horario ya ocupado
- **GIVEN** un turno `RESERVADO` el viernes 2026-11-27 de 09:00 a 09:30
- **WHEN** se reserva ese viernes a las 09:00
- **THEN** la API responde 409 con `{ "codigo": "RN-TU-01", "mensaje": "Ese horario acaba de ser tomado, elegí otro" }`

### Requirement: Odontólogo o prestación inexistentes en la reserva
La reserva MUST responder 404 con código `NO_ENCONTRADO` cuando el slug no existe o el odontólogo está inactivo, o cuando la prestación no existe, está dada de baja o es de otro odontólogo, sin crear ni modificar pacientes.

#### Scenario: Prestación dada de baja
- **GIVEN** una prestación del odontólogo `demo` dada de baja
- **WHEN** se reserva con esa prestación
- **THEN** la API responde 404 con código `NO_ENCONTRADO` y no se crea ningún paciente ni turno

### Requirement: Paciente identificado por DNI dentro del odontólogo
La reserva MUST crear el paciente si no existe un paciente con ese DNI para ese odontólogo, y si existe MUST actualizar nombre, apellido, teléfono y email con lo ingresado (RN-PA-01, RN-PA-02). Si la reserva falla, los datos del paciente MUST quedar como estaban.

#### Scenario: DNI nuevo
- **WHEN** se reserva con un DNI que el odontólogo no tiene registrado
- **THEN** se crea un paciente de ese odontólogo con esos datos y el turno queda asociado a él

#### Scenario: DNI repetido actualiza el contacto
- **GIVEN** un paciente del odontólogo `demo` con DNI `12345678` y email `viejo@example.com`
- **WHEN** se reserva con DNI `12345678` y email `nuevo@example.com`
- **THEN** sigue habiendo un solo paciente con ese DNI para `demo`, su email es `nuevo@example.com` y el turno nuevo queda asociado a él

#### Scenario: Mismo DNI con otro odontólogo
- **GIVEN** un paciente con DNI `12345678` del odontólogo `otro`
- **WHEN** se reserva con DNI `12345678` en `demo`
- **THEN** se crea un paciente distinto para `demo` y el paciente de `otro` no cambia

#### Scenario: Reserva rechazada no toca el contacto
- **GIVEN** un paciente con DNI `12345678` y email `viejo@example.com`
- **WHEN** reserva con email `nuevo@example.com` un horario ya ocupado y recibe 409
- **THEN** el email del paciente sigue siendo `viejo@example.com`

### Requirement: Límite de dos turnos futuros por DNI
Un DNI MUST NOT tener más de 2 turnos `RESERVADO` con inicio futuro con el mismo odontólogo (RN-PA-03). Una reserva que excedería el límite MUST responder 409 con código `RN-PA-03`. El límite MUST cumplirse también con reservas simultáneas del mismo DNI.

#### Scenario: Tercer turno futuro
- **GIVEN** un DNI con 2 turnos `RESERVADO` futuros con `demo`
- **WHEN** ese DNI reserva un tercer horario libre con `demo`
- **THEN** la API responde 409 con código `RN-PA-03` y no se crea el turno

#### Scenario: Cancelados, pasados y de otro odontólogo no cuentan
- **GIVEN** un DNI con 2 turnos futuros `CANCELADO`, 1 turno `RESERVADO` ya pasado y 2 turnos `RESERVADO` futuros con el odontólogo `otro`
- **WHEN** ese DNI reserva con `demo`
- **THEN** la reserva responde 201

#### Scenario: Dos reservas simultáneas del mismo DNI en el límite
- **GIVEN** un DNI con 1 turno `RESERVADO` futuro con `demo`
- **WHEN** llegan a la vez dos reservas de ese DNI para dos horarios libres distintos
- **THEN** exactamente una responde 201, la otra responde 409 con código `RN-PA-03`, y el DNI queda con 2 turnos futuros

### Requirement: Reservas concurrentes del mismo horario por la API
Cuando dos reservas del mismo horario del mismo odontólogo llegan a la vez, exactamente una MUST responder 201 y la otra MUST responder 409 con código `RN-TU-01`, sin depender de la validación previa de la aplicación (RN-TU-01, RN-GL-01, DD-03).

#### Scenario: Dos pacientes reservan el mismo horario a la vez
- **WHEN** dos pacientes con DNI distintos envían a la vez `POST /api/public/demo/turnos` para el viernes 2026-11-27 a las 09:00
- **THEN** una respuesta es 201, la otra es 409 con `{ "codigo": "RN-TU-01", "mensaje": "Ese horario acaba de ser tomado, elegí otro" }`, y en la base queda un único turno activo en ese horario

### Requirement: La reserva no expone datos de otros pacientes
Las respuestas de reserva, exitosas o de error, MUST contener solo datos de la reserva enviada o del consultorio. MUST NOT revelar si otro paciente tiene el horario, ni sus datos (RN-SE-03).

#### Scenario: Conflicto sin datos ajenos
- **GIVEN** un turno `RESERVADO` de otro paciente el viernes 2026-11-27 a las 09:00
- **WHEN** se reserva ese horario y se recibe 409
- **THEN** el cuerpo contiene solo `codigo` y `mensaje`, sin nombre, DNI ni email del otro paciente
