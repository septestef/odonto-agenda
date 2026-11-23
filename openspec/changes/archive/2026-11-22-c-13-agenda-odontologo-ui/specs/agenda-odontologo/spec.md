# Spec Delta — `agenda-odontologo` (C-13)

## ADDED Requirements

### Requirement: Vista inicial y navegación de la agenda

La interfaz MUST exponer la ruta `/agenda` con un selector Día/Semana y navegación anterior, "Hoy" y siguiente. En la primera carga MUST elegir la vista diaria cuando el ancho del viewport sea menor a 720 px y la vista semanal cuando sea mayor o igual a 720 px. La fecha inicial MUST ser la fecha actual en `America/Argentina/Buenos_Aires`. La vista diaria MUST pedir `GET /api/turnos?desde=F&hasta=F+1`; la semanal MUST pedir `desde` igual al lunes de esa semana y `hasta` siete días después. Anterior/siguiente MUST desplazar un día o una semana según la vista activa; "Hoy" MUST volver a la fecha actual y MUST estar deshabilitado cuando ya está en ella. Cambiar de vista o de fecha MUST conservar la otra elección mientras la vista siga montada.

#### Scenario: Primera carga en celular

- **WHEN** se abre `/agenda` con un viewport de 390 px
- **THEN** se muestra la vista diaria de la fecha local de Argentina, con "Día" pulsado y la consulta `?desde=YYYY-MM-DD&hasta=YYYY-MM-DD+1`

#### Scenario: Primera carga en escritorio

- **WHEN** se abre `/agenda` con un viewport de 1440 px
- **THEN** se muestra la vista semanal que empieza el lunes local, con "Semana" pulsado y una consulta de siete días

#### Scenario: Navegación y vuelta a hoy

- **WHEN** se avanza un día o una semana y después se toca "Hoy"
- **THEN** el rango solicitado se desplaza en esa unidad y "Hoy" vuelve a la fecha actual del negocio, quedando deshabilitado

### Requirement: Contenido visible de cada turno

Para cada turno devuelto, la interfaz MUST mostrar el horario local de inicio y fin, nombre y apellido del paciente, nombre de la prestación, su `duracionMinutos` real, el estado y el origen (`ONLINE`/`MANUAL`) con etiqueta textual, nunca solo con color. Un turno `CANCELADO` MUST mostrarse tachado y con `canceladoPor` cuando esté disponible. Los turnos con inicio anterior o igual al "ahora" visual MUST verse atenuados y MUST NO ofrecer la acción Cancelar. La interfaz MUST NO mostrar tokens, hashes, `email_gestion`, identificador del odontólogo ni metadatos de auditoría.

#### Scenario: Turno online cancelado por el paciente

- **WHEN** la agenda devuelve un turno `CANCELADO` con `canceladoPor = PACIENTE` y origen `ONLINE`
- **THEN** la fila aparece tachada, con la etiqueta "Cancelado", "canceló el paciente" y la etiqueta "Online"

#### Scenario: Turno en curso

- **WHEN** el "ahora" visual cae dentro del intervalo de un turno `RESERVADO`
- **THEN** el turno se ve atenuado, se marca como en curso y no expone el botón Cancelar

#### Scenario: Datos sensibles del contrato

- **WHEN** una respuesta incluyera claves o valores de token, hash o email de gestión
- **THEN** la interfaz no los renderiza en la lista, el detalle, los diálogos ni los avisos de error

### Requirement: Franjas, huecos, bloqueos y línea "ahora"

La interfaz MUST combinar los turnos con los horarios y bloqueos del odontólogo para mostrar las franjas de atención agrupadas por período, los huecos "libre" entre eventos dentro de una franja y el texto "fuera del horario de atención" entre franjas. Los huecos MUST NO presentarse como horarios reservables. Los bloqueos MUST visualizarse con su `motivo` y con sus `turnosAfectados` marcados, sin cancelarlos automáticamente. Cuando el rango visible incluye la fecha actual, la interfaz MUST dibujar la línea "ahora" en la posición horaria correspondiente.

#### Scenario: Día con franjas, bloqueo y huecos

- **WHEN** el odontólogo atiende 09:00–13:00 y 16:00–19:00, hay un bloqueo 11:00–12:00 con un turno adentro y no hay turnos a las 10:15
- **THEN** la vista muestra ambas franjas, el hueco "libre", el bloqueo con su aviso, el turno afectado marcado y "13:00 – 16:00 · fuera del horario de atención"

#### Scenario: Semana con turnos dentro de bloqueos

- **WHEN** dos turnos de la semana se superponen con bloqueos
- **THEN** la grilla los marca con el borde de aviso y muestra un resumen que invita a revisarlos o cancelarlos, sin modificar su estado

### Requirement: Detalle del turno

Al elegir un turno, la interfaz MUST abrir su detalle: panel fijo en la vista diaria de escritorio, hoja desde abajo en celular y cajón a la derecha en la vista semanal de escritorio. El detalle MUST mostrar nombre y apellido, DNI, teléfono y email del paciente, nombre y duración de la prestación, horario local, estado, origen y `canceladoPor`/`canceladoEn` cuando corresponda, usando solo campos del contrato de C-12. Si el contrato no expone alguno de esos campos, la fila correspondiente MUST omitirse sin cambiar la estructura visual. El detalle MUST NO exponer secretos de gestión.

#### Scenario: Detalle con todos los campos contratados

- **WHEN** se toca un turno `RESERVADO` de origen `ONLINE` desde la vista diaria en escritorio
- **THEN** se abre el panel fijo con paciente, DNI, teléfono, email, prestación y duración, horario, estado y origen

#### Scenario: Contrato con menos datos del paciente

- **WHEN** el DTO del turno no trae teléfono del paciente
- **THEN** no se muestra esa fila y el resto del panel conserva su estructura y acciones

### Requirement: Estados de carga, vacío y error de la agenda

Mientras se cargan los datos, la interfaz MUST mostrar un estado de carga. Si no hay horarios cargados, MUST mostrar el estado "Todavía no cargaste tus horarios" con acceso a la configuración. Si hay franjas pero no hay turnos en el rango, MUST mostrar "No tenés turnos este día" (o el equivalente semanal). Si el día no tiene franjas, MUST mostrar que no se atiende ese día. Ante fallo de red, 500 `ERROR_INTERNO` o cualquier respuesta inesperada del rango, MUST mostrar "No pudimos cargar la agenda" con una acción Reintentar; los 400 del contrato nunca deben aparecer con rangos generados por la interfaz y, si llegaran, MUST tratarse como ese estado de error.

#### Scenario: Sin horarios cargados

- **WHEN** `GET /api/horarios` devuelve una lista vacía
- **THEN** la agenda muestra el estado "Todavía no cargaste tus horarios" con un enlace a `/config`

#### Scenario: Fallo al cargar el rango

- **WHEN** `GET /api/turnos` falla por red o responde 500 `ERROR_INTERNO`
- **THEN** se muestra el aviso "No pudimos cargar la agenda", el botón Reintentar y no se dibuja una agenda parcial
