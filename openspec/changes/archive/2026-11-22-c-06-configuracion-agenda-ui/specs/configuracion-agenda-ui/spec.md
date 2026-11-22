# Spec Delta — configuracion-agenda-ui

## Purpose

Define el comportamiento observable de las pantallas con las que el odontólogo configura su horario semanal, sus prestaciones y sus bloqueos, incluidos los estados de carga, los errores y la información que se muestra antes de confirmar cada cambio.

## ADDED Requirements

### Requirement: Acceso y carga inicial de la configuración

La ruta protegida `/config` DEBE (MUST) mostrar tres secciones navegables — Horarios, Prestaciones y Bloqueos — dentro del layout del odontólogo, con la sección activa marcada para lectores de pantalla. Al entrar, cada sección DEBE (MUST) pedir solo su endpoint de lectura (`GET /api/horarios`, `GET /api/prestaciones` o `GET /api/bloqueos`) y mostrar un estado de carga mientras espera. Una falla de red DEBE (MUST) mostrar un mensaje de reintento sin descartar los datos ya cargados de las otras secciones.

#### Scenario: Ingreso a la configuración con sesión válida
- **GIVEN** un odontólogo con sesión iniciada
- **WHEN** abre `/config`
- **THEN** se muestran las tres secciones dentro del layout, sin el marcador genérico de C-04, y se piden los tres endpoints de lectura de configuración

#### Scenario: Falla de red en una sección
- **GIVEN** que `GET /api/prestaciones` falla sin respuesta HTTP
- **WHEN** se carga `/config`
- **THEN** esa sección muestra "Revisá tu conexión e intentá de nuevo." con una acción de reintento, y horarios y bloqueos conservan su propio estado

#### Scenario: Sesión vencida
- **GIVEN** que `GET /api/horarios` responde 401 `NO_AUTENTICADO`
- **WHEN** se carga `/config`
- **THEN** el cliente de API existente notifica la sesión vencida y la aplicación redirige a `/login?redirect=%2Fconfig`, sin mostrar un error de horarios como si fuera un dato inválido

### Requirement: Horario semanal y reemplazo atómico desde la UI

La sección Horarios DEBE (MUST) presentar los siete días de la semana ISO (1 = lunes) con cero, una o varias franjas por día en formato `HH:mm`. El guardado DEBE (MUST) enviar un único `PUT /api/horarios` con el objeto `{ "franjas": [...] }` que representa el horario completo, sin ids ni campo de odontólogo, y DEBE (MUST) reemplazar el borrador local con la respuesta del servidor solo cuando la operación termina con éxito.

#### Scenario: Carga de varias franjas por día
- **GIVEN** que `GET /api/horarios` devuelve dos franjas el lunes (09:00–13:00 y 16:00–20:00) y ninguna el domingo
- **WHEN** se muestra la sección Horarios
- **THEN** el lunes muestra ambas franjas en ese orden y el domingo se muestra sin franjas con la acción de agregar una

#### Scenario: Guardado del horario completo
- **GIVEN** un borrador con dos franjas el martes y ninguna el jueves
- **WHEN** se activa "Guardar horario"
- **THEN** se envía un `PUT /api/horarios` con exactamente esas dos franjas y, ante un 200, se muestra "Cambios guardados" y se refresca el borrador desde la respuesta

#### Scenario: Rechazo del reemplazo
- **GIVEN** que el servidor rechaza el `PUT /api/horarios` con 422 `RN-AG-01`
- **WHEN** llega la respuesta
- **THEN** se muestra el mensaje del servidor en un `role="alert"`, el borrador con los cambios del odontólogo sigue editable y no se afirma que el horario se guardó

### Requirement: Validación en cliente antes de pedir

La UI DEBE (MUST) rechazar localmente, sin emitir el pedido correspondiente, toda franja con día fuera de 1–7, hora ausente o con formato distinto de `HH:mm`, inicio igual o posterior al fin, o dos franjas del mismo día que se solapen (RN-AG-01); toda prestación con duración no múltiplo positiva de 15 (RN-AG-03); y todo bloqueo con inicio igual o posterior al fin. Cada rechazo DEBE (MUST) marcar el campo o la franja involucrada con `aria-invalid`, mostrar un mensaje en español rioplatense con voseo y llevar el foco al primer control inválido. Las franjas contiguas y las franjas iguales en días distintos DEBE (MUST) aceptarse.

#### Scenario: Franja invertida
- **GIVEN** una franja del lunes de 13:00 a 09:00
- **WHEN** se intenta guardar el horario
- **THEN** no se envía el `PUT`, la franja queda marcada como inválida con `aria-invalid="true"` y se muestra "La hora de inicio debe ser anterior a la de fin."

#### Scenario: Franjas solapadas
- **GIVEN** dos franjas del miércoles de 09:00–11:00 y de 10:00–12:00
- **WHEN** se intenta guardar el horario
- **THEN** no se envía el `PUT`, ambas franjas se marcan como inválidas y el mensaje explica que las franjas del mismo día no pueden solaparse

