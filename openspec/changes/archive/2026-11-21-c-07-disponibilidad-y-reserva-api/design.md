# Design — C-07 `disponibilidad-y-reserva-api`

## Context

Motivación y alcance: ver `proposal.md`. Requisitos: `specs/disponibilidad-turnos`, `specs/reserva-online`, `specs/eventos-turno` y el delta de `specs/errores-api`.

Estado que deja C-02 (commit `6659e83`, PR pendiente de merge a `main`; esta rama sale de ahí):

| Pieza | Estado actual |
|---|---|
| Esquema | `V2__esquema_base.sql`: `odontologo` (`slug` único, `activo`), `horario_atencion` (`dia_semana` ISO, `time` local), `bloqueo` y `turno` en `timestamptz`, `prestacion` (`activa`, duración múltiplo de 15), `paciente` con `UNIQUE (odontologo_id, dni)` = `paciente_dni_por_odontologo_uk` y `CHECK dni ~ '^[0-9]{7,8}$'`, `turno.token_hash char(64)` único. |
| Anti-solapamiento | `turno_sin_solapamiento` (EXCLUDE gist, `[)`, estados activos). Violación = SQLState `23P01` con ese nombre (contrato con C-07). |
| Trigger | `turno_serializar_por_odontologo` (BEFORE INSERT/UPDATE de turnos activos) toma `pg_advisory_xact_lock` por odontólogo: la segunda transacción espera el commit de la primera y recibe `23P01` determinista. Se agregó en el apply de C-02 porque sin él aparecía `40P01` (ver "Desvíos del apply" de C-02). |
| Entidades | `Odontologo`, `HorarioAtencion`, `Bloqueo`, `Prestacion`, `Paciente`, `Turno` con getters y sin métodos de dominio. Repositorios vacíos salvo `OdontologoRepository.findBySlug`. Auditoría (`creado_en`/`actualizado_en`) con el `Clock` inyectado. |
| Errores | Único `@RestControllerAdvice` `ManejadorErrores` → `ErrorApi {codigo, mensaje, detalles?}`; `ReglaNegocioException(codigo, mensaje, HttpStatus)`; `VALIDACION`/`SOLICITUD_INVALIDA` 400, `NO_ENCONTRADO` 404, `ERROR_INTERNO` 500 sin filtrar detalles. |
| Tiempo | Bean `Clock` UTC; `ZonaNegocio` (`ahora()`, `hoy()`, `aLocal`, `aInstante`); ArchUnit `SinHoraDelSistemaTest` rompe la build con `now()` sin reloj. `AppProperties.reservaMaxDias` (60). |
| Tests | `*Test` unitarios; `*IT` contra PostgreSQL `turnotarde_test` (nunca H2); `IntegracionTest`, `LimpiezaBase` (TRUNCATE, commits reales), `FixturesJdbc`, `RelojDePrueba` + `ConfiguracionRelojDePrueba` (fijo en `2026-11-25T12:00:00Z`); `ReservasConcurrentesIT` (5 repeticiones, camino JPA). |
| Seguridad | **No existe Spring Security** (C-03, en paralelo, CRÍTICO). Solo `spring-security-crypto`. |
| Stack | Spring Boot 4.1.2 (Hibernate 7, Jackson 3), Java 25, `spring-boot-starter-webmvc-test` (MockMvc en `org.springframework.boot.webmvc.test.autoconfigure`). |

Restricciones: demo el miércoles 2026-11-25, camino crítico atrasado, equipo en paralelo (C-03 Marcos, C-08 Lauti, C-10 Jero). Governance **ALTO**: este documento se revisa antes del apply.

**Convención de los ejemplos**: reloj en `2026-11-25T12:00:00Z` (miércoles 09:00 en Buenos Aires), `app.reserva-max-dias = 60`, horas locales UTC-03:00.

## Goals / Non-Goals

**Goals:**
- Una sola función pura decide si un inicio es reservable; la disponibilidad y la reserva la usan las dos, así no pueden divergir.
- Contratos estables y publicados acá (JSON, códigos de error, eventos) para que C-08, C-09, C-10 y C-15 avancen sin esperar el merge.
- RN-TU-01 demostrada de punta a punta por HTTP con concurrencia real; RN-PA-03 también bajo concurrencia.
- Cero datos de otros pacientes en respuestas públicas; token en claro nunca persistido ni devuelto.

**Non-Goals:**
- Envío de emails, plantillas y registro en `notificacion` (C-08). C-07 solo publica el evento.
- Consulta, cancelación y reprogramación por token (C-09). C-07 define `TurnoCancelado`/`TurnoReprogramado` pero no los publica.
- Spring Security, CSRF, sesión, rate limiting (C-03). C-07 **no** agrega `spring-boot-starter-security`.
- ABM de horarios, bloqueos y prestaciones (C-05) y turno manual (C-15).
- UI (C-10).
- Migraciones: **C-07 no necesita cambios de esquema**. Si el apply descubriera alguno, va en `V7__<descripcion>.sql` (convención de C-02) y se registra en "Desvíos del apply".

## Decisions

### D1 — Ubicación de las piezas (monolito por funcionalidad)

