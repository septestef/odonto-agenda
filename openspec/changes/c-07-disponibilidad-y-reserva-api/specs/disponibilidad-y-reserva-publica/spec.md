# Especificación delta: disponibilidad y reserva pública

> Contrato propuesto para ratificación humana; los requisitos describen el comportamiento objetivo, no el estado del sistema.

## Purpose

Permitir que un paciente consulte horarios libres y reserve sin cuenta, sin exponer datos de otros pacientes y sin admitir sobreturnos.

## ADDED Requirements

### Requirement: Perfil y prestaciones públicas por slug

`GET /api/public/{slug}` MUST responder 200 en JSON con `{slug, nombre, apellido, direccionConsultorio, prestaciones}` de un odontólogo activo. Los cuatro primeros campos son strings salvo `direccionConsultorio`, que admite `null`. `prestaciones` es un arreglo (puede estar vacío) de `{id, nombre, duracionMinutos}`; `id` y `duracionMinutos` son enteros JSON y `nombre` es string. Solo incluye prestaciones activas del odontólogo; el orden propuesto es por `id` ascendente. Ninguna de las tres rutas requiere cuenta ni sesión. El ámbito de todo acceso MUST resolverse desde el `slug`, nunca desde un identificador de odontólogo enviado en el cuerpo.

#### Scenario: Perfil con prestaciones de distintos estados y propietarios
- **WHEN** se consulta el perfil de un odontólogo activo que tiene prestaciones activas e inactivas y existen prestaciones de otro odontólogo
- **THEN** la respuesta incluye solo sus prestaciones activas y no expone el ID interno, las credenciales ni el email de login del odontólogo, ni datos de pacientes.

#### Scenario: Recurso no visible
- **WHEN** una ruta pública recibe un slug inexistente/inactivo o se pide una prestación inexistente, ajena o inactiva para un slug activo, con entrada válida
- **THEN** devuelve 404 `NO_ENCONTRADO` sin distinguir esas causas.

### Requirement: Consulta de inicios libres para un día local

`GET /api/public/{slug}/disponibilidad` MUST aceptar `prestacionId` entero positivo y `fecha` calendario ISO `YYYY-MM-DD`, ambos obligatorios. MUST devolver 200 JSON `{fecha, inicios}` con la fecha consultada y un arreglo de strings de inicio sin duplicados y ordenados cronológicamente. `inicio` en POST y los inicios y fines de las respuestas usan el formato exacto `YYYY-MM-DDTHH:mm:ss±HH:mm` con offset numérico válido para ese día y hora en `APP_TIMEZONE` (por defecto Buenos Aires; p. ej. `2026-11-26T09:00:00-03:00`). No se devuelve información de turnos, bloqueos ni pacientes. Un día válido sin slots (incluido fuera de ventana) MUST devolver `inicios: []`.

Para calcular los slots, el sistema MUST partir de la apertura de cada franja del día local y avanzar en pasos de 15 minutos (RN-AG-05). El turno completo MUST entrar en una sola franja, inclusive si `fin` coincide con su cierre. Con un único `ahora` del reloj UTC del servidor por operación, el inicio MUST ser `>= ahora + 24 h` exactas y su fecha local MUST ser `<= hoyLocal + APP_RESERVA_MAX_DIAS` (60 por defecto, con todo el último día local incluido). Los intervalos `[inicio, fin)` MUST excluir bloqueos y turnos `RESERVADO`, `ATENDIDO` y `AUSENTE` del mismo odontólogo, incluso si empezaron antes del día consultado. `CANCELADO` no ocupa y tocar un extremo no es solaparse.

#### Scenario: Grilla anclada y fin exacto
- **WHEN** una franja abre a las 09:05, cierra a las 09:50, la prestación dura 30 minutos y no hay ocupación ni restricciones temporales
- **THEN** se ofrecen 09:05 y 09:20; no 09:15 ni 09:35.

#### Scenario: Bordes de anticipación y horizonte
- **WHEN** se consultan días con candidatos en la marca exacta de 24 h, justo antes, en el último día local habilitado o en el día siguiente
- **THEN** son elegibles los inicios en la marca exacta y el último día local; se excluyen los anteriores a 24 h y los del día siguiente.

#### Scenario: Intervalos que se cruzan o solo se tocan
- **WHEN** un candidato se cruza con un bloqueo o un turno ocupante, solo toca su extremo, o únicamente se cruza con un turno cancelado
- **THEN** se descarta solo si existe un cruce efectivo con un intervalo ocupante o bloqueo del mismo odontólogo.

### Requirement: Reserva online y validación de la entrada

`POST /api/public/{slug}/turnos` MUST aceptar un objeto JSON con `prestacionId` (entero positivo), `inicio` (string en el formato de la consulta, con offset de negocio), `dni` (string de 7 u 8 dígitos ASCII), `nombre` y `apellido` (strings no blancos, hasta 100 caracteres cada uno), `telefono` (string E.164: `+`, primer dígito distinto de cero y hasta 15 dígitos en total) y `email` (string con formato de email, hasta 255 caracteres). Todos son obligatorios. MUST verificar de nuevo prestación activa, ventana temporal, franja, duración y bloqueos, sin exigir un GET previo. Un turno existente no sustituye la decisión de la base sobre el solapamiento del POST.