#### Scenario: Franjas contiguas y días distintos
- **GIVEN** franjas de 09:00–11:00 y 11:00–13:00 el mismo día, y la misma hora en días distintos
- **WHEN** se valida el horario
- **THEN** la validación pasa y el guardado envía el `PUT` con todas esas franjas

#### Scenario: Duración no múltiplo de 15
- **GIVEN** una prestación con duración 20 minutos
- **WHEN** se intenta crear o editar la prestación
- **THEN** no se envía el pedido, el campo de duración queda inválido y se muestra "La duración tiene que ser un múltiplo de 15 minutos."

#### Scenario: Rango de bloqueo invertido
- **GIVEN** un bloqueo con fin igual o anterior al inicio
- **WHEN** se intenta crear el bloqueo
- **THEN** no se envía el `POST`, el campo `fin` queda inválido y se muestra "La hora de fin debe ser posterior a la de inicio."

### Requirement: Gestión de prestaciones con baja lógica

La sección Prestaciones DEBE (MUST) listar las prestaciones propias activas e inactivas con nombre, duración y estado, ofrecer alta, edición y baja, y enviar `POST /api/prestaciones`, `PUT /api/prestaciones/{id}` o `DELETE /api/prestaciones/{id}` según corresponda. El cuerpo de alta y edición DEBE (MUST) contener solo `nombre` y `duracionMinutos`. La baja DEBE (MUST) pedir confirmación previa, mostrar a la prestación como "Dada de baja" después de un 204 y NO DEBE (MUST) ofrecer una acción para reactivarla. La UI DEBE (MUST) avisar que cambiar la duración no modifica los turnos ya reservados (RN-TU-02).

#### Scenario: Alta correcta
- **GIVEN** el nombre "Limpieza" y una duración de 45 minutos
- **WHEN** se confirma el alta
- **THEN** se envía `POST /api/prestaciones` con `{"nombre":"Limpieza","duracionMinutos":45}` y, con un 201, la prestación aparece en el listado como activa

#### Scenario: Edición de duración con turnos existentes
- **GIVEN** una prestación con turnos reservados que conservan su duración original
- **WHEN** se edita su duración y la API responde 200
- **THEN** el listado muestra la duración nueva y un aviso aclarando que los turnos existentes conservan la duración con la que se reservaron

#### Scenario: Baja lógica confirmada
- **GIVEN** una prestación activa y una confirmación aceptada
- **WHEN** se activa "Dar de baja"
- **THEN** se envía `DELETE /api/prestaciones/{id}`, y ante un 204 la fila sigue visible con el estado "Dada de baja" y sin opción de reactivarla

#### Scenario: Baja cancelada
- **GIVEN** el diálogo de confirmación de baja
- **WHEN** se elige "Volver"
- **THEN** no se envía ningún `DELETE` y la prestación sigue activa

### Requirement: Bloqueos y turnos afectados

La sección Bloqueos DEBE (MUST) permitir crear un bloqueo con fecha y hora de inicio y fin en `America/Argentina/Buenos_Aires`, enviando instantes con offset (por ejemplo `2026-11-27T10:00:00-03:00`) y un `motivo` opcional de hasta 255 caracteres. La respuesta del alta y cada elemento del listado DEBE (MUST) mostrar `turnosAfectados` con paciente, prestación, horario y estado de cada turno. La UI DEBE (MUST) ofrecer sobre cada turno afectado una acción visible "Cancelá el turno", sin ejecutarla en este change, y DEBE (MUST) aclarar que crear el bloqueo no cancela turnos (RN-AG-02).

#### Scenario: Alta de bloqueo sin turnos
- **GIVEN** un rango del 27/11 de 10:00 a 12:00 sin turnos activos
- **WHEN** se crea el bloqueo
- **THEN** se envía `POST /api/bloqueos` con inicio y fin con offset y, con un 201, el bloqueo aparece en el listado con "Sin turnos afectados"

#### Scenario: Bloqueo con turnos afectados
- **GIVEN** que el servidor responde 201 con un turno `RESERVADO` en `turnosAfectados`
- **WHEN** se muestra el resultado del alta
- **THEN** se lista ese turno con paciente, prestación, horario y estado, se muestra la acción "Cancelá el turno" y un aviso indicando que el bloqueo no lo canceló

#### Scenario: Acción de cancelar en este change
- **GIVEN** un turno listado en `turnosAfectados`
- **WHEN** se activa "Cancelá el turno"
- **THEN** la UI no envía ningún pedido de cancelación y muestra que esa acción se habilita con la gestión de turnos del odontólogo; el turno sigue figurando en el listado con su estado original

#### Scenario: Baja de bloqueo
- **GIVEN** un bloqueo propio en el listado
- **WHEN** se confirma su borrado
- **THEN** se envía `DELETE /api/bloqueos/{id}` y, con un 204, el bloqueo desaparece del listado sin modificar turnos