| Paquete | Pieza | Rol |
|---|---|---|
| `agenda` | `CalculadoraDisponibilidad` (clase sin Spring), records `Franja`, `Intervalo`, `AgendaDelDia`, enum `MotivoRechazo` | Función pura (D2). |
| `agenda` | `DisponibilidadService` (`@Service`, `readOnly`) | Carga franjas, bloqueos y turnos activos del día y llama a la calculadora. Expone `horariosLibres(...)` y `validarInicio(...)`. |
| `publico` | `ControladorPublico`, `PublicoService`, DTOs `record` | Endpoints `/api/public/{slug}/**`, resolución de slug/prestación activos (404 uniforme). |
| `turno` | `ReservaService` (`@Transactional`), `TokenTurno`, `GuardiaSolapamiento`, `ReglasTurno` (fábrica de `ReglaNegocioException` con códigos y mensajes) | Reserva (D5–D8). |
| `turno.eventos` | `ResumenTurno`, `TurnoReservado`, `TurnoCancelado`, `TurnoReprogramado` | Contrato de eventos (D9). |
| `paciente` | `RegistroPacientes` (upsert con `JdbcTemplate`) | D6. |
| `shared/error` | `ConflictoTurnos` (detector de SQLState) + handler nuevo en `ManejadorErrores` | D8. |

Los métodos de consulta nuevos se agregan **al final** de cada repositorio para minimizar conflictos con C-05:
`OdontologoRepository.findBySlugAndActivoTrue`, `PrestacionRepository.findByIdAndOdontologoIdAndActivaTrue` y `findByOdontologoIdAndActivaTrueOrderByNombre`, `HorarioAtencionRepository.findByOdontologoIdAndDiaSemanaOrderByHoraInicio`, `BloqueoRepository` y `TurnoRepository` con `@Query` JPQL que proyecta a `Intervalo(inicio, fin)` (nunca entidades con paciente, RN-SE-03), `TurnoRepository.contarReservadosFuturos(odontologoId, pacienteId, ahora)`.

### D2 — Disponibilidad como función pura, compartida con la reserva

- `CalculadoraDisponibilidad` recibe todo por parámetro: `fecha`, `zona`, franjas del día (`LocalTime` inicio/fin), ocupados (`Intervalo` de bloqueos y turnos activos, en `Instant`), `duracion`, `ahora` (`Instant`), `anticipacionMinima` (`Duration`), `hoy` y `maxDias`. Sin `Clock`, sin repositorios: se testea con valores fijos.
- Dos operaciones sobre **el mismo predicado**:
  - `horariosLibres(...)` → `List<Instant>` ordenada y sin duplicados.
  - `evaluar(inicio, ...)` → `MotivoRechazo` (`NINGUNO`, `ANTICIPACION`, `HORIZONTE`, `FUERA_DE_AGENDA`, `OCUPADO`), en ese **orden de evaluación** (define qué código ve el paciente si falla más de una regla).
- Reglas, con instantes absolutos:
  1. Candidatos: para cada franja, minutos del día `m = inicioFranja, +15, ...` mientras `m + duración <= finFranja` (aritmética en minutos enteros, sin `LocalTime.plus` que da la vuelta a medianoche). Cada candidato se resuelve a `Instant` con `ZonedDateTime.of(fecha, hora, zona)`; el fin es `inicio + Duration.ofMinutes(duración)` y debe ser `<= instante(fecha, finFranja)`.
  2. `ANTICIPACION`: `inicio < ahora + anticipacionMinima`. La reserva online pasa `Duration.ofHours(24)` (24 h **absolutas**, no "misma hora de mañana"). C-15 podrá pasar `Duration.ZERO` (RN-TU-08) sin tocar la calculadora.
  3. `HORIZONTE`: `fechaLocal(inicio) > hoy + maxDias` (comparación de `LocalDate` en la zona del negocio; el día 60 se puede reservar completo).
  4. `FUERA_DE_AGENDA`: el inicio no es un candidato de (1) o `[inicio, fin)` cruza un bloqueo (RN-AG-05).
  5. `OCUPADO`: `[inicio, fin)` cruza un turno activo (`RESERVADO`, `ATENDIDO`, `AUSENTE`). Intervalos semiabiertos: contiguos no se cruzan (igual que la constraint).
- **DST**: Argentina no tiene horario de verano hoy, pero el cálculo no lo asume. Los límites del día se calculan con `fecha.atStartOfDay(zona)` y `fecha.plusDays(1).atStartOfDay(zona)`, toda comparación es entre `Instant`, y los candidatos van a un `TreeSet`. En un salto de hora un candidato inexistente se corre (regla de `ZonedDateTime`) y uno repetido se deduplica; nunca se generan turnos superpuestos (y la base lo impide igual). Hay un test con `Europe/Madrid` el 2027-03-28 que lo comprueba.
- **Alternativa descartada**: validar la reserva con un algoritmo propio separado de la disponibilidad. Dos implementaciones de la misma regla terminan divergiendo; el paciente vería horarios que después le rechazan.

### D3 — Endpoints y contratos JSON (para C-10)

Todos bajo `/api/public/{slug}`, sin sesión, `application/json`. Instantes con offset de la zona del negocio (`OffsetDateTime`, ej. `2026-11-27T09:00:00-03:00`); fechas `YYYY-MM-DD`.

