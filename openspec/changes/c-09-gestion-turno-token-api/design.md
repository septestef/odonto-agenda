# Diseño: C-09 - Gestión del turno por token

## Context

Ver [propuesta](proposal.md) y [delta de gestión](specs/gestion-turno-token/spec.md). C-07 ya genera y hashea el token (`TokenTurno`), valida agenda con `DisponibilidadService` y traduce conflictos de PostgreSQL con `GuardiaSolapamiento`. La reserva hace upsert y toma el lock de paciente antes de insertar; cambia `paciente.email` con cada reserva del mismo DNI. V2 ya tiene columnas de cancelación, vínculo al anterior y hash único; el `Turno` JPA usa `IDENTITY` y todavía no tiene transiciones de dominio.

C-08 ya consume los tres tipos de eventos inmutables. Para reprogramar usa `nuevo.pacienteEmail` sin releer al paciente; todavía no hay emisores de gestión y sus IT usan eventos de prueba. La auditoría de C-08 puede no existir, incluso si el destinatario era válido (rechazo de cola, fallo al registrar o caída). C-03 ya permite `/api/public/**` y lo excluye de CSRF; C-04 ya reconoce `/turno/{token}`, sin implementar la gestión.

## Goals / Non-Goals

- **Objetivos:** un solo criterio de agenda para selector y escritura, transiciones seguras para el mismo original y para el mismo destino, destinatario controlado por la credencial inicial y contratos estables para C-11.
- **No objetivos:** duplicar la regla de agenda o reusar `ReservaService.reservar()` para mover; cambiar autenticación, configurar agenda, modificar el consumidor o la entrega de C-08, ni implementar UI o recuperación del link.

## Decisions

**Governance ALTO — diseño general aprobado con condiciones.** Esta revisión no cierra las decisiones pendientes ni habilita Apply. Los deltas describen el comportamiento propuesto, sujeto al corte explícito al final.

### D1 - Rutas, detalle y frontera de la credencial

Controlador dedicado en `publico` con DTOs `record`; servicio de gestión en `turno`, repositorio por `token_hash` único, helper `TokenTurno` y el único `ManejadorErrores`. Validá el segmento completo de 43 caracteres ASCII base64url sin padding antes de calcular el hash de su texto UTF-8. No normalices, no pruebes el hash recibido como credencial ni uses cookies, DNI, ids o slug provistos por el cliente para autorizar. La consulta y los snapshots salen del turno autorizado y su propio consultorio. No cargar contacto mutable para armar el DTO ni para notificar.

**Desambiguación en el ruteo, no en Bean Validation.** Restringí el GET al segmento completo de token, según D2, sin reservar el slug `turnos` ni cambiar los matchers de C-07. Los POST tienen un segmento de acción adicional y no se cruzan con C-07; validá el token en el controlador después de la validación HTTP del cuerpo cuando corresponda.

**GET `/api/public/turnos/{token}` → 200**, ejemplo:

```json
{
  "inicio": "2026-11-27T09:00:00-03:00",
  "fin": "2026-11-27T09:30:00-03:00",
  "estado": "RESERVADO",
  "prestacion": { "id": 1, "nombre": "Consulta" },
  "odontologo": {
    "slug": "demo",
    "nombre": "Ana",
    "apellido": "Pérez",
    "direccionConsultorio": null
  },
  "puedeCancelar": true,
  "puedeReprogramar": true
}
```

Sin id del turno, paciente u odontólogo, ningún dato del paciente, hash, token, motivo de inhabilitación, ni enlace al sucesor. `fin` refleja la duración guardada, aunque el catálogo haya cambiado. `puedeCancelar` se calcula con estado `RESERVADO` e `inicio > ahora`, aun con prestación/odontólogo inactivo. `puedeReprogramar` también requiere ambos activos y snapshot admisible; no promete que haya horarios. El enlace viejo no rota ni redirige: conserva acceso de lectura solo a su propio turno.

**POST `/cancelar` → 204**, sin cuerpo de entrada requerido ni de salida. **POST `/reprogramar` → 204**, JSON `{ "inicio": "2026-11-27T10:00:00-03:00" }`, sin cuerpo de respuesta ni `Location`. `inicio` es `OffsetDateTime` obligatorio; rechazar campos de entrada adicionales solo en este contrato, sin cambiar el mapper global. 204 evita devolver links, ids o información que la UI no necesita; la UI puede releer el original y el email trae el nuevo link.

