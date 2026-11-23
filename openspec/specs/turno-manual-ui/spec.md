# turno-manual-ui Specification

## Purpose

Define la pantalla con la que el odontólogo carga un turno manual desde su agenda, eligiendo prestación, día y horario, completando los datos del paciente y viendo el resultado o los rechazos del servidor sin salir de la agenda.

## Requirements

### Requirement: Acceso desde la agenda

La vista de agenda MUST mostrar una acción “Nuevo turno” en su cabecera, tanto en la vista diaria como en la semanal, de escritorio y de celular. Al activarla MUST abrirse el formulario de carga sin recargar la página, conservando el rango de fechas visible.

#### Scenario: Botón en la agenda diaria

- **GIVEN** el odontólogo autenticado en `/agenda` con la vista diaria activa
- **WHEN** toca “Nuevo turno”
- **THEN** se abre el formulario de carga del turno y el rango de la agenda permanece sin cambios

#### Scenario: Botón en la agenda semanal y en celular

- **GIVEN** la agenda semanal en escritorio o la agenda diaria en un celular de 360 px
- **WHEN** se activa “Nuevo turno”
- **THEN** la acción está visible y accesible, y el formulario se adapta al ancho disponible sin desbordes

### Requirement: Selección de prestación, día y horario

El formulario MUST cargar las prestaciones del odontólogo, permitir elegir una, seleccionar un día y mostrar los horarios libres devueltos por la disponibilidad autenticada. MUST reutilizar el navegador de días y el selector de horarios compartidos. Un día sin horarios libres MUST mostrar un estado vacío con un mensaje claro, sin dejar confirmar.

#### Scenario: Horarios del día seleccionado

- **GIVEN** una prestación elegida y un día con horarios libres, incluidos horarios dentro de las próximas 24 h
- **WHEN** se abre el paso de horarios
- **THEN** la lista muestra esos horarios en hora local de Argentina, ordenados y con el mismo formato que el selector de la reserva pública

#### Scenario: Día sin horarios libres

- **GIVEN** una prestación elegida y un día sin franjas, bloqueado o sin horarios futuros
- **WHEN** se consulta ese día
- **THEN** se muestra el mensaje de estado vacío y el botón de confirmación permanece deshabilitado

### Requirement: Formulario de paciente y validación local

El formulario MUST reutilizar los campos y validadores del paciente (DNI de 7 u 8 dígitos, teléfono E.164, email, nombre y apellido obligatorios) y MUST mostrar mensajes por campo en español rioplatense con voseo. La validación local MUST NOT reemplazar la validación del servidor. Los datos de usuario MUST insertarse con `textContent` o mecanismos equivalentes que no interpreten HTML.

#### Scenario: Campos inválidos

- **WHEN** se confirma el formulario con DNI `12.345.678`, teléfono `3511234567` y email `luis@`
- **THEN** la pantalla muestra un mensaje junto a cada campo inválido y no envía la solicitud

#### Scenario: Errores de validación del servidor

- **GIVEN** una solicitud rechazada con 400 `VALIDACION`
- **WHEN** el cliente interpreta la respuesta
- **THEN** los mensajes de `detalles` se muestran en los campos correspondientes y los datos ya cargados se conservan

### Requirement: Envío de la solicitud manual

Al confirmar, la pantalla MUST enviar `POST /api/turnos` con CSRF a través del cliente compartido y MUST mostrar un estado de envío mientras espera. Ante 401, 403 o error de red MUST mostrar el estado apropiado sin perder los datos cargados y sin afirmar que el turno fue creado.

#### Scenario: Envío en curso

- **GIVEN** un formulario válido
- **WHEN** se confirma y la solicitud está en curso
- **THEN** el botón muestra un estado de envío y no se puede duplicar la confirmación

#### Scenario: Sesión vencida o error inesperado

- **GIVEN** una confirmación en curso
- **WHEN** la API responde 401 `NO_AUTENTICADO`, 403 `ACCESO_DENEGADO` o un error de red
- **THEN** se muestra el estado apropiado sin perder los datos cargados y no se afirma que el turno fue creado

### Requirement: Conflicto y errores de agenda en el formulario

Ante 409 `RN-TU-01` la pantalla MUST mostrar en el formulario “Ese horario acaba de ser tomado, elegí otro” y MUST refrescar los horarios disponibles sin borrar los datos del paciente. Ante 422 `RN-TU-11` o `RN-AG-05` MUST mostrar el mensaje del servidor y permitir elegir otro día u horario.

#### Scenario: Conflicto por horario tomado

- **GIVEN** el formulario completo y un horario que otro turno ocupó mientras se cargaba
- **WHEN** se confirma y la API responde 409 `RN-TU-01`
- **THEN** se muestra “Ese horario acaba de ser tomado, elegí otro”, se refrescan los horarios del día, y DNI, nombre, apellido, teléfono y email siguen cargados

#### Scenario: Error de agenda u horizonte

- **WHEN** la API responde 422 `RN-AG-05` o `RN-TU-11`
- **THEN** se muestra el mensaje devuelto y el odontólogo puede volver a elegir día u horario sin perder sus datos

#### Scenario: Alta exitosa y vuelta a la agenda

- **GIVEN** un formulario válido
- **WHEN** la API responde 201
- **THEN** el formulario se cierra, se recarga el rango visible de la agenda, el turno nuevo aparece con origen `MANUAL` y se muestra un aviso de confirmación

#### Scenario: Sesión vencida o error inesperado

- **GIVEN** una confirmación en curso
- **WHEN** la API responde 401 `NO_AUTENTICADO`, 403 `ACCESO_DENEGADO` o un error de red
- **THEN** se muestra el estado apropiado sin perder los datos cargados y no se afirma que el turno fue creado

### Requirement: Diseño y accesibilidad

La pantalla MUST usar los tokens del design system aprobado revisión 2, sin colores, espaciados, radios ni fuentes hardcodeados. MUST funcionar en 360 px de ancho, con etiquetas asociadas a cada campo, foco visible y estados de carga, error y éxito anunciados mediante atributos ARIA o roles apropiados.

#### Scenario: Uso en celular de 360 px

- **WHEN** se abre el formulario en un viewport de 360 px de ancho
- **THEN** todos los campos y botones son visibles y operables sin desplazamiento horizontal

#### Scenario: Estados anunciados

- **GIVEN** un campo con error o un aviso de conflicto
- **WHEN** se renderiza el formulario
- **THEN** el mensaje está asociado al campo o expuesto con un rol de alerta y el foco sigue siendo visible
