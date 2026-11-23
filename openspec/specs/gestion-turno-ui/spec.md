# gestion-turno-ui Specification

## Purpose
Define la pantalla `/turno/{token}` con la que el paciente, sin cuenta, consulta su turno desde el link del email y lo cancela o lo cambia de horario, viendo con claridad el resultado y los rechazos del servidor.

## Requirements

### Requirement: Consulta del turno por link

Al abrir `/turno/{token}`, la pantalla MUST cargar `GET /api/public/turnos/{token}` y mostrar fecha, hora de inicio y fin, prestación, nombre del odontólogo y dirección del consultorio cuando exista. MUST NOT mostrar datos del paciente, ids ni el token. Mientras carga MUST mostrar un estado de carga.

#### Scenario: Turno reservado y futuro
- **WHEN** el paciente abre el link de un turno `RESERVADO` del viernes 27/11 a las 09:00 de "Consulta"
- **THEN** la pantalla muestra "Viernes 27 de noviembre", "09:00 a 09:30", "Consulta" y el odontólogo, sin DNI, email ni token

#### Scenario: Link inválido o desconocido
- **WHEN** la API responde 404 `NO_ENCONTRADO`
- **THEN** la pantalla muestra "No encontramos este turno" con la indicación de revisar el link del email más reciente, sin acciones y sin revelar si el token existió

### Requirement: Acciones según lo que permite el servidor

La pantalla MUST mostrar "Cancelar turno" solo si `puedeCancelar` es verdadero y "Cambiar horario" solo si `puedeReprogramar` es verdadero. MUST NOT calcular esos permisos con el reloj del navegador.

#### Scenario: Turno que no se puede reprogramar desde el link
- **WHEN** el GET devuelve `puedeCancelar: true` y `puedeReprogramar: false`
- **THEN** la pantalla muestra "Cancelar turno", no muestra "Cambiar horario" y explica que para cambiar el horario hay que cancelar y reservar de nuevo

#### Scenario: Turno sin acciones
- **WHEN** el GET devuelve `puedeCancelar: false` y `puedeReprogramar: false`
- **THEN** la pantalla muestra el turno en solo lectura, sin botones de acción

### Requirement: Cancelación con confirmación previa

"Cancelar turno" MUST abrir una confirmación que repita fecha, hora y prestación antes de enviar `POST /api/public/turnos/{token}/cancelar`. Mientras la solicitud está en curso MUST impedir un segundo envío. Con 204 MUST volver a pedir el turno con el mismo token y mostrarlo cancelado en solo lectura.

#### Scenario: El paciente confirma la cancelación
- **WHEN** el paciente toca "Cancelar turno", luego "Sí, cancelar turno" y la API responde 204
- **THEN** la pantalla relee el turno, muestra "Cancelaste tu turno" y el turno como "Cancelado" en solo lectura, y avisa que le llega un email

#### Scenario: El paciente se arrepiente
- **WHEN** el paciente abre la confirmación y toca "No, volver"
- **THEN** no se envía ninguna solicitud y el turno sigue mostrándose activo

#### Scenario: Doble toque al confirmar
- **WHEN** el paciente toca dos veces "Sí, cancelar turno" antes de recibir la respuesta
- **THEN** la API recibe una sola solicitud de cancelación

### Requirement: Reprogramación con la misma prestación

"Cambiar horario" MUST mostrar días dentro de `fechaMinima`–`fechaMaxima` del perfil público del odontólogo y los horarios libres de `GET /api/public/{slug}/disponibilidad` para la prestación del turno. MUST marcar el día del turno actual y MUST NOT permitir elegir otra prestación ni fechas fuera de la ventana.

#### Scenario: Elegir un día con horarios
- **WHEN** el paciente entra a "Cambiar horario" y elige el jueves 26
- **THEN** la pantalla pide la disponibilidad de la misma prestación para el 26 y muestra solo esos horarios libres

#### Scenario: Navegar por semanas
- **WHEN** el paciente toca "Semana siguiente" hasta llegar a la semana que contiene `fechaMaxima`
- **THEN** "Semana siguiente" queda deshabilitado y ningún día posterior a `fechaMaxima` puede elegirse

#### Scenario: Día sin horarios
- **WHEN** la disponibilidad del día elegido devuelve una lista vacía
- **THEN** la pantalla indica que no hay horarios libres ese día y no habilita "Confirmar cambio"

### Requirement: Confirmación del cambio de horario

Con un horario elegido, la pantalla MUST mostrar el horario actual tachado y el nuevo antes de confirmar. Al confirmar MUST enviar `POST /api/public/turnos/{token}/reprogramar` con el `inicio` tal como lo devolvió la disponibilidad e impedir envíos duplicados. Con 204 MUST releer el turno con el mismo token y confirmar el horario nuevo sin redirigir.