**`GET /api/public/{slug}`** → 200
```json
{
  "slug": "demo",
  "nombre": "Ana",
  "apellido": "Pérez",
  "direccionConsultorio": "Av. Colón 1234, Córdoba",
  "zonaHoraria": "America/Argentina/Buenos_Aires",
  "fechaMinima": "2026-11-26",
  "fechaMaxima": "2027-01-24",
  "prestaciones": [
    { "id": 1, "nombre": "Consulta", "duracionMinutos": 30 },
    { "id": 2, "nombre": "Limpieza", "duracionMinutos": 45 }
  ]
}
```
- `fechaMinima` = fecha local de `ahora + 24 h`; `fechaMaxima` = `hoy + app.reserva-max-dias`. Calculadas con el reloj del servidor (RN-GL-02), para que el calendario de C-10 no use la hora del celular. `direccionConsultorio` puede ser `null`. Prestaciones activas ordenadas por nombre. No incluye email, teléfono ni `id` del odontólogo.

**`GET /api/public/{slug}/disponibilidad?prestacionId=1&fecha=2026-11-27`** → 200
```json
{
  "fecha": "2026-11-27",
  "prestacionId": 1,
  "duracionMinutos": 30,
  "horarios": [
    { "inicio": "2026-11-27T09:00:00-03:00", "hora": "09:00" },
    { "inicio": "2026-11-27T09:15:00-03:00", "hora": "09:15" }
  ]
}
```
- Fecha pasada, de hoy, fuera del horizonte o sin franjas → 200 con `horarios: []` (no es un error: el calendario puede consultar cualquier día). C-10 manda de vuelta `inicio` **tal cual** en el POST.

**`POST /api/public/{slug}/turnos`**
```json
{
  "prestacionId": 1,
  "inicio": "2026-11-27T09:00:00-03:00",
  "dni": "12345678",
  "nombre": "Luis",
  "apellido": "Gómez",
  "telefono": "+5493511234567",
  "email": "luis@example.com"
}
```
→ **201** (sin `Location`, sin token, sin id de turno)
```json
{
  "inicio": "2026-11-27T09:00:00-03:00",
  "fin": "2026-11-27T09:30:00-03:00",
  "prestacion": { "id": 1, "nombre": "Consulta", "duracionMinutos": 30 },
  "odontologo": { "nombre": "Ana", "apellido": "Pérez", "direccionConsultorio": "Av. Colón 1234, Córdoba" },
  "email": "luis@example.com"
}
```
- `email` es el que el paciente acaba de ingresar (para "Te mandamos el link a ..."); no es dato de otro paciente. No se devuelve el id del turno: no le sirve a la UI y evita exponer un identificador secuencial.

Validación (`SolicitudReserva` record, Bean Validation, mensajes en voseo):

| Campo | Reglas |
|---|---|
| `prestacionId` | `@NotNull @Positive` |
| `inicio` | `@NotNull`, tipo `OffsetDateTime`: sin offset Jackson no lo parsea → 400 `SOLICITUD_INVALIDA` (evita interpretar la hora del cliente) |
| `dni` | `@NotBlank @Pattern("^[0-9]{7,8}$")` — "Ingresá tu DNI sin puntos, 7 u 8 dígitos" |
| `nombre`, `apellido` | `@NotBlank @Size(max = 100)` |
| `telefono` | `@NotBlank @Pattern("^\\+[1-9][0-9]{7,14}$")` (E.164, máx. 15 dígitos) — "Ingresá el teléfono con código de país, por ejemplo +5493511234567" |
| `email` | `@NotBlank @Email @Size(max = 255)` + `@Pattern(".+@.+\\..+")` (el `@Email` de Hibernate acepta `a@b`) |

No se normaliza (trim, puntos del DNI): la UI de C-10 normaliza antes de enviar y el backend es estricto.

### D4 — Tabla de errores (contrato para C-10, C-09 y C-15)

| Situación | HTTP | `codigo` | `mensaje` |
|---|---|---|---|
| Campo inválido o faltante | 400 | `VALIDACION` | "Hay datos inválidos. Revisalos y probá de nuevo." + `detalles: [{campo, mensaje}]` |
| JSON ilegible, `inicio` sin offset, `fecha` mal formada, parámetro faltante | 400 | `SOLICITUD_INVALIDA` | "No pudimos interpretar la solicitud." |
| Slug inexistente, odontólogo inactivo, prestación inexistente/de baja/ajena | 404 | `NO_ENCONTRADO` | "No encontramos lo que buscabas." (mismo texto en todos los casos, RN-SE-01) |
| Inicio antes de ahora + 24 h (incluye pasado) | 422 | `RN-TU-03` | "Los turnos se reservan con al menos 24 horas de anticipación. Elegí otro horario." |
| Inicio después del horizonte | 422 | `RN-TU-11` | "Todavía no se puede reservar para esa fecha. Elegí un día dentro de los próximos {N} días." |
| Inicio fuera de franja, fuera de paso, que no entra completo o que cruza un bloqueo | 422 | `RN-AG-05` | "Ese horario no está disponible. Elegí uno de la lista." |
| Horario ocupado (validación previa, `23P01` o `40P01`/lock) | 409 | `RN-TU-01` | "Ese horario acaba de ser tomado, elegí otro" (texto literal de RN-GL-01) |
| DNI con 2 turnos futuros `RESERVADO` | 409 | `RN-PA-03` | "Ya tenés 2 turnos reservados en este consultorio. Cancelá uno desde el link del email para sacar otro." |
| Cualquier otro | 500 | `ERROR_INTERNO` | genérico |

