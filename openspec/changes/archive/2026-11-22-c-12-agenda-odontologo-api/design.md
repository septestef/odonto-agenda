# Design

## Context

Motivación y alcance: `proposal.md`. Estado verificado el 2026-11-22 sobre `HEAD=origin/main=2130a3a`: C-03/C-05/C-07/C-08/C-09 integrados y archivados; C-12 sigue solo en planificación, sin controladores propios. Backend Spring Boot 4.1.2, Java 25, Gradle con JUnit Platform y tareas separadas `test`/`integrationTest`; integración PostgreSQL real `turnotarde_test`.

Evidencia relevante:
- `shared/seguridad/OdontologoActual.id()` devuelve `long` del principal `OdontologoAutenticado`; sin principal válido lanza 401 `NO_AUTENTICADO`. El rol lo exige `auth/ConfiguracionSeguridad` para `/api/**`. `AislamientoOdontologo.exigir(Optional<T>)` espera una consulta previamente filtrada y produce 404 genérico.
- La cadena usa CSRF SPA: cookie `XSRF-TOKEN`, header `X-XSRF-TOKEN`, GET sin token. No modificarla ni agregar autenticación nueva.
- `turno/Turno` guarda `Instant inicio/fin`, estado, origen y metadatos de cancelación; asociaciones paciente/prestación LAZY, sin `@Version`. C-09 agregó `cancelarPorPaciente`, `sucesor` y `emailGestion` no actualizable. Falta transición por odontólogo. `TurnoRepository` ya aporta `findByTokenHash`, `bloquearPorHash` (SQL nativo `SELECT * FROM turno WHERE token_hash = :hash FOR UPDATE`), intervalos, conteo y proyección C-05 de afectados, no un listado completo de agenda. `PacienteRepository` no tiene consultas propias.
- `V2__esquema_base.sql`: exclusión `[inicio,fin)` para `RESERVADO|ATENDIDO|AUSENTE`; `CANCELADO` deja de ocupar al commit. El trigger advisory solo corre cuando NEW es ocupante: **no serializa dos cancelaciones**.
- `ReservaService`: orden de escrituras paciente (upsert/lock) → turno/advisory. C-12 no debe tomar ni mutar paciente después de bloquear turno.
- `TurnoCancelado(ResumenTurno turno, CanceladoPor canceladoPor, Instant canceladoEn)` ya existe. C-08 consume AFTER_COMMIT con `fallbackExecution=false`, executor dedicado y registro separado REQUIRES_NEW; no tiene outbox, deduplicación ni reintentos durables.
- `ManejadorErrores`: faltantes/conversión → `SOLICITUD_INVALIDA`; validación semántica → `VALIDACION` con detalles; `ReglaNegocioException` preserva código/status/detalles. `ConflictoTurnos.esFallaDeLock` detecta 40P01/55P03 y `PessimisticLockingFailureException`; la guardia de reserva usa un mensaje impropio para cancelar.
- `openspec/specs/errores-api/spec.md` generaliza los locks a RN-TU-01 y no explicita la precedencia CSRF/sesión. Se conserva el mapeo de locks; el delta precisa filtros, confidencialidad y aplicación del error interno existente a C-12. Rechazos esperados de negocio requieren RN de KB; una inconsistencia interna del estado persistido no es un rechazo de negocio.
- Compatibilidad P4: `openspec/specs/errores-api/spec.md:19–24` exige RN para rechazos de negocio y `:56–61` exige 500 ERROR_INTERNO genérico con log para errores internos. `backend/src/main/java/ar/turnotarde/shared/error/ManejadorErrores.java:118–128` ya captura Exception y devuelve ese código con el mensaje genérico vigente; `:139–140` fija application/json. El log sanitizado de `:120–122` solo aplica a ControladorGestionTurno; C-12 cae en `:124`, que registra la excepción completa. Por eso P4 exige diagnóstico local sanitizado y excepción técnica de mensaje fijo sin datos ni causas con campos, sin tocar advice ni agregar infraestructura compartida.