Si confirma, MUST responder 201 en JSON con exactamente `{turnoId, prestacionId, inicio, fin, estado}`: ambos IDs son enteros JSON, los instantes se presentan con offset de negocio y `estado` es `RESERVADO`. El turno se persiste `RESERVADO/ONLINE` con instantes UTC (`timestamptz`) y duración fijada según la prestación en ese momento (RN-TU-02). El token no aparece en el cuerpo, cabeceras, URL ni logs; en persistencia solo se conserva su hash SHA-256, no el token original (RN-SE-02).

#### Scenario: Reserva válida sin consulta previa
- **WHEN** se envía una reserva válida y se confirma la transacción
- **THEN** se devuelve 201 con el resumen del turno y sin credenciales ni datos personales, y cambiar después la duración de la prestación no cambia el fin guardado.

#### Scenario: Error de formato o de regla
- **WHEN** se reciben campos del POST inválidos, un JSON ilegible, parámetros de consulta ausentes/no interpretables, o un inicio interpretable que viola una regla de agenda
- **THEN** se responde en el formato `errores-api`: 400 `VALIDACION` con `detalles: [{campo, mensaje}, ...]` para campos inválidos del POST y parámetros interpretados que no validan (p. ej. `prestacionId` no positivo); 400 `SOLICITUD_INVALIDA` para cuerpo ilegible/no deserializable o query ausente/no convertible; 422 con código de negocio para un inicio válido que infringe una regla.

#### Scenario: Código de rechazo de un inicio inválido para el negocio
- **WHEN** el inicio ya pasó, no alcanza las 24 h, supera el horizonte, o no respeta la grilla, la franja completa o los bloqueos
- **THEN** se devuelve 422 con el primer código aplicable en ese orden: `RN-TU-07`, `RN-TU-03`, `RN-TU-11`, `RN-AG-05` para grilla/franja o `RN-AG-02` para bloqueo. Un offset que no corresponde a la fecha y hora local se rechaza como campo `inicio` inválido (400 `VALIDACION`), no como error de negocio.

### Requirement: Atomicidad, límite por DNI y concurrencia

La reserva MUST crear o actualizar nombre, apellido, teléfono y email del paciente identificado por `(odontologo_id, dni)` y crear el turno dentro de una transacción. El conteo MUST impedir más de dos turnos con `inicio > ahora` y estado `RESERVADO` para ese paciente y odontólogo, aun con solicitudes simultáneas en horarios distintos (RN-PA-03). Con cupo agotado, MUST devolver 422 `RN-PA-03`. Cualquier rechazo MUST dejar intactos el paciente y los turnos previos y no dejar paciente ni turno parcial.

El 409 `RN-TU-01` MUST corresponder únicamente a la violación de `turno_sin_solapamiento` para intervalos `[inicio, fin)` del mismo odontólogo y estados ocupantes. MUST devolver `{ "codigo": "RN-TU-01", "mensaje": "Ese horario acaba de ser tomado, elegí otro" }` sin detalles internos. El sistema MUST NOT mapear la violación de otra restricción a ese conflicto.

#### Scenario: Dos DNIs compiten por el mismo horario
- **WHEN** dos solicitudes válidas con DNIs distintos intentan reservar el mismo horario del mismo odontólogo de forma concurrente en transacciones separadas
- **THEN** exactamente una obtiene 201 y la otra 409 `RN-TU-01`; queda una sola fila de turno para ese horario y no persiste ningún cambio de contacto de la solicitud perdedora.

#### Scenario: Dos horarios distintos compiten por el último cupo del mismo DNI
- **WHEN** un DNI con un futuro `RESERVADO` intenta reservar dos horarios válidos y no solapados del mismo odontólogo al mismo tiempo
- **THEN** exactamente una solicitud obtiene 201 y la otra 422 `RN-PA-03`; quedan dos futuros `RESERVADO` para el paciente, no tres, y solo se guardan los datos de la solicitud ganadora.

#### Scenario: Aislamiento entre odontólogos y estados que no cuentan
- **WHEN** ese mismo DNI reserva para otro odontólogo o existen turnos pasados, `CANCELADO`, `ATENDIDO` o `AUSENTE`
- **THEN** el cupo no incorpora esos turnos ni modifica al paciente de otro odontólogo; la ocupación de intervalos se evalúa por separado.

### Requirement: Resultado de reserva para consumidores internos

C-07 MUST publicar solo `TurnoReservado` como resultado de la reserva online, con entrega utilizable para efectos posteriores al commit. C-07 MUST NOT generar notificaciones ni publicar `TurnoCancelado` o `TurnoReprogramado`.

#### Scenario: Reserva rechazada o confirmada
- **WHEN** una reserva falla antes del commit o hace rollback
- **THEN** no se entrega un evento confirmado; una reserva confirmada tiene un único `TurnoReservado` asociado para consumo interno posterior al commit.
