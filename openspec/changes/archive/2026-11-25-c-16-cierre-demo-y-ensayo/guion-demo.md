# Guion de demo — OdontoAgenda

**Miércoles 25/11/2026 · [localhost:8080](http://localhost:8080) · 13 min 30 s.** Horas del negocio en `America/Argentina/Buenos_Aires`. Los tiempos son de presentación, no de preparación. Este guion describe resultados esperados; no acredita un ensayo.

## Preparación y referencias de turnos

- Antes de presentar, acordá con Estefi la revisión que incluye A, el arranque con perfil `demo`, el buzón Gmail controlado y la base local descartable. El 25/11, V900 trae 09:00 Consulta y 09:30 Arreglo de caries (`RESERVADO`, `ONLINE`), y 11:00 Limpieza (`CANCELADO`, `ONLINE`, no ocupa el intervalo). V901 agrega cuatro turnos `RESERVADO`: 10:30 Consulta (`ONLINE`), 12:00 Limpieza (`MANUAL`), 16:30 Extracción (`ONLINE`) y 18:00 Arreglo de caries (`MANUAL`). El 26/11 V900 conserva un turno manual a las 17:00; el 27/11 queda libre en el seed. Conservá los turnos y sus prestaciones: el origen `ONLINE` de un dato del seed no significa que se haya reservado en vivo, y ni V900 ni V901 se usan para gestionar por enlace o probar correo.
- Usá dos contextos de navegador en la misma notebook: sesión del odontólogo y reserva/gestión del paciente sin sesión. Prepará fuera de la proyección la sesión de Gmail y la carga de credenciales. Usá nombres y DNIs sintéticos nuevos, distintos por alta y de los del seed, y solo el buzón de prueba controlado como destinatario. No envíes a las direcciones ficticias del seed. Para WhatsApp usá únicamente un teléfono de prueba controlado, en formato E.164; no uses un número real ajeno ni supongas que uno inventado es seguro. Nunca envíes el mensaje de WhatsApp.
- No leas ni muestres `.env`, credenciales, cookies, tokens ni enlaces completos de gestión. Abrí los enlaces recibidos en la misma notebook, fuera de proyección, y mostrale al público solo el contenido seguro de gestión, sin barra de dirección. Ocultá también destinatarios, teléfonos, URL de WhatsApp y vistas previas de enlaces. Si no podés separar la vista segura, no proyectes esa pantalla.
- Chequeá huecos con la hora efectiva del **servidor** al iniciar y antes de confirmar, tanto en el ensayo como en la demo: para altas online y el destino de una reprogramación, elegí el 26 o el 27 solo si el inicio sigue a **más de 24 h**, con margen para presentar. Si el 26 no cumple, probá el 27 con el mismo criterio. La regla admite `inicio >= ahora + 24 h`; el margen extra evita llegar al límite mientras hablás. Cada turno debe entrar completo en su franja, empezar en la grilla de 15 min y no pisar turnos ni bloqueos. No cambies el reloj ni las reglas.

| Referencia (solo para el guion) | Preparación y ciclo de vida | Hueco candidato, no verificado |
| --- | --- | --- |
| **P-C** | Reserva online nueva en el paso 3; su propio correo y enlace; cancelar como paciente en el paso 5. | 26/11 18:00, o 27/11 18:00. |
| **P-R → P-R2** | Prepará antes de presentar una segunda reserva online, con DNI distinto y enlace propio recibido en el buzón controlado. En el paso 5 movela a P-R2: original cancelado, nuevo reservado con enlace nuevo. No toques P-R2 en los pasos siguientes. | P-R: 26/11 18:30 o 27/11 18:30. P-R2: 27/11 16:00. |
| **O-C** | Prepará antes de presentar una tercera reserva online, con otro DNI sintético distinto de P-C y P-R y el mismo buzón controlado como email de gestión admisible. Debe seguir futura y reservada para que la cancele el odontólogo en el paso 7. | 26/11 19:00 o 27/11 19:00. |
| **W** | Prepará una cuarta reserva online con teléfono de prueba controlado y buzón controlado. Debe seguir futura y reservada hasta el paso 8. No la canceles ni la reprogrames. | 26/11 19:30 o 27/11 19:30. |
| **M** | Dejá un hueco libre para el alta manual del paso 9. No lo uses para el bloqueo, la reprogramación ni el caso concurrente del smoke. | 25/11 a las 11:00 o 16:00, solo si el inicio sigue siendo futuro y confirmás que todo el intervalo cabe en la franja sin solaparse con turnos activos ni bloqueos (para `Consulta`, 11:00–11:30 o 16:00–16:30, que termina justo cuando empieza la Extracción). Si no, probá 26/11 16:00 o 27/11 16:30 y volvé a verificar la duración completa. |

Los ejemplos usan `Consulta` (30 min); si cambiás prestación, recalculá el intervalo completo. Contrastá la disponibilidad y anotá las referencias finales sin datos privados. Si una reserva previa ya existe al repetir el recorrido, no la dupliques: comprobá estado y disponibilidad y replanificá las referencias consumidas. Si una hora candidata no sirve, elegí otro hueco del 26/27 que no esté asignado a otro paso. Si no hay un hueco elegible, declaralo bloqueado. La prueba de concurrencia se hace aparte del recorrido de presentación, con otro hueco y otros DNIs.

## Recorrido en vivo

### 1. Login — 00:00–00:50 · US-001

- **En pantalla:** en `/login`, ingresá como odontólogo de demo con la contraseña configurada localmente, fuera de proyección. Volvé a compartir cuando cargue `/agenda`.
- **Decí:** «Entro como odontóloga. Esta agenda es privada; el paciente no necesita una cuenta para reservar».
- **Esperado:** sesión iniciada y agenda privada visible.
- **Si falla:** «No pude iniciar sesión; la parte privada queda bloqueada». No encadenes intentos ni muestres la configuración. Si la reserva pública carga por separado, podés mostrarla sin dar por resuelto el login.

### 2. Configurá horarios, prestaciones y un bloqueo — 00:50–02:55 · US-002, US-003, US-004

- **En pantalla:** en `Configuración` (`/config`), mostrá las franjas de lunes a viernes, 09:00–13:00 y 16:00–20:00. Agregá una franja de ejemplo el sábado 28 (la configuración es semanal: por ejemplo, 10:00–11:00) sin tocar las franjas del 25–27; creá una prestación sintética de 15 min sin editar ni dar de baja las del seed; agregá fuera del recorrido un bloqueo que cubra esa franja el sábado 28. Confirmá que el rango está libre antes de bloquearlo, sin cancelar afectados. Si repetís, reutilizá los datos de demostración existentes en vez de duplicarlos.
- **Decí:** «Defino en qué franjas atiendo y cuánto dura cada prestación. Si no voy a atender un día, bloqueo ese horario: deja de ofrecerse sin cancelar los turnos que ya existían».
- **Esperado:** se guardan la franja semanal, la prestación y el bloqueo; el rango bloqueado no se ofrece en la página pública para esa fecha. Las franjas y huecos de los próximos pasos quedan intactos.
- **Si falla:** «Esta configuración no se guardó o no se reflejó en la disponibilidad. Para la reserva voy a usar un hueco que ya esté disponible». Si no hay uno válido, dejá pendiente la reserva.

### 3. Reserva online — 02:55–04:30 · US-005, US-006

- **En pantalla:** en la ventana del paciente, abrí `/reservar/demo`. Elegí `Consulta`, el día y el hueco de **P-C**, volvé a comprobar el margen y completá DNI, nombre, apellido, teléfono y email sintéticos/controlados. Confirmá una vez y mostrá la pantalla de éxito.
- **Decí:** «Sin crear una cuenta, elijo una prestación y veo solo los horarios que realmente están disponibles. Completo mis datos y confirmo. La agenda no permite que dos turnos se superpongan».
- **Esperado:** confirmación en pantalla; P-C queda `RESERVADO`, origen `ONLINE`. El intervalo deja de ofrecerse a otros pacientes.
- **Si falla:** «Ese horario no quedó confirmado». Si hay conflicto, refrescá y elegí otro hueco elegible no asignado; si la respuesta es incierta, comprobá la agenda antes de repetir el alta.

### 4. Recepción del email — 04:30–05:25 · US-007

- **En pantalla:** revisá bandeja y spam de Gmail fuera de proyección. Ubicá la confirmación de **P-C** y comprobá fecha, hora, prestación y dirección; abrí su enlace en la misma notebook y mostrale al público solo la parte segura de gestión.
- **Decí:** «La confirmación trae los datos del turno y un enlace para gestionarlo, sin iniciar sesión como paciente».
- **Esperado:** correo recibido en bandeja o spam; el enlace lleva a la gestión de P-C en localhost. La aceptación SMTP por sí sola no acredita entrega.
- **Si falla:** «La reserva se puede guardar aunque el correo no llegue. Voy a comprobar si quedó en agenda; la entrega y el acceso por enlace no se dieron por verificados». Si P-C persiste, no lo dupliques. Si Gmail falla, pasá al enlace genuino de **P-R** solo si ya llegó al buzón controlado y sigue válido; no recuperes tokens del seed ni sustituyas entrega por una captura o un mockup.

### 5. Cancelación y reprogramación desde gestión — 05:25–08:00 · US-008, US-009

- **En pantalla:** con el enlace de **P-C**, elegí cancelar y confirmá. Volvé a abrirlo, verificá solo lectura y comprobá que su hueco volvió a ofrecerse si aún cumple el margen. Después abrí el enlace distinto de **P-R**, elegí reprogramar y confirmá **P-R2** para la misma prestación. Verificá el estado del original y buscá el nuevo correo; abrí su nuevo enlace fuera de proyección. Revisá también si llegó el aviso de cancelación.
- **Decí:** «Cancelo el primer turno y libero el horario. Ahora muevo otro turno: si no se puede reservar el nuevo horario, el anterior se conserva. Si el cambio se confirma, recibo un enlace nuevo».
- **Esperado:** P-C cancelado y sin acciones; P-R cancelado y P-R2 reservado como una sola operación; nueva confirmación con enlace propio a P-R2, y aviso de cancelación para P-C. Para cancelar alcanza con que el turno aún no haya empezado; para el nuevo horario rige la anticipación online.
- **Si falla:** «Esta gestión no se completó o no pude comprobarla». Si falta el enlace de P-C, no lo canceles desde otro rol para simular el flujo; seguí con P-R si es posible. Si reprogramar falla, verificá que P-R siga reservado antes de reintentar. Si el cambio se guardó pero no llega el nuevo correo, mostrá solo el estado que puedas comprobar y registrá el bloqueo de entrega.

### 6. Agenda diaria y semanal — 08:00–09:25 · US-010

- **En pantalla:** en `/agenda`, elegí `Día` y `Hoy` o navegá al **25/11**. Mostrá las franjas, los turnos de la mañana y tarde y su estado/origen en el detalle. Pasá a `Semana`, recorré la semana que contiene el 25 y ubicá P-R2 si quedó confirmado; usá la navegación anterior/siguiente y volvé al rango.
- **Decí:** «Acá está la agenda del miércoles: puedo ver cada turno en detalle y pasar a la semana para organizarme. Distingo qué turnos entraron online y cuáles cargué yo».
- **Esperado:** el 25/11 muestra los seis turnos reservados y el cancelado de las 11:00 de V900/V901, según el filtro de estado; la semana permite ubicar los turnos del 26/27. Mostrá el origen en el detalle o en Día, sin prometer una insignia en cada tarjeta semanal.
- **Si falla:** «No pude cargar esta vista» o «La agenda no muestra todos los turnos esperados». Mostrá solo la vista que funcione; no describas datos no observados como cargados.

### 7. Cancelación por odontólogo — 09:25–10:30 · US-012

- **En pantalla:** en Día o Semana abrí **O-C** por su fecha y hora, verificá que siga reservado y futuro, elegí cancelar y confirmá. Revisá el nuevo estado y el aviso en el buzón controlado; verificá disponibilidad si el hueco aún cumple la regla online. No toques **W**, **M** ni un turno del seed.
- **Decí:** «Si no puedo atender un turno, también lo puedo cancelar desde la agenda. El horario se libera y el paciente recibe un aviso por correo».
- **Esperado:** O-C queda cancelado y deja de ocupar su intervalo. La cancelación no depende del correo: solo se notifica al email de gestión admisible de ese turno, y la entrega se acredita únicamente si llega al buzón controlado.
- **Si falla:** «No pude comprobar la cancelación» o «La cancelación quedó registrada, pero no pude confirmar el aviso». No sustituyas O-C por W ni des por enviado un correo no recibido.

### 8. WhatsApp — 10:30–11:35 · US-015

- **En pantalla:** abrí el detalle de **W**, futuro y reservado, con teléfono de prueba y dirección cargada. Elegí `WhatsApp` solo si está preparado el destino controlado y podés ocultar URL y teléfono. Mostrá la apertura y el texto precargado si está disponible, sin enviarlo ni contactar a un número real. Cerrá la pestaña de destino.
- **Decí:** «Puedo abrir WhatsApp con un mensaje que ya trae día, hora, prestación y dirección. Todavía no se envió nada: el envío es manual».
- **Esperado:** se prepara el texto y se abre una pestaña; si el navegador bloquea la apertura, aparece `Abrir WhatsApp` como alternativa. No se envía ningún mensaje.
- **Si falla:** «No pude abrir WhatsApp de forma segura; solo muestro la acción en la agenda». Si aparece la alternativa, usala únicamente bajo las mismas condiciones de seguridad. Marcá la apertura como pendiente o bloqueada, no como exitosa.

### 9. Creación de turno manual — 11:35–12:55 · US-011

- **En pantalla:** desde `/agenda`, elegí `Nuevo turno`, prestación y hueco **M**, completá los datos sintéticos y el buzón controlado y confirmá. Si hoy ya no hay huecos futuros, elegí el alternativo y navegá a su fecha en Día para ver el resultado. Revisá el correo de confirmación sin proyectar su enlace.
- **Decí:** «Si alguien llama, puedo cargarle el turno. Para un alta manual no tengo que esperar 24 horas: tiene que ser a futuro, dentro de mi agenda y sin pisar otro turno».
- **Esperado:** se ve el turno `RESERVADO` de origen `MANUAL`, con aviso de alta y confirmación por email si se entrega. Se conserva el rango visible al cerrar el formulario; si corresponde, navegá a la fecha elegida para verlo.
- **Si falla:** «Este turno no se pudo cargar» o «La carga quedó en agenda, pero no pude confirmar el correo». Si se ocupó el hueco, elegí otro válido sin tomar una referencia aún necesaria. No cambies el reloj.

### 10. Logout — 12:55–13:30 · US-001

- **En pantalla:** elegí `Cerrar sesión`. Volvé a intentar abrir `/agenda` en ese contexto sin iniciar sesión.
- **Decí:** «Cierro sesión. La agenda queda protegida; la página de reserva sigue siendo pública».
- **Esperado:** vuelve al login y no deja acceder a la agenda privada sin sesión.
- **Si falla:** «No pude verificar el cierre de sesión». Dejá de proyectar y cerrá el contexto de demo; cerrar la ventana no acredita que la sesión se invalidó.

## Cobertura y lo que no está

- **Must Have de la KB:** US-001 (1 y 10), US-002/003/004 (2), US-005/006 (3), US-007 (4), US-008/009 (5), US-010 (6) y US-012 (7). También US-015 (8) y US-011 (9). El guion recorre historias, no sustituye pruebas de todos sus criterios (por ejemplo, bloqueo de login, concurrencia o viewport de 360 px). Ver [plantilla de smoke](evidencias/B-smoke.md) y [plantilla visual](evidencias/B-visual.md).
- **No se muestra:** US-013, marcar atendido/ausente (C-19); US-014, recordatorio automático por email (diferido de C-14 a C-21; los correos transaccionales sí entran); historia clínica básica (C-17), odontograma (C-18) y preparación de producción (C-20). La demo es local, no un despliegue productivo ni una integración con WhatsApp Business.
