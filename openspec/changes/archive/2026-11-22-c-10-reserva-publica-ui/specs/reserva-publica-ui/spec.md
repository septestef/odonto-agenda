# Spec Delta

## Purpose

Define la pantalla pública con la que un paciente, sin cuenta, elige una prestación y un horario libre, completa sus datos y reserva un turno, mostrando con claridad el resultado y los rechazos del servidor.

## ADDED Requirements

### Requirement: Carga del perfil público del odontólogo

Al abrir `/reservar/{slug}`, la pantalla MUST cargar `GET /api/public/{slug}` y mostrar el nombre y apellido del odontólogo, la dirección del consultorio cuando exista y solo las prestaciones activas con su duración. MUST NOT mostrar email, teléfono ni prestaciones dadas de baja. Mientras carga MUST mostrar un estado de carga; si el recurso no existe MUST mostrar un mensaje genérico de "no encontrado".

#### Scenario: Perfil con prestaciones activas
- **WHEN** el paciente abre `/reservar/demo` y el odontólogo tiene "Consulta" activa y "Blanqueamiento" dada de baja
- **THEN** la pantalla muestra "Consulta" con su duración, no muestra "Blanqueamiento" y no muestra el email del odontólogo

#### Scenario: Slug inexistente o inactivo
- **WHEN** el paciente abre `/reservar/no-existe` o el slug de un odontólogo inactivo y la API responde 404 `NO_ENCONTRADO`
- **THEN** la pantalla muestra un mensaje genérico de que el link no es válido, sin revelar cuál de los casos ocurrió y sin formulario de reserva

### Requirement: Selección de prestación, fecha y horario libre

La pantalla MUST permitir elegir una prestación y luego una fecha dentro de la ventana `fechaMinima`–`fechaMaxima` del perfil, y MUST mostrar los horarios libres que devuelve `GET /api/public/{slug}/disponibilidad`. Las fechas fuera de la ventana MUST NOT poder elegirse. Si la lista de horarios está vacía MUST mostrar un estado vacío con una acción para probar otro día.

#### Scenario: Horarios libres de un día
- **GIVEN** una prestación de 30 minutos y una franja de 09:00 a 10:00
- **WHEN** el paciente elige la prestación y el viernes 2026-11-27
- **THEN** la pantalla muestra los horarios 09:00, 09:15 y 09:30 como opciones seleccionables

#### Scenario: Día sin horarios
- **WHEN** la disponibilidad del día elegido devuelve una lista vacía
- **THEN** la pantalla muestra "No hay horarios libres este día" y permite elegir otra fecha o prestación

#### Scenario: Fechas fuera de la ventana reservable
- **WHEN** el perfil informa fecha mínima 2026-11-26 y máxima 2027-01-24
- **THEN** la pantalla no permite elegir el 2026-11-25 ni el 2027-01-25

### Requirement: Validación de los datos del paciente en la pantalla

Antes de enviar la reserva, la pantalla MUST validar que DNI tenga 7 u 8 dígitos sin puntos, que teléfono tenga formato E.164, que email tenga formato válido y que nombre y apellido no estén vacíos. Un campo inválido MUST mostrar su mensaje debajo del campo y MUST impedir el envío. La validación del cliente MUST NOT reemplazar la validación del servidor.

#### Scenario: DNI con puntos
- **WHEN** el paciente escribe `12.345.678` y toca "Confirmar reserva"
- **THEN** el campo DNI muestra su error y no se envía ninguna solicitud a la API

#### Scenario: Teléfono sin código de país
- **WHEN** el paciente escribe `3511234567`
- **THEN** el campo teléfono muestra el ejemplo de formato E.164 y la reserva no se envía

#### Scenario: Datos válidos
- **WHEN** el paciente completa DNI `12345678`, nombre, apellido, teléfono `+5493511234567` y un email válido
- **THEN** la pantalla habilita la confirmación y envía la reserva

### Requirement: Confirmación de la reserva

Al confirmar, la pantalla MUST enviar `POST /api/public/{slug}/turnos` con la prestación, el inicio con offset y los datos del paciente, MUST impedir envíos duplicados mientras la solicitud está en curso y MUST mostrar el estado de envío. Con 201 MUST mostrar una pantalla de éxito con fecha, hora, prestación, datos del consultorio y el email al que se enviará el link de gestión. El token y su hash MUST NOT mostrarse en ningún momento.