Integración verificada (lectura de repositorio e historial; no ejecución de suites):
- C-05: implementación `56a17b0` y merge `4f38ffc`, archivo `152452f` incorporado por `18fdd9b`; `openspec/changes/archive/2026-11-22-c-05-configuracion-agenda-api/` y spec principal `configuracion-agenda` existen. `ServicioBloqueos.crear/aDto` guarda el bloqueo y proyecta afectados sin cancelar ni publicar; `TurnoRepository.turnosAfectados` filtra dueño, estados ocupantes e intersección semiabierta. No reutilizar ese DTO reducido como agenda completa. El archivo tasks registra 252 unitarios/251 IT; son resultados históricos, no verificación ejecutada en esta revisión. GATE 3 satisfecho junto con C-08.
- C-09: implementación `bf5de6a` y merge `308d33a`, archivo `c09cd5d` incorporado por `2130a3a`; `openspec/changes/archive/2026-11-22-c-09-gestion-turno-token-api/` y specs principales `gestion-turno-token`/`eventos-turno` existen y describen el snapshot estable. V9 agrega `email_gestion` nullable sin backfill; `Turno.sucesor` lo hereda. `GestionTurnoService.cancelar` líneas 60–70: bloquear por hash → reloj → reglas → transición PACIENTE → flush → leer odontólogo/email de gestión → evento. `BuzonCorreo.admisible` y `AvisoCorreo.candidato` descartan destinatarios ausentes/no admisibles; no recurren al email mutable.
- Diferencia comprobada entre contrato y código C-09: el spec de gestión exige RN-TU-01 para locks, pero `ControladorGestionTurno.cancelar` llama directo al servicio, sin `GuardiaSolapamiento`; `ManejadorErrores.errorDeBase` solo traduce la exclusión, no fallas de lock. No afirmar que ese camino ya traduce todo conflicto. C-12 envolverá su propia frontera transaccional con la guardia existente, sin corregir C-09 ni el advice compartido.

## Goals / Non-Goals

**Goals:** contratos JSON estables, consultas acotadas al dueño, fechas independientes de Windows/UTC, transición atómica e idempotente y una publicación por cambio efectivo, manteniendo garantías PostgreSQL y consumidor existente.

**Non-Goals:** cambios en seguridad, nuevas migraciones/infraestructura, serialización JPA, otro snapshot de contacto en tablas, entrega SMTP durable, nuevas acciones o filtros de UI. Se reutiliza el `email_gestion` ya integrado. C-09 conserva su alcance de token/reprogramación; C-05/C-13 aportan horarios/bloqueos fuera de estas respuestas.

## Decisions

**P1–P9 APROBADAS y apply autorizado explícitamente por el usuario el 2026-11-22**, según pedido humano al ejecutor C12. La autorización es separada de la revisión y no se infiere del CLI. Se conservan las decisiones completas de abajo y la resolución previa de P4.

Resoluciones aprobadas: P1 fechas locales y rango semiabierto de 1–31 días; P2 DTO exacto, duración real, privacidad y consulta sin N+1; P3 paginación propia y aislamiento; P4 CANCELADO → tiempo RN-TU-07 → inconsistencia futura ERROR_INTERNO → cancelar RESERVADO futuro; P5 lock solo de fila y reloj posterior, sin inversión de locks; P6 destinatario exclusivo email_gestion admisible y snapshot/evento compatibles; P7 errores exactos por advice existente; P8 RN-TU-01 compartido sin excepción nueva; P9 sesión/rol/CSRF heredados y su precedencia. No se inventan RN ni se extiende RN-SE-02 a sesión.