**Por qué 409 vs 422**: 409 cuando el rechazo depende del **estado actual** de los datos y la misma solicitud podría funcionar en otro momento (horario tomado, límite alcanzado); 422 cuando la solicitud está bien formada pero **viola una regla de agenda** que no depende de otros pacientes (anticipación, horizonte, franja). C-10 decide por `codigo`, no por estado: con `RN-TU-01` refresca la disponibilidad (US-006); con `RN-TU-03`/`RN-TU-11`/`RN-AG-05` también refresca; con `RN-PA-03` muestra el mensaje sin refrescar. C-09 ya usa 409 para `RN-TU-07` (estado del turno), coherente con este criterio. **Alternativa**: todo 409, o todo 422. Descartada: pierde la distinción útil para logs y monitoreo, y RN-GL-01 ya fija 409 para RN-TU-01.

Los códigos y mensajes viven en `turno.ReglasTurno` (fábrica de `ReglaNegocioException`), no en `CodigosError` (que es solo para códigos técnicos).

### D5 — Flujo transaccional de la reserva y orden de locks

`ReservaService.reservar(odontologo, prestacion, solicitud)` en **una** transacción (`READ COMMITTED`, la de PostgreSQL por defecto):

1. **Resolución** (en `PublicoService`, antes de la transacción de escritura): odontólogo por slug activo y prestación activa del mismo odontólogo → si no, 404.
2. **Validación de agenda** (solo lecturas): `DisponibilidadService.validarInicio` con `ahora` del `Clock` → `RN-TU-03` / `RN-TU-11` / `RN-AG-05` (422) o `RN-TU-01` (409, validación previa para dar el mensaje claro, DD-03). Nada se escribió todavía.
3. **Upsert del paciente** (D6): toma el lock de fila de `paciente (odontologo_id, dni)` hasta el commit.
4. **Límite RN-PA-03**: `count(turno) where paciente_id = ? and odontologo_id = ? and estado = 'RESERVADO' and inicio > ahora` `>= 2` → 409 `RN-PA-03`.
5. **Token** (D7) y `turnos.saveAndFlush(new Turno(..., ONLINE, hash))` envuelto en `GuardiaSolapamiento` (D8). El trigger toma el advisory lock del odontólogo; la constraint decide.
6. **Evento**: `publisher.publishEvent(new TurnoReservado(...))` todavía dentro de la transacción (D9).
7. Commit → 201.

Cualquier `ReglaNegocioException` es `RuntimeException` → rollback completo: si falla 4 o 5, el contacto del paciente **no** queda actualizado (escenario "Reserva rechazada no toca el contacto").

**Orden de locks (regla para C-09 y C-15)**: primero `paciente` (upsert), después `turno` (advisory lock del trigger). Ningún camino toma el advisory lock y después el lock de paciente, así que no hay ciclo posible entre reservas. C-15 reutiliza `ReservaService` con origen `MANUAL` y anticipación cero; C-09 (reprogramar) no hace upsert de paciente.

### D6 — Upsert atómico del paciente con `INSERT ... ON CONFLICT` (cierra la carrera de RN-PA-03)

```sql
INSERT INTO paciente (odontologo_id, dni, nombre, apellido, telefono, email, creado_en, actualizado_en)
VALUES (?, ?, ?, ?, ?, ?, ?, ?)
ON CONFLICT ON CONSTRAINT paciente_dni_por_odontologo_uk
DO UPDATE SET nombre = EXCLUDED.nombre, apellido = EXCLUDED.apellido, telefono = EXCLUDED.telefono,
              email = EXCLUDED.email, actualizado_en = EXCLUDED.actualizado_en
RETURNING id
```
- Se ejecuta con `JdbcTemplate` en `paciente.RegistroPacientes`; comparte la transacción de JPA (el `JpaTransactionManager` expone la conexión JDBC). Los instantes de auditoría salen de `ZonaNegocio.ahora()` (nunca `now()` de la base). Después, `pacientes.getReferenceById(id)` para el FK del turno.
- **Por qué**: (a) sin carrera en la creación: dos reservas simultáneas del mismo DNI nuevo no generan `23505` (la segunda espera y entra por `DO UPDATE`); (b) el `DO UPDATE` deja la fila del paciente bloqueada hasta el commit, así que **dos reservas del mismo DNI se serializan**: la segunda recién cuenta sus turnos (paso 4, nueva instantánea en `READ COMMITTED`) cuando la primera confirmó. Eso hace que RN-PA-03 se cumpla bajo concurrencia sin locks extra ni `SERIALIZABLE`. Hay un test concurrente que lo prueba.
- **Alternativas descartadas**: `findBy...` + `save` en JPA (carrera en la creación y en el límite: dos requests del mismo DNI podrían dejar 3 turnos futuros); `SELECT ... FOR UPDATE` sobre el paciente (no existe la fila la primera vez); aceptar la carrera para la demo (no hace falta: el upsert la cierra gratis). `@Query` nativa con `RETURNING` en Spring Data: descartada por riesgo con Hibernate 7 tratando un `INSERT` como consulta.
- RN-PA-02 / SU-03 / PA-05: se sobrescribe el contacto con lo último ingresado, como dice la KB. PA-05 sigue abierta para producción; no cambia la demo.

### D7 — Token de gestión