#### Scenario: Reprogramación exitosa
- **WHEN** el paciente elige el jueves 26 a las 10:30, confirma y la API responde 204
- **THEN** la pantalla relee el turno original, muestra "Listo, cambiaste tu turno al jueves 26/11 a las 10:30" y avisa que el link nuevo llega por email, sin mostrar ni pedir ese link

#### Scenario: El `inicio` no se re-serializa
- **WHEN** la disponibilidad ofrece `2026-11-26T10:30:00-03:00` y el paciente lo confirma
- **THEN** el cuerpo enviado es `{ "inicio": "2026-11-26T10:30:00-03:00" }` con el mismo texto

### Requirement: Rechazos al reprogramar

La pantalla MUST reaccionar por `codigo`: 409 `RN-TU-01` avisa que el horario acaba de ser tomado; 422 `RN-TU-03`, `RN-TU-11` o `RN-AG-05` avisan que ese horario ya no está disponible. En ambos casos MUST refrescar la disponibilidad del día y limpiar la selección. 409 `RN-SE-04` explica que no se puede cambiar desde el link y deja cancelar.

#### Scenario: Horario tomado mientras elegía
- **WHEN** el paciente confirma 10:30 y la API responde 409 `RN-TU-01`
- **THEN** la pantalla muestra "Ese horario acaba de ser tomado", vuelve a pedir la disponibilidad del día, limpia la selección y deja de ofrecer 10:30 si ya no está libre

#### Scenario: Horario fuera de la agenda
- **WHEN** el paciente confirma un horario y la API responde 422 `RN-AG-05`
- **THEN** la pantalla muestra "Ese horario ya no está disponible" y refresca la disponibilidad del día

#### Scenario: No se puede reprogramar desde el link
- **WHEN** la API responde 409 `RN-SE-04`
- **THEN** la pantalla explica que este turno no se puede cambiar desde el link y ofrece volver al turno para cancelarlo

### Requirement: Turno que ya no se puede modificar

Si una acción responde 409 `RN-TU-07` o `RN-SE-02`, o un 404 `NO_ENCONTRADO` al reprogramar, la pantalla MUST mostrar un mensaje claro y volver a pedir el turno para mostrar su estado actual en solo lectura, sin reintentar la acción.

#### Scenario: El turno ya empezó
- **WHEN** el paciente confirma la cancelación y la API responde 409 `RN-TU-07`
- **THEN** la pantalla muestra "No pudimos hacer el cambio" con "Este turno ya no se puede modificar" y el turno releído en solo lectura

#### Scenario: El turno ya fue cancelado en otra pestaña
- **WHEN** el paciente confirma un cambio y la API responde 409 `RN-SE-02`
- **THEN** la pantalla muestra el mismo aviso y el estado actual del turno releído

### Requirement: Vista de solo lectura

Un turno `CANCELADO`, `ATENDIDO`, `AUSENTE` o con inicio pasado MUST mostrarse en solo lectura, con su estado ("Cancelado" o "Pasado") y un enlace para reservar un turno nuevo con el mismo odontólogo, sin acciones de cancelar ni cambiar.

#### Scenario: Link viejo después de reprogramar
- **WHEN** el paciente vuelve a abrir el link original de un turno que reprogramó
- **THEN** la pantalla muestra ese turno como "Cancelado", sin acciones y con "Reservar otro turno"

#### Scenario: Turno pasado
- **WHEN** el GET devuelve un turno con inicio anterior a ahora y `puedeCancelar: false`
- **THEN** la pantalla muestra el turno con la marca "Pasado" y sin acciones

### Requirement: Errores de red y del servidor

Ante un error de red, un 500 o una respuesta con forma inesperada, la pantalla MUST mostrar un mensaje genérico con "Reintentar" y conservar la selección hecha. MUST NOT reintentar automáticamente una cancelación ni una reprogramación.

#### Scenario: Se corta la conexión al cancelar
- **WHEN** el paciente confirma la cancelación y la solicitud falla por red
- **THEN** la pantalla muestra "No pudimos conectarnos" con "Reintentar", aclara que el turno no cambió y no envía otra solicitud hasta que el paciente lo pide

### Requirement: Privacidad del link y accesibilidad

La pantalla MUST usar el token solo en las rutas de su propia API: MUST NOT guardarlo en almacenamiento del navegador, mostrarlo, loguearlo ni enviarlo a terceros. MUST ser usable a 360 px, con objetivos táctiles de 44 px, tokens de `design-system/tokens.css`, voseo y datos de la API insertados como texto.

#### Scenario: El token no sale de la ruta
- **WHEN** el paciente usa la pantalla completa (consulta, cambio y cancelación)
- **THEN** `localStorage` y `sessionStorage` no contienen el token, la consola no lo muestra y ninguna solicitud lo envía fuera de `/api/public/turnos/`

#### Scenario: Pantalla angosta
- **WHEN** el paciente abre la pantalla a 360 px de ancho
- **THEN** no hay scroll horizontal de la página y la confirmación de cancelación se abre como hoja inferior con foco en su título