### D2 - Reglas de agenda y tabla de errores

C-11 puede cargar el perfil y la ventana de C-07 con `odontologo.slug`, y usar `GET /api/public/{slug}/disponibilidad?prestacionId=&fecha=` con el id devuelto. Reenviá el `inicio` ofrecido; al escribir, revalidá con el mismo predicado de C-07, `ZonaNegocio` y `Clock`, horizonte configurable inclusivo en la fecha local, franja desde su inicio en pasos de 15 min, duración vigente y rangos `[)`. No excluir al propio original: si el destino lo pisa, también queda fuera del selector público. Mismo instante con otro offset no es un cambio; pasa por las mismas reglas y, si llega al chequeo de ocupación, recibe `RN-TU-01`. No se acepta un no-op exitoso ni se agrega una consulta especial por token. Si la prestación cambió de duración, el original queda como estaba y el sucesor usa la duración vigente del destino.

**Matcher GET:** `/api/public/turnos/{token:[A-Za-z0-9_-]{43}}`, sin lookahead negativo. Spring MVC `PathPatternParser` 7.0.10 distingue las llaves anidadas del cuantificador `{43}` de las llaves externas de captura; el regex se aplica al segmento completo, no a los separadores `/`. El literal `disponibilidad` no coincide, por lo que C-07 conserva su GET (incluido 400 `SOLICITUD_INVALIDA` si faltan parámetros), `GET /api/public/turnos` sigue siendo el perfil del slug y `POST /api/public/turnos/turnos` sigue siendo la reserva. `GET /api/public/turnos/turnos` conserva el 405 `METODO_NO_PERMITIDO` del path de reserva, no se interpreta como gestión. No dependas del orden de controladores.

**GET sin match propio:** para un segmento mal formado que llega a MVC y no coincide con otra ruta ni con un recurso real, `ConfiguracionSeguridad` deja pasar `/api/public/**` sin sesión (y mantiene su exención CSRF); `ConfiguracionSpa` no devuelve `index.html` bajo `api/`. `ManejadorErrores` transforma `NoResourceFoundException` o `NoHandlerFoundException` en el mismo 404 JSON `NO_ENCONTRADO` que un token de formato válido sin coincidencia por hash. No cambiar firewall/contenedor: sus rechazos previos a MVC no pertenecen a ese contrato. Cubrir también headers de D5 cuando el GET mal formado no entra al controlador.

Orden de evaluación para solicitudes que el ruteo entregó a gestión; un error de lock/SQL puede interrumpir la operación en el punto donde la base lo detecta. El check del original vale para ambos POST:

| Orden | Situación | HTTP / `codigo` | `mensaje` |
|---|---|---|---|
| 1 | `inicio` faltante o nulo en JSON válido | 400 `VALIDACION` | “Hay datos inválidos. Revisalos y probá de nuevo.”, con `detalles` para `inicio` |
| 1 | JSON ilegible, `inicio` sin offset o campo extra; tipo no soportado | 400 / 415 `SOLICITUD_INVALIDA` | Mensajes genéricos del advice para cada caso |
| 2 | Token mal formado, desconocido o hash presentado como token | 404 `NO_ENCONTRADO` | “No encontramos lo que buscabas.” |
| 3 | Original con `inicio <= ahora`, cualquiera sea su estado | 409 `RN-TU-07` | “Este turno ya empezó. Solo podés consultarlo.” |
| 4 | Original futuro no `RESERVADO` | 409 `RN-SE-02` | “Este turno solo se puede consultar.” |
| 5 | Reprogramar sin odontólogo o prestación activos y asociados | 404 `NO_ENCONTRADO` | Mismo mensaje genérico que arriba |
| 6 | Reprogramar sin snapshot o con buzón no admisible según C-08 | 409 `RN-SE-04` (propuesta pendiente de Estefi) | “No podés reprogramar este turno desde el link.” |
| 7 | Destino anterior a `ahora + 24 h` → posterior al horizonte local → fuera de agenda o bloqueado | 422 `RN-TU-03` → `RN-TU-11` → `RN-AG-05` | Reusar mensajes de C-07, en ese orden |
| 8 | Solapamiento o conflicto SQL de turno, incluso lock | 409 `RN-TU-01` | “Ese horario acaba de ser tomado, elegí otro” |
| — | Otro error de integridad (p. ej. colisión de hash) o error inesperado | 500 `ERROR_INTERNO` | Genérico, sin valores ni texto de base |