Checkpoint 3 implementado: cancelación/aviso, seguridad y concurrencia verificados junto con agenda/pacientes. Check final 299 unitarios/416 PostgreSQL IT, todos 0 failures/errors/skipped. Sin decisiones fuera de P1–P9. TDD-EVIDENCE.md registra ciclos reales y una desviación de trazabilidad: locks/guardia y algunos escenarios transaccionales se probaron después del primer GREEN del POST, sin RED independiente previo; esas tareas y 7.2 quedan abiertas para revisión del coordinador. Todo local en `feat/c-12-agenda-odontologo-api`, sin commit/push/PR/archive ni cambios en CHANGES.md, specs principales, firmas compartidas, advice o configuración de seguridad.

### P1 — Rango de agenda por fechas locales

`desde` y `hasta` obligatorios, `LocalDate` ISO `YYYY-MM-DD`, interpretados en `ZonaNegocio` (`America/Argentina/Buenos_Aires`). Convertir cada fecha a comienzo de su día zonal; usar `[desde,hasta)` e intersección `inicio < hastaInstant && fin > desdeInstant`. Entre 1 y 31 días calendario, calculados entre fechas, no como bloques de 24 horas. Histórico y futuro más allá de 60 días admitidos; 24 h/60 días son reglas de reserva, no de lectura.

Diaria: `?desde=2026-11-25&hasta=2026-11-26`. Semanal: `?desde=2026-11-23&hasta=2026-11-30`. Preferido a timestamps arbitrarios/hasta inclusivo por eliminar ambigüedad al cambiar de día; 31 días limita trabajo sin impedir vista semanal. Descartado reutilizar intervalos activos: ocultaría cancelados y datos de agenda.

### P2 — DTO de turnos y estrategia de lectura

200 `application/json`, array sin paginación; vacío `[]`. Todos los estados (`RESERVADO`, `CANCELADO`, `ATENDIDO`, `AUSENTE`) y orígenes (`ONLINE`, `MANUAL`); orden ascendente `inicio,id`. No se agregan filtros por estado/origen ni orden configurable.

Esquema exacto `TurnoAgendaRespuesta` (record propuesto):

| Campo | Tipo JSON / nulabilidad |
|---|---|
| id | integer int64 positivo |
| inicio, fin | string ISO offset-date-time, offset de la zona del negocio |
| estado | enum de estados, no nulo |
| origen | ONLINE o MANUAL, no nulo |
| paciente | objeto `PacienteRespuesta`, no nulo |
| prestacion | objeto `{id: int64 positivo, nombre: string, duracionMinutos: integer positivo}`, no nulo |
| canceladoPor | PACIENTE, ODONTOLOGO o null |
| canceladoEn | offset-date-time o null |

`PacienteRespuesta` contiene **solo** `id`, `dni`, `nombre`, `apellido`, `telefono`, `email`, todos no nulos; `dni` siempre string. Contactos/nombre de prestación vigentes al consultar. `duracionMinutos` se calcula de `fin-inicio` (duración real fijada, RN-TU-02), no del valor mutable de prestación. Los campos anulables se incluyen con `null` cuando corresponda.

```json
[
  {
    "id": 42,
    "inicio": "2026-11-25T09:00:00-03:00",
    "fin": "2026-11-25T09:30:00-03:00",
    "estado": "RESERVADO",
    "origen": "ONLINE",
    "paciente": {"id": 7, "dni": "12345678", "nombre": "Ana", "apellido": "Pérez", "telefono": "+5491100000000", "email": "ana@example.test"},
    "prestacion": {"id": 3, "nombre": "Consulta", "duracionMinutos": 30},
    "canceladoPor": null,
    "canceladoEn": null
  }
]
```