- `TokenTurno.generar()`: 32 bytes de un `SecureRandom` (instancia única, por defecto del sistema) → `Base64.getUrlEncoder().withoutPadding()` (43 caracteres, seguros en URL sin escapar). `TokenTurno.hashear(token)`: SHA-256 del texto UTF-8 del token → hex en minúscula (64 caracteres) = `token_hash`. Devuelve `record TokenTurno(String plano, String hash)` con `toString()` que **no** muestra `plano`.
- C-09 busca con `TokenTurno.hashear(tokenDeLaUrl)`; es la única función de hash: no se reimplementa.
- Colisión de `token_hash` (`turno_token_hash_uk`): probabilidad despreciable (2^-256); si ocurriera sale como 500 y no como `RN-TU-01` (D8 distingue la constraint).

### D8 — Traducción de conflictos de la base a `RN-TU-01`

- `shared.error.ConflictoTurnos.esSolapamiento(Throwable)`: recorre la cadena de causas y es `true` si encuentra un `SQLException` con SQLState `23P01` cuyo mensaje nombra `turno_sin_solapamiento`. `esFallaDeLock(Throwable)`: `CannotAcquireLockException`/`PessimisticLockingFailureException`, o SQLState `40P01` (deadlock) o `55P03` (lock no disponible). Se compara por SQLState y nombre de constraint porque el driver es `runtimeOnly` (no se compila contra `PSQLException`).
- `turno.GuardiaSolapamiento.ejecutar(Supplier<T>)`: envuelve la escritura de turnos (`saveAndFlush`). Si `esSolapamiento` o `esFallaDeLock` → lanza `ReglasTurno.horarioTomado()` (409 `RN-TU-01`); cualquier otra excepción se relanza intacta. Se usa `saveAndFlush` para que la constraint salte **dentro** del servicio y no en el commit. C-09 y C-15 usan la misma guardia (nota heredada de C-02: mapear `40P01`).
- **Red de seguridad en el único `ManejadorErrores`** (no se agrega otro advice): handler para `DataAccessException` → si `esSolapamiento` responde 409 `RN-TU-01`; si no, el mismo 500 `ERROR_INTERNO` con log que ya existe. El handler global **no** traduce fallas de lock genéricas: un deadlock en otra tabla (por ejemplo, horarios de C-05) no debe decirle al usuario "ese horario acaba de ser tomado". Ese mapeo vive solo en la guardia, que sabe que está escribiendo turnos.
- La respuesta nunca incluye SQL, nombre de constraint ni mensaje del driver (spec `errores-api`).

### D9 — Eventos de dominio y cómo llega el token en claro al email

Paquete `ar.turnotarde.turno.eventos`. Records inmutables, sin entidades JPA (no hay lazy loading fuera de la transacción). Instantes en UTC (`Instant`); el consumidor formatea con `ZonaNegocio`.

```java
public record ResumenTurno(
        long turnoId, long odontologoId, Instant inicio, Instant fin,
        long prestacionId, String prestacionNombre, int duracionMinutos,
        String pacienteNombre, String pacienteApellido, String pacienteEmail, String pacienteTelefono,
        String odontologoNombre, String odontologoApellido, String direccionConsultorio) {}

public record TurnoReservado(ResumenTurno turno, OrigenTurno origen, String token) {
    @Override public String toString() { /* turno + origen, token como "***" */ }
}

public record TurnoCancelado(ResumenTurno turno, CanceladoPor canceladoPor, Instant canceladoEn) {}

public record TurnoReprogramado(ResumenTurno anterior, ResumenTurno nuevo, String token) {
    @Override public String toString() { /* sin token */ }
}
```

| Evento | Lo publica | Cuándo | Consumidor |
|---|---|---|---|
| `TurnoReservado` | C-07 (online), C-15 (manual, `origen = MANUAL`) | Dentro de la transacción de la reserva, antes del commit | C-08: email de confirmación con `{app.base-url}/turno/{token}` |
| `TurnoCancelado` | C-09 (paciente), cancelación del odontólogo (C-13) | Dentro de la transacción de la cancelación | C-08: email de cancelación (sin link: el token ya no sirve) |
| `TurnoReprogramado` | C-09 | Dentro de la transacción única de reprogramar; `token` es el **nuevo** | C-08: email con el link nuevo |

- `pacienteEmail` es el vigente **al momento** de la reserva (SU-03: cada token va al email cargado cuando se creó). Por eso el evento lleva una copia y C-08 no necesita volver a leer al paciente.
- **Publicación**: `ApplicationEventPublisher.publishEvent` dentro de la transacción. C-08 escucha con `@TransactionalEventListener(phase = AFTER_COMMIT)` (DD-09): si hay rollback el evento se descarta; un fallo del SMTP ya no puede revertir el turno (RN-NO-04). En C-07 no hay consumidores productivos; un listener de test verifica el contrato.
- **Token en claro en memoria — razonamiento de seguridad**: RN-SE-02 prohíbe guardarlo y Flujo 3 prohíbe devolverlo; el email es el único canal. El evento es un objeto en el heap que vive desde el `INSERT` hasta que C-08 arma el email: no se serializa, no se persiste (no hay outbox), no cruza procesos, y `toString()` lo oculta para que un `log.info("{}", evento)` no lo filtre. C-08 MUST NOT loguear el token ni el link completo ni guardarlo en `notificacion`. **Trade-off aceptado**: si el proceso muere entre el commit y el envío, el turno existe pero el paciente no recibe el link; el odontólogo lo ve en su agenda (RN-NO-04: el turno vale aunque el email no llegue). Un "reenviar link" (rotar token) queda post-demo.
- **Alternativas descartadas**: (a) devolver el token en el 201 (viola Flujo 3: cualquiera que reserve con el DNI de otro obtendría el control del turno); (b) outbox con el token cifrado (agrega tabla, clave y job; desproporcionado para la demo); (c) que C-08 genere el token después del commit (exigiría un `UPDATE` de `token_hash` fuera de la transacción y deja turnos sin token si falla).