`RN-TU-07` prevalece sobre `RN-SE-02` cuando `inicio <= ahora`, aun con un original cancelado. `RN-SE-04` no colisiona con un código existente en el repo y figura en [KB 05](../../../knowledge-base/05_reglas_de_negocio.md#propuesta-para-c-09-pendiente-de-aprobación-de-estefi-no-vigente) solo como propuesta: aplica al destinatario, no a otros rechazos ni a C-07/C-08. Los 409 de negocio usan JSON `{codigo, mensaje}` sin `detalles`. La anticipación se valida **solo para el destino**. Cancelar no necesita probar agenda, horizonte, que haya email ni que el horario vaya a figurar en la disponibilidad: libera ocupación, no altera los demás filtros.

### D3 - Una transacción, dos protecciones y orden de locks

**Cancelación:** buscar/bloquear el original por hash con lock de fila de escritura, sin locks en joins ni advisory. Leer estado y hora de nuevo después de obtener el lock; tomar un instante del `Clock` para validar y marcar `cancelado_en`. Cambiar a `CANCELADO/PACIENTE`, hacer flush, publicar el único evento. El trigger de V2 no toma advisory al salir del conjunto activo. Este camino no debe pedir después locks de paciente ni advisory, tampoco al construir el evento.

**Reprogramación:** una transacción de escritura a `READ COMMITTED`, con dos protecciones: fila del original para consumo único del link de escritura, y advisory existente del consultorio más constraint para el destino.

1. Resolver por hash en proyección de ids, sin materializar ni bloquear todavía el original. Primero bloquear al paciente sin actualizarlo: `SELECT id FROM paciente WHERE id = ? AND odontologo_id = ? FOR NO KEY UPDATE`, por PK y con el consultorio obtenido del original. `RegistroPacientes` usa `ON CONFLICT ON CONSTRAINT paciente_dni_por_odontologo_uk DO UPDATE` sobre la misma fila: V2 define la única de `(odontologo_id, dni)` y la PK `id`; el upsert cambia solo nombre, apellido, teléfono, email y `actualizado_en`, no claves. La serialización requiere que los locks de fila **conflictúen**: `FOR NO KEY UPDATE` entra en conflicto consigo mismo y con `FOR UPDATE`, así que serializa C-09 contra otro C-09 y contra el `DO UPDATE` de C-07 aunque este tome el modo más fuerte. No se presupone el modo interno exacto del upsert. El modo elegido no bloquea `KEY SHARE` de la FK. Después tomar **el mismo** `pg_advisory_xact_lock` de dos claves de V2, con namespace `hashtext('turno_sin_solapamiento')` y `hashtext(odontologo_id::text)`, en la misma conexión y transacción. No tomar el lock de turnos antes del advisory en este flujo. C-07 sigue `paciente → advisory` al insertar. No usar un lock de sesión ni otro namespace.
2. Tomar lock solo sobre la fila original; volver a leer su identidad, estado y vínculos bajo lock, no reusar una entidad cacheada de la consulta inicial. Una vez obtenidos todos los locks, capturar un único `Instant ahora` del `Clock`. Usarlo para decidir estado/elegibilidad temporal del original, el día local, la anticipación y el horizonte del destino, y `cancelado_en`. Revalidar consultorio/prestación y destinatario en el orden de D2. Chequear disponibilidad antes de modificar para seguir viendo el original. La cancelación sola puede ganar la fila mientras se espera y su resultado se revalida.
3. Cambiar el original a `CANCELADO/PACIENTE` y **hacer flush del UPDATE antes de invocar el INSERT de `IDENTITY`**, para quitar la ocupación en el orden correcto. Insertar sucesor `RESERVADO/ONLINE` con mismo paciente, odontólogo y prestación, nuevo inicio/fin, `reprogramado_desde_id`, snapshot heredado y hash nuevo; no copiar marcas de cancelación ni recordatorio. Hacer flush y publicar solo `TurnoReprogramado` antes del commit.
4. La constraint `turno_sin_solapamiento` sigue decidiendo aunque la prevalidación pase. Usar `GuardiaSolapamiento` en la frontera de la operación transaccional (invocada a través de otro bean), para cubrir locks, flush y errores que emerjan al cerrar la transacción. Mapear `23P01` solo para la constraint nombrada, `40P01`/`55P03` y las fallas de lock traducidas por la capa de persistencia a `RN-TU-01` en este ámbito; no cambiar la traducción global de otros módulos. No capturar para continuar, usar transacciones separadas ni reintentar dentro de una transacción abortada.

El reemplazo de un futuro por otro futuro no suma reservas para ese DNI (RN-PA-03). No ejecutar `count >= 2` ni upsert en C-09: una persona con dos turnos puede mover uno. El lock de paciente mantiene coordinación con la reserva de C-07, sin tocar el contacto. No reusar `reservar()`: haría upsert, aplicaría el cupo de alta y publicaría `TurnoReservado`. Tampoco depender solo del advisory implícito en INSERT: llegaría después de una escritura del original. La alternativa de serializar todo el consultorio por fila o usar `SERIALIZABLE` con reintentos no agrega una garantía útil al esquema existente.

**Sobrecarga para el mismo instante:** `DisponibilidadService.validarInicio(Long odontologoId, Prestacion prestacion, Instant inicio, Instant ahora)` deriva `hoy` con `LocalDate.ofInstant(ahora, zonaNegocio.zona())` y no vuelve a leer `Clock` ni `zonaNegocio.hoy()`. Ambas firmas delegan al mismo núcleo privado con `ahora` y `hoy` explícitos. La firma actual de tres argumentos conserva el orden de preparar agenda/duración y sus lecturas propias (`zonaNegocio.ahora()` y `zonaNegocio.hoy()`), incluso si cruza medianoche entre ellas; conserva así sus resultados y punto de invocación de C-07. No cambiar `horariosLibres` ni el conteo de cupo. La auditoría técnica general no forma parte de esta marca de negocio.

### D4 - Destinatario de gestión, legados y eventos

**Default propuesto:** capturar `turno.email_gestion` de la misma solicitud de reserva y dentro de su transacción, con el valor exacto que C-07 ya acepta. Al reprogramar copiarlo del original; no se actualiza con el upsert ni se permite elegirlo desde la gestión. Este dato vincula un turno a la dirección a la que se dirigió su link, no verifica propiedad ni entrega. No usar `paciente.email`, `notificacion` ni un turno cercano: el primero es editable por DNI y la auditoría es best effort, incompleta y no un vínculo de autorización.

**Legados y buzones no admisibles:** dejar el snapshot nullable sin backfill. C-07 acepta hasta 255 caracteres y una sintaxis más amplia que C-08 (`AvisoCorreo.buzonValido`: hasta 254 y un buzón ASCII estricto). Un snapshot presente no basta. Extraer/reusar el mismo predicado puro de buzón en una pieza interna compartida que C-08 siga usando sin cambios de conducta. No endurecer `SolicitudReserva.EMAIL`, normalizar para forzar admisión ni prometer entrega. Si falta el snapshot o el buzón no es admisible, permitir leer y cancelar; emitir `TurnoCancelado` sin email utilizable, que C-08 descarta sin transporte ni intento de auditoría. Rechazar la reprogramación **antes de escribir o generar el token**; no quemar el único link de gestión a sabiendas de que el nuevo será descartado. Alternativa a revisar: hacer legados solo lectura incluso antes del inicio. No se recomienda porque no permite liberar el turno y no mejora el secreto cuando la cancelación no envía correo. No proponer recuperación ni reenvío para completar datos legados.

Mantener firmas `TurnoCancelado(ResumenTurno turno, CanceladoPor canceladoPor, Instant canceladoEn)` y `TurnoReprogramado(ResumenTurno anterior, ResumenTurno nuevo, String token)`. Snapshots inmutables con los datos necesarios para el aviso; no incluir PII innecesaria del contacto actual, ni pasar entidades o loguear snapshots. En reprogramación solo se envía el aviso de `nuevo`, al snapshot heredado, con su horario y link y FK al sucesor. `TokenTurno.generar()` conserva 32 bytes aleatorios/base64url y token claro solo en memoria en el evento, cuya representación lo oculta. Cancelación no incluye link ni token. Reusar el consumidor `AFTER_COMMIT` sin fallback y el registro independiente de C-08; no agregar SMTP sincrónico, reintentos ni segundo evento.

### D5 - Confidencialidad de URLs y errores

- El link es una credencial portadora; conservar sus rutas y la política de C-03 sin ampliar `permitAll`, CSRF, CORS ni cookie/sesión. No crear sesión por ser portador del token. No usar `GET` para mutar.
- En las respuestas de gestión, incluidas las de error, y en el documento del link `/turno/{token}` servido por Spring, fijar `Cache-Control: no-store` y `Referrer-Policy: no-referrer`. Limitar estos headers a los caminos con credencial; no modificar el frontend ni cacheo de assets de la SPA. Evitar redirecciones que repitan el segmento; C-11 debe conservar estas protecciones y no transmitir URL ni token a terceros. HTTPS fuera de localhost; no afirmar que esto borra historial del navegador ni protege links reenviados.
- Revisar access logs del contenedor y proxy, logs de request/error (el advice hoy puede loguear la excepción completa), trazas/métricas, SQL con valores y salida SMTP. Omitir o redactar el segmento antes del primer registro; usar patrón de ruta y categorías cerradas, sin excepción cruda en esos caminos sensibles. Verificar 200/204/400/404/409/415/422/500, incluyendo token inválido y fallas inesperadas inducidas en test. No modificar logs de módulos no relacionados. Ni el hash ni el link completo se devuelven o se loguean. Despliegues fuera de la demo deben verificar también la capa de entrada que quede fuera del repo.
- **Rechazos de escritura:** C-08 ya detectó PII en un detalle de PostgreSQL/Hibernate emitido antes del advice. Con V9, una fila rechazada puede incluir `email_gestion`. Capturá salidas de ORM/driver y verificá también el destino de logs de PostgreSQL configurado en el entorno de prueba, con centinelas de email, token y hash en escrituras rechazadas (reserva y sucesor). No des por probado el log inferior con `toString()` ni un JSON limpio. La mitigación concreta queda pendiente de revisión y debe ser acotada a la superficie comprobada; no se propone silenciar logging global.

### D6 - Estrategia de pruebas

Strict TDD: prueba en rojo observada por el comportamiento faltante, mínima implementación en verde y refactor con regresión; no omitir tests ni fingir un rojo. Unitarios para frontera de token/DTO, reloj y precedencias, admisión de buzón, y traducción de `40P01`/errores de integridad. IT reales por HTTP/MockMvc, PostgreSQL local `turnotarde_test`, `Clock` controlable y SMTP falso, sin H2 ni red SMTP. Usar conexiones independientes para observar commit y tests de rollback con base real.

Las carreras HTTP usan servidor y transacciones reales, sincronización determinista con barreras/timeout y sin `sleep`: dos acciones sobre el mismo original, dos originales al mismo destino, y reserva contra reprogramación (incluido mismo paciente para verificar orden de locks/cupo). El perdedor por destino puede fallar en validación previa; eso **no** demuestra rollback tras UPDATE. Cubrirlo por separado, forzando de modo acotado al test que el precheck dé paso con destino realmente ocupado y que la exclusion constraint del INSERT real falle tras el UPDATE; instrumentar que el UPDATE se ejecutó, consultar el estado persistido desde otra conexión y verificar que no queda sucesor ni aviso. Ejercitar también rollback posterior a publicar y comprobar ausencia de consumo `AFTER_COMMIT` con ejecutor controlado/drenado. Mantener las regresiones de C-07 (exactamente una reserva ganadora) y C-08 con sus contratos intactos.

Con un `Clock` controlable, avanzar mientras espera un lock hasta el inicio exacto (409 `RN-TU-07`); en escenarios separados avanzar después de capturar `ahora` durante lecturas de agenda. Verificar que el borde de 24 h y la fecha local al cruzar medianoche (último día del horizonte y siguiente) se juzgan con el instante capturado, que el `cancelado_en` confirmado coincide y que no hay una segunda lectura para decisiones de negocio. Mantener regresiones C-07 de la firma actual, disponibilidad y reserva.

## Risks / Trade-offs

- **Turnos ya emitidos sin snapshot** → conservan consulta y cancelación, pero no todos recibirán aviso ni podrán moverse: degradación visible, sin redirigir la credencial a un tercero. Para la demo, usar nuevas reservas con buzón admisible después de migrar.
- **SMTP/cola y caída post-commit** → aun con buzón admisible, no hay entrega garantizada, el 204 no promete correo y el sucesor no se revierte. Se preserva el mejor esfuerzo de C-08 (incluida auditoría cuando corresponde).
- **Esperas por paciente/consultorio** → transacciones breves y sin SMTP, orden fijo y tests de contención. No editar V2 ni atenuar la constraint.
- **Inversión de locks al actualizar un turno activo (C-14/C-19):** `Turno` no usa `@DynamicUpdate`; Hibernate puede incluir `estado`, `inicio`, `fin` u `odontologo_id` en el `SET` al marcar solo `recordatorio_enviado_en` en un `RESERVADO`. El trigger V2 es `UPDATE OF` esas cuatro columnas y, si `NEW.estado` es activo, toma el advisory aunque los valores del `SET` no cambien. Fila → advisory puede cruzarse con advisory → fila de C-09 y causar `40P01`; la guardia lo mostraría como 409 `RN-TU-01` aunque el destino esté libre.
- **Regla de integración futura:** para updates ajenos a estado/ocupación (C-14, marca y auditoría), no incluir ninguna de esas cuatro columnas en el `SET` (UPDATE específico o `@DynamicUpdate` según el caso, verificando el SQL efectivamente emitido). C-19 legítimamente cambia `RESERVADO → ATENDIDO/AUSENTE`; sigue en el conjunto activo y, aun con intervalo/ocupación idénticos, `SET estado` activa el trigger. `@DynamicUpdate` no evita ese caso: obtener el mismo advisory **antes** del lock de fila/UPDATE, siguiendo `paciente si aplica → advisory → fila`, también para cualquier otra escritura que cambie ocupación y dispare el trigger. Al integrar cada change, inspeccionar SQL y probar la carrera con C-09 en PostgreSQL real (barreras y conexiones independientes), verificando ausencia de `40P01`/falsos 409 y preservación de los efectos válidos. Esta precaución no cambia C-14/C-19, V2 ni el mapeo aprobado.
- **Datos personales duplicados** → decisión explícita de privacidad: una columna restringida a gestión, sin exposición, backfill ni índices adicionales. Protección de backups y acceso a DB sigue siendo relevante.
- **Cambio de agenda posterior al selector** → el POST revalida y C-11 refresca ante conflicto o rechazo de agenda; no se modifica C-05 ni se promete congelar el catálogo entre requests.

## Migration Plan

Si Estefi aprueba D4, agregar `V9__email_gestion_turno.sql` con `email_gestion varchar(255)` nullable, sin default ni backfill. No editar migraciones existentes ni seeds reales, ni exigir `NOT NULL` a legados; verificar Flyway sobre base vacía y copia de prueba con turnos viejos. Aplicar la migración antes de usar la versión nueva; nuevos turnos de C-07 escriben el snapshot y los sucesores lo heredan. No hay nuevas variables ni dependencias. Si se vuelve a la versión anterior, dejar la columna aditiva y los datos confirmados intactos: las altas de esa versión quedan sin snapshot. Volver a desplegar no reenvía ni recupera links; no revertir destructivamente V9.

## Corte antes de Apply (ALTO)

1. **GATE 3:** C-08 archivado; C-05 pendiente aunque la ficha C-09 solo liste C-07/C-08. No empezar sin cumplir el gate o sin excepción explícita de Estefi.
2. **V9:** aprobación explícita de Estefi pendiente para la columna nullable, su propósito y el vínculo entre turno y destinatario. Solo si la aprueba, documentar en KB 04 (`§turno`) la columna, el propósito y ese vínculo (D4 y tareas); no actualizar KB 04 en esta revisión.
3. **Política de legados y `RN-SE-04`:** pendientes de aprobación explícita de Estefi; consulta y cancelación sin aviso si corresponde, reprogramación bloqueada ante snapshot ausente/no admisible. No activar la RN propuesta en KB 05 sin esa decisión.
4. **C-11:** la actualización de su contrato en `CHANGES.md` queda pendiente, a cargo de Estefi.
5. **Apply:** autorización separada pendiente, aun con diseño general aprobado con condiciones. No empezar al cerrar solo esta revisión documental.

## UI Mockups

No aplica: C-09 es solo API y protección del documento existente; la UI de gestión y el mockup de revisión 2 corresponden a C-11.