Servicios de consulta `readOnly` y proyección DTO plana con joins explícitos más mapeo a records anidados dentro de transacción; no navegación por cada fila ni N+1. Alternativa aceptable solo si mantiene contrato y número acotado de consultas: fetch explícito de asociaciones. No exponer `odontologoId`, token/hash (`token_hash`/`tokenHash`), `email_gestion`/`emailGestion`, auditoría, recordatorio ni entidades. La prohibición incluye valores y claves en agenda, detalles anidados, listados, pacientes, cancelación y errores; no agregar endpoint de detalle. `paciente.email` sigue siendo contacto mutable autorizado por sesión, nunca alias del destinatario reservado. Si ambos emails coinciden no se suprime ese contacto; probar también valores centinela distintos para detectar filtraciones del snapshot. Preferido a devolver entidades o `Page` de framework por privacidad y estabilidad.

### P3 — Pacientes con paginación propia

`GET /api/pacientes?page=0&size=20`; ambos opcionales, enteros int32, `page >= 0`, `1 <= size <= 100`. Orden fijo ascendente `apellido,nombre,id`, con collation de PostgreSQL (no prometer orden lingüístico español). Consulta y conteo filtrados por dueño. Todos los pacientes propios, incluso sin turnos futuros. No búsqueda DNI/apellido ni `sort` configurable; paginar es un detalle de lectura acotada, no una historia adicional de C-17.

200 `application/json`, wrapper record propio `PaginaPacientesRespuesta`: `contenido` array de `PacienteRespuesta`, `page` y `size` enteros efectivos, `totalElementos` int64 no negativo, `totalPaginas` entero no negativo = techo(total/size), 0 si vacío. Página fuera de rango → 200 con contenido vacío y totales reales. Offset calculado sin overflow (`long`); no serializar metadatos internos de Spring.

```json
{"contenido":[{"id":7,"dni":"12345678","nombre":"Ana","apellido":"Pérez","telefono":"+5491100000000","email":"ana@example.test"}],"page":0,"size":20,"totalElementos":1,"totalPaginas":1}
```

Alternativa lista ilimitada descartada por crecimiento; paginación por cursor descartada por complejidad innecesaria en demo. El orden es estable para un conjunto sin modificaciones concurrentes; no promete snapshot entre páginas/pedidos.

### P4 — Cancelación, prioridades e idempotencia

`POST /api/turnos/{id}/cancelar` no define body ni requiere Content-Type. `id` long positivo. La respuesta exitosa es **204 sin cuerpo**, sin DTO ni token; no esperar SMTP.

Después de filtros y validación de id, consultar **id + dueño de sesión** con lock. Recurso inexistente/ajeno → 404 antes de mirar estado o tiempo. Dentro del lock:

| Estado | Resultado / precedencia |
|---|---|
| CANCELADO | 204 incluso si pasó; no cambiar autor/fecha originales ni publicar otro evento |
| Cualquier otro estado con inicio <= ahora | 409 `RN-TU-07`, incluidos ATENDIDO/AUSENTE históricos y la igualdad; sin cambios ni evento |
| ATENDIDO / AUSENTE con inicio > ahora | Inconsistencia interna persistida: 500 `ERROR_INTERNO` genérico mediante fallback existente; diagnóstico sanitizado, sin cambios, flush de actualización, evento ni aviso |
| RESERVADO con inicio > ahora | CANCELADO, canceladoPor ODONTOLOGO, canceladoEn ahora; un evento y 204 tras commit |

No hay anticipación mínima de 24 h para cancelar. La repetición sobre cancelado no ejecuta otra cancelación: justifica permitirla aun pasada y conservar cancelación previa por PACIENTE. Alternativa 409 al repetir descartada porque dificulta reintentos HTTP tras respuesta perdida. No inventar RN ni reutilizar RN-TU-10 como rechazo de cancelación.

**Resolución contractual P4 autorizada:** tras sesión/dueño/lock y un único reloj posterior al lock, aplicar estrictamente las filas de la tabla en orden. El rechazo esperado por tiempo usa RN-TU-07, con igualdad como en `openspec/specs/gestion-turno-token/spec.md:110–120`; la excepción idempotente CANCELADO es propia de C-12. KB `05_reglas_de_negocio.md:29–32` distingue prohibición temporal, cancelación futura y marcado de asistencia solo después del inicio: ATENDIDO/AUSENTE futuros no son alcanzables por el flujo normal y se tratan como inconsistencia interna persistida, no como una regla de negocio que se disfraza de error técnico. No extender RN-SE-02 de token ni usar RN-TU-10 como código de rechazo. No reparar, normalizar ni cancelar automáticamente esa fila.