### D10 — Seguridad: coordinación con C-03 y C-10

C-07 no agrega Spring Security. Hasta que C-03 mergee, todo `/api/**` está abierto (como hoy). Recomendación para C-03 (CRÍTICO, decide Marcos con aprobación humana):
- `requestMatchers("/api/public/**").permitAll()`.
- **Exentar `/api/public/**` de CSRF** (`csrf.ignoringRequestMatchers("/api/public/**")`). Razonamiento: CSRF protege acciones que se apoyan en credenciales **ambientales** (la cookie de sesión). Los endpoints públicos no usan sesión ni otorgan más privilegio a una request forjada desde otro sitio que a una request directa del atacante. Además, solo aceptan `application/json` (un formulario HTML cross-site recibe 415), y las rutas de C-09 usan como credencial el token en la URL, no una cookie. Exigir CSRF acá obligaría al paciente a tener una cookie `XSRF-TOKEN` previa sin ganar seguridad.
- Los endpoints públicos no deben crear sesión (`JSESSIONID`) para el paciente.
- **Para C-10**: el `api-client.ts` puede mandar `X-XSRF-TOKEN` si la cookie existe (es inocuo), pero no debe depender de él en `/api/public/**`.
- **Regresión**: los IT de C-07 llaman a los endpoints públicos sin sesión ni CSRF. Cuando C-03 mergee, esos tests deben seguir en verde sin cambios: son la verificación de su configuración.
- Rate limiting: no hay en C-07. `08_arquitectura` acepta que alcanzan el límite por DNI y la regla de 24 h para la demo (ver R4).

### D11 — Estrategia de tests (TDD, PostgreSQL real, reloj fijo)