### Requirement: Errores del servidor visibles y seguros

Todo error devuelto por la API DEBE (MUST) presentarse usando el `mensaje` del cuerpo `{codigo, mensaje, detalles?}`, en un contenedor con rol `alert` para los rechazos. Cuando `detalles` identifica un campo (`franjas[i]`, `nombre`, `duracionMinutos`, `fin`, `inicio`), la UI DEBE (MUST) asociar el mensaje con ese control y llevarle el foco. Los códigos `RN-AG-01`, `RN-AG-03`, `VALIDACION`, `SOLICITUD_INVALIDA`, `NO_ENCONTRADO`, `NO_AUTENTICADO` y `ACCESO_DENEGADO` DEBE (MUST) manejar sin confundirlos con un error de validación local. El texto de la API DEBE (MUST) insertarse como texto plano, nunca como HTML.

#### Scenario: Rechazo por regla de negocio con índice de franja
- **GIVEN** una respuesta 422 `RN-AG-01` con `detalles` en `franjas[1]`
- **WHEN** se procesa la respuesta
- **THEN** se muestra el `mensaje` devuelto, se marca esa franja como inválida y se envía el foco a su control de inicio

#### Scenario: Duración rechazada por el servidor
- **GIVEN** una respuesta 422 `RN-AG-03` con `detalles` en `duracionMinutos`
- **WHEN** se procesa la respuesta
- **THEN** se muestra el mensaje del servidor junto al campo de duración, aunque la validación local la haya considerado válida

#### Scenario: Recurso ajeno o inexistente
- **GIVEN** una respuesta 404 `NO_ENCONTRADO` al editar una prestación
- **WHEN** se procesa la respuesta
- **THEN** se muestra un mensaje que no revela si el recurso pertenece a otro odontólogo, sin SQL ni nombres de restricciones

#### Scenario: Mensaje con marcas HTML
- **GIVEN** que la API devuelve un `mensaje` que contiene `<script>algo</script>`
- **WHEN** se muestra el error
- **THEN** el texto aparece literalmente en pantalla y no se interpreta ni ejecuta como marcado

### Requirement: Aislamiento por odontólogo de sesión

La UI NO DEBE (MUST) construir, enviar ni conservar un `odontologoId` propio: ningún cuerpo ni query string de los pedidos de configuración DEBE (MUST) incluirlo, y la identidad del odontólogo DEBE (MUST) resolverse únicamente con la sesión existente del `api-client`. Ante un 404 de un recurso ajeno, la UI DEBE (MUST) mostrar el mismo mensaje que para uno inexistente (RN-SE-01).

#### Scenario: Cuerpos sin identificador de odontólogo
- **GIVEN** cualquier alta, edición, baja o reemplazo de configuración
- **WHEN** se inspeccionan el cuerpo JSON y la query string del pedido
- **THEN** ninguno contiene `odontologoId` ni otro identificador de dueño, y la petición se envía con `credentials: 'same-origin'` y el header `X-XSRF-TOKEN` en escrituras

#### Scenario: Recurso de otro odontólogo
- **GIVEN** un id de prestación o bloqueo que pertenece a otra cuenta
- **WHEN** se intenta editar o borrar desde la UI
- **THEN** se muestra el mismo mensaje de "No encontramos ese recurso" que para un id inexistente y no se expone el dato ajeno

### Requirement: Presentación adaptable y accesible

Las tres secciones DEBE (MUST) ser utilizables en un viewport de 360 px de ancho y en escritorio, sin perder etiquetas, mensajes de error ni acciones; los controles interactivos DEBE (MUST) conservar el objetivo táctil mínimo definido por el design system. La UI DEBE (MUST) usar exclusivamente los tokens de `design-system/tokens.css` para colores, tipografía, espaciados, radios y sombras, y DEBE (MUST) respetar literalmente los textos visibles, el orden de la información y los estados de los mockups aprobados de este change.

#### Scenario: Uso en celular
- **GIVEN** un viewport de 360 px de ancho
- **WHEN** se abre cualquiera de las tres secciones
- **THEN** no hay desbordamiento horizontal de la página, las franjas y los turnos afectados siguen legibles y todas las acciones siguen alcanzables

#### Scenario: Estados visuales acordados
- **GIVEN** el mockup aprobado de una sección y viewport
- **WHEN** se verifica la pantalla real durante el cierre del change
- **THEN** coinciden los textos visibles, los estados representados y el orden de los bloques, y solo se aceptan diferencias documentadas y aprobadas

#### Scenario: Sin valores visuales hardcoded
- **GIVEN** el código de estilos y plantillas de las tres secciones
- **WHEN** se revisa estáticamente
- **THEN** no aparecen colores, fuentes, espaciados ni radios literales: todos los valores visuales salen de variables de `design-system/tokens.css`