Para esa inconsistencia, antes de cualquier mutación/flush de actualización, lectura de contactos o construcción/publicación del evento, registrar localmente un marcador fijo de diagnóstico (por ejemplo `CANCELACION_ODONTOLOGO_ESTADO_INCONSISTENTE`) y solo el enum de estado permitido. No incluir ids, datos personales, emails, tokens/hashes, entidad ni excepción con campos. Propagar una excepción técnica estándar de mensaje fijo y sin causa ni valores persistidos al fallback existente; su registro completo por el advice contendrá solo ese mensaje fijo y traza técnica sin campos de negocio. La respuesta exacta es 500 application/json `{"codigo":"ERROR_INTERNO","mensaje":"Ocurrió un error inesperado. Probá de nuevo en unos minutos."}`, sin detalles internos. Estado y todos los metadatos quedan intactos, sin publicación, evento, aviso, SMTP ni auditoría de notificación. No se agrega logging compartido ni se modifica el advice. La triangulación futura cubre ambos estados futuros, ambos históricos y en igualdad con RN-TU-07, y CANCELADO pasado con 204.

### P5 — Reutilizar exclusión de C-09 y reloj posterior al lock

Orden para C-12, adaptando exactamente la cancelación integrada de C-09:
1. Superar filtros de sesión/rol/CSRF, validar id y obtener dueño exclusivamente de `OdontologoActual.id()`.
2. Invocar servicio público proxificado desde una frontera sin transacción; dentro de una única tx obtener **solo la fila Turno**, `WHERE id = :id AND odontologo_id = :dueno FOR UPDATE`, equivalente al lock de escritura de `bloquearPorHash`. No autorizar con token ni consultar primero por id sin dueño. No joins fetch bajo lock, locks sobre asociaciones, lectura previa de entidad ni reutilización de estado cacheado: la primera materialización de ese turno en esta tx debe ser la lectura bloqueante. Ausente/ajeno → mismo 404, sin mirar estado/tiempo.
3. Una vez adquirido el lock y observado el estado vigente, capturar un único `Instant ahora` con `ZonaNegocio.ahora()` (delega en Clock inyectado). Revalidar P4 bajo lock y usar ese mismo instante para tiempo y `canceladoEn`; no leer el reloj antes de la espera ni otra vez para decidir. La idempotencia C-12 es propia: no llamar ciegamente a `exigirOriginalEditable`, cuya precedencia y RN-SE-02 pertenecen a C-09.
4. Para transición efectiva, marcar CANCELADO/ODONTOLOGO, hacer flush **antes** de construir/publicar el aviso, leer asociaciones y odontólogo sin locks explícitos y tomar el email de gestión de la fila bloqueada. Construir P6, publicar una vez dentro de tx y confirmar antes de devolver 204. Para CANCELADO previo, 204 sin mutar/flush de actualización ni evento.

Compatibilidad y ausencia de inversión de locks: la cancelación C-09 y C-12 siguen `fila turno → transición a CANCELADO → flush → lecturas sin locks → evento`; **nunca** piden después lock/upsert de paciente ni advisory. Reprogramar C-09 sigue `proyección JDBC de ids sin entidad → paciente FOR NO KEY UPDATE → advisory del consultorio (namespace de V2) → fila original FOR UPDATE → revalidación de vínculos/estado y reloj → flush del original → INSERT sucesor → evento`. C-07 sigue paciente/upsert → advisory al insertar. Aunque una reprogramación espere la fila que tiene C-12, C-12 no espera los locks de paciente/advisory que aquella retiene; las lecturas ordinarias de odontólogo/prestación no agregan esa dependencia. V2 toma advisory solo si NEW es ocupante; CANCELADO no lo dispara. No extrapolar este protocolo a updates que mantienen estado ocupante (incluido C-19), que requieren advisory antes de fila, ni afirmar ausencia universal de deadlocks ajenos al flujo. El lock de fila no implica un snapshot global serializable de catálogo.