| Nivel | Clase | Qué cubre |
|---|---|---|
| Unit | `CalculadoraDisponibilidadTest` | Todos los escenarios de `disponibilidad-turnos` (pasos, cierre de franja, varias franjas, borde exacto de 24 h, día 60/61, bloqueo medio y de varios días, contiguos, turno activo/cancelado) + orden de `MotivoRechazo` + DST con `Europe/Madrid`. Sin Spring. |
| Unit | `TokenTurnoTest` | 43 caracteres base64url sin padding que decodifican a 32 bytes; vector conocido de SHA-256; dos tokens distintos; `toString` sin plano. |
| Unit | `ConflictoTurnosTest`, `GuardiaSolapamientoTest` | `23P01`+nombre → `RN-TU-01`; `23P01` de otra constraint, `23505` → no; `CannotAcquireLockException`/`40P01` → `RN-TU-01` en la guardia. |
| Unit | `ManejadorErroresTest` (existente, se amplía con `ControladorDePrueba`) | `DataIntegrityViolationException` con `23P01 turno_sin_solapamiento` → 409 sin filtrar texto; otra violación → 500. |
| Unit | `SolicitudReservaTest` (`Validator`) | DNI, teléfono E.164, email, obligatorios. |
| Unit | `EventosTurnoTest` | `toString` de `TurnoReservado`/`TurnoReprogramado` no contiene el token. |
| IT | `PerfilPublicoIT`, `DisponibilidadPublicaIT` (MockMvc) | Contrato JSON exacto (nombres de campos de D3), 404 uniformes, sin datos de pacientes ni motivo de bloqueos, 400 por parámetros. |
| IT | `RegistroPacientesIT` | Upsert crea/actualiza/separa por odontólogo, auditoría con el reloj fijo. |
| IT | `ReservaOnlineIT` (MockMvc) | 201 y estado en la base, token no en el cuerpo, `token_hash` hex 64, 400 sin efectos, 422 por código, 409 por ocupado, 404, upsert, contacto intacto tras rechazo, límite 2 y casos que no cuentan. |
| IT | `ReservaEventosIT` | Listener de test `AFTER_COMMIT`: un evento, `sha256(token) == token_hash`, turno visible desde otra conexión; sin evento en 409. |
| IT | `ReservasConcurrentesApiIT` (**obligatorio**) | `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `RestClient`, dos hilos con `CountDownLatch`, mismo horario, DNIs distintos, `@RepeatedTest(5)`: exactamente un 201 y un 409 `RN-TU-01`, un solo turno activo. Segundo método `@RepeatedTest(5)`: mismo DNI con 1 turno futuro, dos horarios distintos → un 201 y un 409 `RN-PA-03`. |

- Todos los IT con `@Import(ConfiguracionRelojDePrueba.class)` (reloj en `2026-11-25T12:00:00Z`) y `LimpiezaBase` en `@BeforeEach`. Nunca H2. Nunca `now()`: el ArchUnit existente lo vigila.
- El concurrente usa un servidor HTTP real (Tomcat con hilos propios) y no MockMvc compartido entre hilos, para no depender de la seguridad de hilos de MockMvc.
- `ReservasConcurrentesIT` de C-02 se mantiene sin cambios (prueba el camino de la base).

## Risks / Trade-offs

- **R1** [En el test concurrente por HTTP, las dos requests podrían no llegar juntas y la perdedora caer en la validación previa en lugar de la base] → el resultado esperado (un 201 y un 409 `RN-TU-01`) es el mismo por cualquiera de los dos caminos; el camino de la base sin validación previa ya lo prueba `ReservasConcurrentesIT` y la traducción la prueba `GuardiaSolapamientoTest`. Se repite 5 veces.
- **R2** [Merge con C-05 en los mismos repositorios] → métodos nuevos al final de cada interfaz, sin tocar los existentes. Si C-05 mergea antes, se rebasa y se reusan sus métodos si son equivalentes.
- **R3** [Cuando C-03 mergee, Spring Security por defecto exige auth y CSRF en todo] → D10 lo documenta; los IT de C-07 fallan de inmediato si la configuración de C-03 no libera `/api/public/**`. Avisar a Marcos antes del merge.
- **R4** [Sin rate limiting, alguien puede ocupar muchos horarios con DNIs inventados] → aceptado para la demo (DD-02, `08_arquitectura` §Seguridad). Post-demo: límite por IP o por email.
- **R5** [Token perdido si el proceso cae entre commit y email] → aceptado (D9). Post-demo: reenviar link.
- **R6** [El evento se publica aunque todavía no haya consumidores] → sin efecto: Spring descarta eventos sin listeners. El listener de test documenta el contrato para C-08.
- **R7** [`INSERT ... ON CONFLICT` con `JdbcTemplate` fuera del contexto de persistencia de JPA] → el paciente no se carga antes en esa transacción, así que no hay estado viejo en caché; se usa `getReferenceById` solo para el FK. `RegistroPacientesIT` lo verifica.
- **R8** [Jackson 3 podría serializar `OffsetDateTime` normalizado a UTC] → los IT comparan el texto exacto `...-03:00`; si Jackson lo normalizara, se formatea explícitamente en el DTO y se registra en "Desvíos del apply".
- **R9** [Carga de la demo: la disponibilidad hace 3 consultas por pedido] → índices existentes `(odontologo_id, dia_semana)`, `(odontologo_id, inicio)`; volumen de demo mínimo.

## Migration Plan

1. Rama `feat/c-07-disponibilidad-y-reserva-api` sobre `6659e83` (C-02). Si el PR de C-02 se mergea a `main` con cambios, rebasar antes del apply.
2. **Sin migraciones**: el esquema `V2` alcanza. Reservado `V7__` (y `V7_1__`, ...) si el apply descubriera una necesidad.
3. Sin dependencias nuevas en `build.gradle.kts`. Sin variables de entorno nuevas (usa `APP_RESERVA_MAX_DIAS` y `APP_TIMEZONE` existentes).
4. Rollback: revertir el merge; no hay cambios de esquema que deshacer.

## Open Questions

- PA-05 (sobrescribir el contacto de un DNI repetido o avisar al odontólogo): se mantiene la sobrescritura de la KB para la demo; no cambia specs ni tareas.
- PA-01 (horizonte de 60 días): ya es configurable con `APP_RESERVA_MAX_DIAS`; el valor final no cambia el diseño.

## Decisiones que requieren aprobación (ALTO)

1. **409 vs 422** (D4): `RN-TU-01` y `RN-PA-03` → 409; `RN-TU-03`, `RN-TU-11`, `RN-AG-05` → 422.
2. **Upsert nativo con `ON CONFLICT`** que además serializa reservas del mismo DNI y cierra la carrera del límite RN-PA-03 (D6).
3. **Token en claro solo en memoria dentro de `TurnoReservado`**, con la ventana de pérdida aceptada (D9).
4. **Respuesta 201** sin token, sin id de turno y con el email ingresado (D3).
5. **Perfil público** con `fechaMinima`/`fechaMaxima` del servidor, sin teléfono ni email del odontólogo (D3).
6. **Recomendación a C-03**: `permitAll` y exención de CSRF en `/api/public/**` (D10).
7. **Disponibilidad de fechas fuera de rango** → 200 con lista vacía, no error (D3).

## Aprobación ALTO

- Aprobación ALTO: aprobado por el usuario el 2026-11-21 (incluye los 7 ítems de "Decisiones que requieren aprobación").

## Desvíos del apply

1. **Email: una sola regla de formato** (D3). `SolicitudReserva.email` usa `@NotNull @Size(max = 255) @Pattern("^[^@\s]+@[^@\s]+\.[^@\s]+$")` y no `@Email` + `@Pattern`: con las dos reglas un email inválido producía dos detalles para el mismo campo y la spec pide un detalle por campo inválido. El mismo criterio (una regla por campo, `@NotNull` + `@Pattern`) se aplicó a `dni` y `telefono`. Los casos `luis@` y `a@b` siguen rechazados (`SolicitudReservaTest`).
2. **Bug de zona horaria encontrado en el smoke (7.2) y corregido: `shared.tiempo.ZonaDelProceso`.** Con `hibernate.jdbc.time_zone=UTC` (C-01) y el proceso Java en otra zona (la de Windows en la notebook, UTC-03:00), las columnas `time` de `horario_atencion` se leían desplazadas: la franja 09:00 de la base aparecía como 06:00 y la disponibilidad ofrecía horarios de madrugada. Los tests no lo veían porque Gradle fija `user.timezone=UTC`. Se agregó `ZonaDelProceso.fijarUtc()` (fija `TimeZone.setDefault(UTC)`) y `TurnoTardeApplication.main` lo llama antes de arrancar. Cubierto por `ZonaDelProcesoTest` y `HorarioAtencionZonaDelSistemaIT` (simula un proceso en Buenos Aires). **Aviso para C-05 y para quien despliegue con `java -jar`:** no hace falta ninguna opción `-Duser.timezone`; si alguien arranca la app sin pasar por `main` (por ejemplo, un test de contexto propio), el proceso debe estar en UTC.
3. **Candidatos "no superpuestos" en el test de DST (2.3).** La grilla de 15 minutos hace que los candidatos de una misma lista se pisen entre sí cuando la prestación dura más de 15 minutos (09:00 y 09:15 para 30 minutos); eso es normal y no es un turno doble. El test de `Europe/Madrid` 2027-03-28 afirma lista ordenada, sin duplicados, sin horarios en la hora inexistente (02:00 a 02:59) y con el fin de cada turno dentro de la franja (`01:00, 01:15, 01:30, 01:45, 03:00, 03:15, 03:30`).
4. **`ManejadorErrores` importa `turno.ReglasTurno`** (D8) para no duplicar el código y el texto de `RN-TU-01`; es la única dependencia de `shared.error` hacia `turno`.
5. **Firma de la reserva.** `ReservaService.reservar(Odontologo, Prestacion, Instant inicio, DatosPaciente)` devuelve el `ResumenTurno` del evento; `publico.PublicoService.reservar(slug, solicitud)` resuelve slug y prestación (404 uniforme) y arma el DTO `ReservaConfirmada` (con `OdontologoPublico`). Así `turno` no depende de `publico`. Como `@Valid` corre antes de resolver el slug, un cuerpo inválido a un slug inexistente responde 400 y no 404.
6. **Los IT nuevos limpian la base al terminar** (`@AfterEach` con `LimpiezaBase`): sin eso quedaba un odontólogo `demo` en `public` y fallaba `DatosDemoIT.lasTablasDePublicNoSeTocan` según el orden de ejecución.
7. **R8 no se materializó:** Jackson 3 serializa `OffsetDateTime` con el offset recibido (`-03:00`); no hizo falta formatear en el DTO.
8. **TDD, honestidad sobre el rojo:** `ReservaEventosIT` (5.5) y `ReservasConcurrentesApiIT` (6) pasaron en la primera corrida porque el evento y la guardia ya se habían implementado en 5.4. Para 5.5 se verificó el rojo comentando `publishEvent` (falla `ReservaEventosIT`) y restaurándolo. La concurrencia se corrió 3 veces más (5 + 5 repeticiones cada una) sin fallas.
9. **Smoke (7.2) contra la base de desarrollo `turnotarde`:** quedó un turno de prueba (paciente ficticio DNI `30111222`, `smoke@example.com`, martes 2026-11-24 09:00) en esa base; no hay endpoint para borrarlo.
10. **Corrección tras la verificación independiente — ReDoS en el email (hallazgo MAJOR).** La regex `^[^@\s]+@[^@\s]+\.[^@\s]+$` retrocedía de forma cuadrática: un email mal formado de 100 KB tardaba varios segundos de CPU por request en un endpoint público y sin rate limiting. Se reemplazó por `^(?=[^\s]{6,255}$)[^@\s]{1,64}@(?!.*\.\.)[^@\s.][^@\s]*\.[^@\s.]+$` (constante `SolicitudReserva.EMAIL`): el lookahead de largo corta cualquier entrada enorme y no hay grupos repetidos (que en `java.util.regex` recursan y desbordaban la pila con un primer intento de etiquetas acotadas). El largo máximo de 255 ahora vive en la regex y se quitó `@Size` del email, así un email demasiado largo sigue dando un único detalle por campo (antes daba dos). Además se rechazan los puntos dobles en el dominio (`a@b..c`, que antes se aceptaba). Tests nuevos en `SolicitudReservaTest`: emails comunes válidos, seis formatos inválidos, borde de 255/256 caracteres y una entrada de 100 KB que debe responder en menos de 1 s (antes: timeout).
11. **Corrección tras la verificación independiente — zona UTC fuera de `main` (hallazgo MINOR, seguimiento 1).** `ZonaDelProceso.fijarUtc()` ya no se llama desde `main`: lo aplica `shared.tiempo.ZonaUtcInicializador`, un `ApplicationContextInitializer` registrado en `META-INF/spring.factories`, que corre antes de crear cualquier bean en todo contexto de Spring Boot (`bootRun`, `java -jar` y `@SpringBootTest` lanzado desde el IDE, que no pasa por `main` ni por el `user.timezone` de Gradle). `ZonaUtcInicializadorTest` falla si se borra el registro (verifica `SpringFactoriesLoader` y los inicializadores de `SpringApplication`). Queda como seguimiento para un change posterior evaluar quitar `hibernate.jdbc.time_zone` (arreglo de raíz sugerido por la verificación).

## UI Mockups

- Design system revision: 2
- Decision: no (no UI) — 2026-11-21. C-07 es solo API; la UI de reserva es C-10 (con su propio mockup).
- Mockups: —
- Approval: —
- Preview: —
- Visual verify: —