#### Scenario: Reserva exitosa
- **WHEN** la API responde 201 para el viernes 2026-11-27 a las 09:00
- **THEN** la pantalla muestra "¡Turno reservado!", el viernes 27 de noviembre a las 09:00, la prestación y el email ingresado, y no muestra ningún token

#### Scenario: Doble toque en confirmar
- **WHEN** el paciente toca "Confirmar reserva" dos veces seguidas mientras la primera solicitud sigue en curso
- **THEN** se envía una sola solicitud y el botón queda deshabilitado con el estado de envío

### Requirement: Horario tomado durante la reserva

Si la reserva responde 409 `RN-TU-01`, la pantalla MUST mostrar el mensaje "Ese horario acaba de ser tomado, elegí otro" y MUST refrescar la disponibilidad del día elegido para que el horario ocupado desaparezca. MUST conservar los datos del paciente ya ingresados.

#### Scenario: Conflicto por horario ocupado
- **GIVEN** el paciente completó sus datos para las 09:00
- **WHEN** la API responde 409 `RN-TU-01`
- **THEN** la pantalla muestra el mensaje de horario tomado, vuelve al paso de horario con la lista actualizada y mantiene DNI, nombre, apellido, teléfono y email

### Requirement: Límite de turnos futuros y ventana de reserva

La pantalla MUST traducir los rechazos del servidor a mensajes claros: 409 `RN-PA-03` por el límite de dos turnos futuros, 422 `RN-TU-03` por menos de 24 horas de anticipación, 422 `RN-TU-11` por superar el horizonte y 422 `RN-AG-05` por un horario que dejó de estar dentro de la agenda. En esos casos MUST NOT reintentar la reserva automáticamente.

#### Scenario: Límite de dos turnos
- **WHEN** la API responde 409 `RN-PA-03`
- **THEN** la pantalla explica que ya hay dos turnos futuros con ese odontólogo y que debe cancelar uno desde el link del email antes de reservar otro

#### Scenario: Fuera de la ventana de anticipación
- **WHEN** la API responde 422 `RN-TU-03`
- **THEN** la pantalla explica que los turnos se reservan con al menos 24 horas de anticipación y ofrece elegir otro horario

### Requirement: Errores de validación y de recurso inexistente del servidor

Si la API responde 400 `VALIDACION`, la pantalla MUST mostrar el detalle por campo que devuelve el servidor junto al campo correspondiente. Si la prestación o el odontólogo dejan de existir y la API responde 404 `NO_ENCONTRADO`, MUST mostrar un mensaje genérico y volver a cargar el perfil. Ante un error de red o 500 MUST mostrar un mensaje genérico con opción de reintentar, sin perder los datos ingresados.

#### Scenario: Validación rechazada por el servidor
- **WHEN** la API responde 400 `VALIDACION` con `detalles` para `email`
- **THEN** el campo email muestra el mensaje del servidor y el resto de los datos permanece cargado

#### Scenario: Prestación dada de baja entre la carga y la reserva
- **WHEN** la API responde 404 `NO_ENCONTRADO` al confirmar
- **THEN** la pantalla muestra un mensaje genérico, recarga el perfil y no deja el formulario en un estado inconsistente

### Requirement: Pantalla usable en celular y sin datos de otros pacientes

La pantalla MUST ser usable a 360 px de ancho, con una sola acción principal por paso, objetivos táctiles de al menos 44 px y textos en español rioplatense con voseo. MUST usar las variables de `design-system/tokens.css` y MUST insertar los textos provistos por el usuario o la API como texto, nunca como HTML. MUST NOT mostrar datos de otros pacientes ni identificadores de turnos ajenos.

#### Scenario: Vista en 360 px
- **WHEN** la pantalla se abre en un viewport de 360 px de ancho
- **THEN** prestación, días, horarios y formulario se muestran en una columna, sin desborde horizontal y con controles de al menos 44 px de alto

#### Scenario: Nombre con caracteres especiales
- **WHEN** el perfil o los datos ingresados contienen `<script>` o entidades HTML
- **THEN** la pantalla los muestra como texto literal y no ejecuta ni interpreta el contenido