Dos cancelaciones con transacciones separadas: segunda espera, relee/revalida el estado bajo lock y devuelve 204 idempotente. Exactamente una transición/publicación en ejecución normal. La constraint sigue siendo la garantía de solapamiento; liberación visible a otras transacciones al commit, nunca prometerla antes.

Alternativas descartadas: advisory para cancelar (innecesario y tomarlo después de fila invierte el orden C-09), `@Version` (requiere migración y resolución de conflictos), mutex en memoria (no cubre procesos). Verificar SQL y locks reales con PostgreSQL en futuro apply, no solo mocks. Reutilizar `GuardiaSolapamiento.ejecutar` desde la frontera **fuera** de la tx, invocando el servicio transaccional en otro bean para cubrir también fallos al cerrar/confirmar. Conservar su RN-TU-01 conforme P8; nunca self-invocation, continuar una tx abortada ni reintentar automáticamente.

### P6 — Evento y snapshot compatibles con C-08

Publicar dentro de la tx tras flush, solo por transición efectiva: `TurnoCancelado(ResumenTurno, ODONTOLOGO, ahora)`. `ResumenTurno` conserva exactamente sus campos actuales: `turnoId, odontologoId, inicio, fin, prestacionId, prestacionNombre, duracionMinutos, pacienteNombre, pacienteApellido, pacienteEmail, pacienteTelefono, odontologoNombre, odontologoApellido, direccionConsultorio`; `duracionMinutos = Duration(fin-inicio)` en minutos, dirección puede ser null. Sin entidades, token ni hash. Se conserva la propuesta de nombre/apellido/teléfono y catálogo vigentes leídos al cancelar; la excepción es pacienteEmail, que debe usar el snapshot estable del turno. C-09 actualmente deja nombre/apellido/teléfono en null; C-12 no modifica su emisor ni la firma compartida.

El campo interno `ResumenTurno.pacienteEmail` lleva **exclusivamente** `turno.email_gestion` si `BuzonCorreo.admisible` lo acepta, o null en caso contrario, igual que C-09. No usar `paciente.email`, request, notificaciones ni otro turno como fallback; tampoco normalizar para forzar admisión. El destinatario ya estaba congelado al reservar (o heredado al reprogramar), no se vuelve a capturar al cancelar. Si paciente.email cambió de A a B, se avisa a A; los DTOs privados pueden mostrar B sin exponer A como email de gestión. Snapshot null o inválido → cancelar normalmente, publicar sin email utilizable; `AvisoCorreo.candidato` lo descarta sin SMTP **ni fila/intento de auditoría**. RN-SE-04 no rechaza esta cancelación: es exclusividad de reprogramar por token.

Leer asociaciones y odontólogo del dueño dentro de tx sin locks explícitos y construir un único snapshot; no leer paciente.email como destinatario ni prometer snapshot global serializable de contactos/catálogo. Las lecturas ordinarias no adquieren el lock de fila de paciente que podría invertir el protocolo de C-09. Reutilizar C-08 sin tocar firmas ni executor: AFTER_COMMIT, asunto «Cancelamos tu turno», sin link/token; registro independiente REQUIRES_NEW solo para avisos admitidos. SMTP fallido conserva CANCELADO y 204, y registra ERROR si el aviso fue admitido y la base permite registrarlo. Rollback posterior a publish → ninguna entrega AFTER_COMMIT, aviso ni fila.

Alternativa SMTP síncrono descartada por RN-NO-04; outbox descartada por alcance. No prometer entrega durable ni exactly-once SMTP: caída del proceso/rechazo de executor puede perder aviso, SMTP puede aceptar con resultado incierto, como en C-08.

### P7 — Errores de entrada exactos, advice existente

Todos los errores JSON `{codigo,mensaje,detalles?}`. `detalles` solo cuando aporta; para semántica, array de `{campo,mensaje}`. No RFC 7807. Validar antes de consultas/mutaciones. Ningún cuerpo de éxito o error expone `token_hash`/`tokenHash`, `email_gestion`/`emailGestion` ni sus valores reservados, tampoco por eco en mensaje/detalles o excepciones.

| Condición | HTTP / código | Mensaje y detalles |
|---|---|---|
| Falta desde/hasta; fecha inválida/vacía/no ISO; page/size/id no convertible o fuera de int32/int64 | 400 SOLICITUD_INVALIDA | `No pudimos interpretar la solicitud.`; sin detalles |
| hasta <= desde | 400 VALIDACION | mensaje común; detalle `hasta`: `Elegí una fecha posterior a desde.` |
| diferencia > 31 días | 400 VALIDACION | mensaje común; detalle `hasta`: `Elegí un rango de hasta 31 días.` |
| page < 0 | 400 VALIDACION | mensaje común; detalle `page`: `Ingresá una página mayor o igual a 0.` |
| size < 1 o > 100 | 400 VALIDACION | mensaje común; detalle `size`: `Elegí un tamaño entre 1 y 100.` |
| id <= 0 | 400 VALIDACION | mensaje común; detalle `id`: `Ingresá un identificador mayor a 0.` |
| id ajeno o inexistente | 404 NO_ENCONTRADO | `No encontramos lo que buscabas.` |
| no CANCELADO con inicio <= ahora, incluidos ATENDIDO/AUSENTE | 409 RN-TU-07 | `No podés cancelar un turno que ya empezó.` |
| ATENDIDO/AUSENTE con inicio > ahora | 500 ERROR_INTERNO | `Ocurrió un error inesperado. Probá de nuevo en unos minutos.`; sin detalles; inconsistencia interna según P4 |
| falla técnica de lock en cancelación o exclusión nombrada | 409 RN-TU-01 | `Ese horario acaba de ser tomado, elegí otro` (contrato compartido vigente) |
| otra falla inesperada | 500 ERROR_INTERNO | `Ocurrió un error inesperado. Probá de nuevo en unos minutos.` |

Mensaje común de validación: `Hay datos inválidos. Revisalos y probá de nuevo.` Conversión antes de semántica; para rango se evalúa orden antes de longitud. Si page y size son inválidos, incluir ambos detalles (no depender del orden del array). `page=` o `size=` explícitamente vacíos son solicitud inválida, no defaults. Defaults solo por ausencia; evitar normalización silenciosa de parámetros vacíos por el binding.

```json
{"codigo":"VALIDACION","mensaje":"Hay datos inválidos. Revisalos y probá de nuevo.","detalles":[{"campo":"hasta","mensaje":"Elegí una fecha posterior a desde."}]}
```

No usar 500 para rechazos esperados de negocio: reutilizar validación y `ReglaNegocioException`. La inconsistencia interna persistida de P4 usa el fallback 500 existente con excepción y diagnóstico sanitizados, sin editar advice. No se incorporan códigos técnicos nuevos para eludir una regla de negocio.

### P8 — Preservar el contrato general de lock

Se retira la excepción antes propuesta `CONFLICTO_CONCURRENTE`: `errores-api` principal y `gestion-turno-token` ya exigen `40P01`, `55P03` o fallo de lock traducido → 409 `RN-TU-01`, igual que la exclusión nombrada. Reutilizar guardia existente en la frontera de C-12, sin cambiar advice, C-07, C-09 ni el requisito compartido de locks. No reintento automático ni timeout/config nueva. Fallas no clasificadas → 500; ninguna respuesta incluye SQL/stack traces ni secretos. Un fallo revierte la transición y no entrega evento al consumidor. Dos POST sanos que solo esperan un lock no son conflicto: deben completar 204/204.

Trade-off: el mensaje heredado de horario tomado es poco preciso para cancelar. Se elige compatibilidad contractual y reuso de la traducción ya disponible por sobre agregar un código y modificar contratos compartidos. Una mejora transversal de esa semántica requiere otro alcance; no es prerrequisito de C-12 ni se exige en sus tareas.

### P9 — Seguridad heredada y coordinación

Reutilizar sesión/rol y utilidades C-03 sin cambios. GET sin sesión → 401 `NO_AUTENTICADO`, sin rol válido → 403 `ACCESO_DENEGADO`. POST autenticado sin CSRF o con CSRF inválido → 403. POST sin sesión con CSRF válido alcanza autorización → 401; sin sesión y sin CSRF puede ser rechazado antes con 403. Confirmar esta precedencia con cadena real en futura IT; no prometer 401 universal ni cambiar filtros para forzarlo. Mensajes heredados: 401 `Tu sesión no está iniciada o expiró. Volvé a ingresar.`; 403 `No pudimos verificar la solicitud. Recargá la página e intentá de nuevo.` (verificado en `ErrorApiAccessDeniedHandler`).

IDs de odontólogo en query/headers/body no alteran dueño; POST no toma datos de un body, no define campos de entrada allí. Testear IDs inyectados sin afirmar que la sesión cambia. Con entrada válida y filtros superados, dueño ajeno se resuelve 404 antes de estado/tiempo. C-09 ya está integrado: conservar sus reglas de token, revalidación y orden P5. En una carrera con gestión por token solo puede existir una transición efectiva sobre el original: si C-09 gana, C-12 responde 204 idempotente sobre ese original cancelado, sin tocar sucesor ni autor original; si C-12 gana, el perdedor C-09 aplica su RN-SE-02/RN-TU-07 según reloj y su contrato, no la idempotencia privada de C-12.

## Risks / Trade-offs

- [Estado persistido futuro incoherente] → P4 resuelta: 500 genérico existente y diagnóstico local sanitizado, sin reparación automática; verificar ambos estados y que el log completo del advice no reciba campos ni causas sensibles.
- [Contrato de locks amplio y diferencia con cancelación C-09] → preservar RN-TU-01 y envolver frontera propia; reportar diferencia observada sin extender cambios a C-09.
- [Escritores concurrentes ya integrados] → reutilizar P5 y probar C-12 contra cancelar/reprogramar de C-09; preservar precedencias/idempotencias distintas por credencial.
- [Listados/LAZY podrían generar N+1 o locks de asociaciones] → proyecciones para listas, lock de entidad única e IT de SQL/consultas; no serializar entidades.
- [Paciente mutable] → destinatario exclusivo del snapshot persistido email_gestion, fallback cerrado null/invalid; otros datos del snapshot conservan la propuesta de valores leídos al cancelar, sin prometer snapshot global, identidad verificada ni entrega.
- [31 días sin paginar turnos] → rango acotado y orden estable; revisar P1 si hay un volumen fuera del modelo de odontólogo independiente.
- [Correo best effort y respuestas perdidas] → idempotencia de transición; límites de C-08 documentados, sin promesa durable.

## Migration Plan

P1–P9 aprobadas y autorización separada de apply recibida: primero baseline, luego TDD por tarea con checkpoints, endpoints nuevos sin migración/config/dependencias, regresiones C-03/C-05/C-07/C-08/C-09 y validación de deltas. Si se revierte código tras despliegue, conservar cancelaciones y notificaciones ya persistidas; no reactivar turnos automáticamente. El PR funcional mantiene C-12 activo: no archiva, no sincroniza specs principales ni modifica CHANGES.md. Tras merge del PR funcional, preparar archivado/sync/roadmap en PR separado y autorizado; no incluirlos como condición para cerrar tareas funcionales.
