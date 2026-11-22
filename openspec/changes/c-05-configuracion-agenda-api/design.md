# Design — C-05 `configuracion-agenda-api`

## Context

Motivación y alcance: `proposal.md`. Requisitos: `specs/configuracion-agenda/spec.md`.

Estado que deja `main` (C-01, C-02, C-03, C-04, C-07 y C-08 archivados):

| Pieza | Estado actual |
|---|---|
| Stack | Spring Boot 4.1.2, Java 25, Gradle 9.9.1, Hibernate 7 con `ddl-auto=validate`, Jackson 3 (anotaciones `com.fasterxml.jackson.annotation`), Flyway 12 con `out-of-order: true`. |
| Esquema | `V2__esquema_base.sql` ya tiene `horario_atencion` (día ISO 1–7, `time`, CHECK `hora_inicio < hora_fin`, índice por odontólogo/día), `prestacion` (`duracion_minutos` múltiplo de 15, `activa`, UNIQUE por odontólogo+nombre) y `bloqueo` (`timestamptz`, CHECK `inicio < fin`, índice por odontólogo/inicio). `V3` agrega el índice de email; `V900` es seed de demo. |
| Entidades | `agenda.HorarioAtencion`, `agenda.Bloqueo` y `prestacion.Prestacion` existen con getters, auditoría con el `Clock` y `Prestacion.desactivar()` (RN-AG-04). Los repositorios Spring Data existen; C-07 agregó métodos al final de `PrestacionRepository`, `HorarioAtencionRepository` y `TurnoRepository`. |
| Seguridad | C-03 dejó una `SecurityFilterChain`: `/api/public/**` y `POST /api/auth/login` públicos, todo el resto de `/api/**` exige `ROLE_ODONTOLOGO`, CSRF `spa()` con header `X-XSRF-TOKEN`, sesión HTTP sin JWT. `shared/seguridad.OdontologoActual.id()` y `AislamientoOdontologo.exigir(Optional<T>)` son el contrato de aislamiento; `soporte.AutenticacionDePrueba` entrega sesión + CSRF en los IT. |
| Errores | `shared/error.ManejadorErrores` (`@RestControllerAdvice`) produce `{codigo, mensaje, detalles?}`; `ReglaNegocioException(codigo, mensaje, estado)` es la forma de rechazar por regla de negocio. Los 401/403 los escribe la cadena de filtros, con el mismo formato. |
| Tiempo | Bean `Clock` UTC inyectable y `ZonaNegocio` para interpretar/mostrar en `America/Argentina/Buenos_Aires`. ArchUnit `SinHoraDelSistemaTest` prohíbe `now()` sin reloj. `ZonaUtcInicializador` fija UTC antes de crear beans (desvío 11 de C-07). |
| Tests | `*Test` unitarios sin base; `*IT` contra PostgreSQL real `turnotarde_test` con `IntegracionTest`, `LimpiezaBase`, `FixturesJdbc` y `RelojDePrueba`. Nunca H2. |

Governance **MEDIO** (`CHANGES.md`): se implementa con checkpoints, sin aprobación previa de Estefi antes del apply. Es un change de backend puro: no hay gate de mockups.

## Goals / Non-Goals

**Goals:**

- Publicar los contratos HTTP de horarios, prestaciones y bloqueos para que C-06 y C-13 puedan implementarse en paralelo sin renegociar JSON.
- Cumplir RN-AG-01..04 y RN-SE-01 con el mismo aislamiento y formato de error que C-03/C-07.
- No tocar el esquema: reutilizar `V2` y dejar `V5__` reservado solo si el apply descubre una necesidad real.
- Cubrir con tests la atomicidad del reemplazo semanal, la baja lógica, el listado de turnos afectados y el aislamiento entre odontólogos.

**Non-Goals:**

- Cancelar turnos desde un bloqueo (C-12) o cargar turnos manuales (C-15). C-05 solo los lista.
- Reactivar prestaciones dadas de baja: no hay requisito en la v0.1; se hace con un alta nueva si hiciera falta.
- Modificar `ConfiguracionSeguridad`, los endpoints públicos de C-07 ni los eventos de C-07/C-08.
- UI, mockups, design system ni rutas del frontend (C-06 y C-13).
- Bloqueos periódicos, excepciones por fecha fuera de los bloqueos, o validación de solapamiento entre bloqueos: la KB no lo pide.
- Cambios de esquema por ahora. Si aparece uno, va en `V5__<descripcion>.sql` (`V5_1__` si hay más de una) sin editar migraciones ya mergeadas.

## Decisions

### D1 — Módulos y piezas nuevas

| Paquete | Clase | Rol |
|---|---|---|
| `agenda` | `ControladorHorarios`, `ServicioHorarios`, `ValidadorFranjas` | HTTP, transacción de reemplazo y validación pura de franjas. |
| `agenda` | `ControladorBloqueos`, `ServicioBloqueos` | HTTP, alta/consulta/borrado y listado de turnos afectados. |
| `agenda.dto` | `FranjaDto`, `PeticionHorario`, `HorarioDto`, `PeticionBloqueo`, `BloqueoDto`, `TurnoAfectadoDto` | Contratos `record`. |
| `prestacion` | `ControladorPrestaciones`, `ServicioPrestaciones` | HTTP y reglas de prestaciones. |
| `prestacion.dto` | `PeticionPrestacion`, `PrestacionDto` | Contratos `record`. |
| `shared.reglas` | `ReglasAgenda` | Fábrica de `ReglaNegocioException` con los códigos `RN-AG-01` y `RN-AG-03` y sus mensajes en voseo. |

`ReglasAgenda` vive en `shared` y no en `agenda` porque tanto `agenda` como `prestacion` la necesitan y ninguno debería depender del otro. Alternativa descartada: duplicar los mensajes en los dos módulos (divergencia garantizada). `shared.error` importa `turno.ReglasTurno` desde C-07; acá no hace falta tocar el advice.

Métodos nuevos se agregan **al final** de los repositorios existentes para minimizar conflictos:

- `HorarioAtencionRepository`: consulta por odontólogo ordenada por día/hora y borrado por odontólogo.
- `PrestacionRepository`: consulta por odontólogo ordenada por nombre, búsqueda por id+odontólogo, existencia por odontólogo+nombre.
- `BloqueoRepository`: consulta por odontólogo ordenada por inicio, búsqueda por id+odontólogo. La superposición de turnos se consulta en `TurnoRepository` (constructor expression a un record de proyección, nunca entidades en la API), reutilizando el predicado semiabierto que ya usa `intervalosActivosQueCruzan(...)`.
- `TurnoRepository`: proyección de turnos que cruzan un rango para `turnosAfectados` (constructor expression a `TurnoAfectadoProyeccion`, con `Instant`; el servicio convierte a `TurnoAfectadoDto` con offset). JPQL no puede construir `OffsetDateTime`. No se necesita `OdontologoRepository`: los constructores de `HorarioAtencion`, `Bloqueo` y `Prestacion` reciben `Long odontologoId` directo.

**Firmas existentes confirmadas (no cambian):** `HorarioAtencionRepository.findByOdontologoIdAndDiaSemanaOrderByHoraInicio(Long, short)`, `BloqueoRepository.intervalosQueCruzan(Long, Instant, Instant)` y `PrestacionRepository.findByIdAndOdontologoIdAndActivaTrue(Long, Long)` / `findByOdontologoIdAndActivaTrueOrderByNombre(Long)`. Se agregan métodos nuevos al final sin modificar esas firmas porque C-07 las consume.

### D2 — Contratos HTTP para C-06 y C-13

Todos bajo `/api/**`, `application/json`, sesión obligatoria y CSRF en `POST`/`PUT`/`DELETE`. Los instantes viajan con offset (por ejemplo `2026-11-27T10:00:00-03:00`), como en C-07; las horas de franja son `HH:mm` locales sin fecha. El servidor interpreta con `ZonaNegocio`; no se aceptan instantes sin offset, para no depender de la zona del dispositivo (RN-AG-06, RN-GL-02).

**Horarios**

`GET /api/horarios` y `PUT /api/horarios` → 200:

```json
{ "franjas": [
  { "diaSemana": 1, "inicio": "09:00", "fin": "13:00" },
  { "diaSemana": 1, "inicio": "16:00", "fin": "20:00" }
] }
```

El cuerpo del `PUT` es el mismo objeto. No se devuelven ids: el reemplazo semanal regenera las filas y obligar a C-06 a manejar ids sería ruido.

**Prestaciones**

`GET /api/prestaciones` → 200 (incluye inactivas para que la UI pueda mostrar el estado):

```json
{ "prestaciones": [
  { "id": 1, "nombre": "Consulta", "duracionMinutos": 30, "activa": true },
  { "id": 2, "nombre": "Limpieza", "duracionMinutos": 45, "activa": false }
] }
```

`POST /api/prestaciones` con `{ "nombre": "Consulta", "duracionMinutos": 30 }` → 201 con el DTO creado (`id` y `activa: true` incluidos). `PUT /api/prestaciones/{id}` con el mismo cuerpo → 200 con el DTO actualizado. `DELETE /api/prestaciones/{id}` → 204 sin cuerpo; no cambia `activa` a `true` en ningún caso.

**Bloqueos**

`GET /api/bloqueos?desde=&hasta=` (filtros opcionales con offset) y `POST /api/bloqueos` → 200/201:

```json
{ "bloqueos": [
  {
    "id": 7,
    "inicio": "2026-11-27T10:00:00-03:00",
    "fin": "2026-11-27T12:00:00-03:00",
    "motivo": "Control particular",
    "turnosAfectados": [
      { "id": 12, "inicio": "2026-11-27T10:30:00-03:00", "fin": "2026-11-27T11:00:00-03:00",
        "estado": "RESERVADO", "pacienteNombre": "Luis", "pacienteApellido": "Gómez",
        "prestacionNombre": "Consulta" }
    ]
  }
] }
```

`POST` recibe `{ "inicio", "fin", "motivo" }`; `motivo` es opcional y como máximo 255 caracteres. `DELETE /api/bloqueos/{id}` → 204 sin cuerpo. El `id` de `turnosAfectados` es el que C-12 usará para cancelar; no se exponen DNI, email, teléfono ni tokens.

### D3 — Errores: códigos, estados y orden de validación

| Situación | HTTP | `codigo` | `detalles` |
|---|---|---|---|
| Sin sesión / CSRF inválido | 401 / 403 | `NO_AUTENTICADO` / `ACCESO_DENEGADO` | — (lo escribe la cadena de filtros) |
| Campo obligatorio, formato de hora, día fuera de 1–7, duración ≤ 0, rango de bloqueo invertido | 400 | `VALIDACION` | `[{campo, mensaje}]` |
| JSON ilegible, parámetro de fecha con formato inválido | 400 | `SOLICITUD_INVALIDA` | — |
| Franja con inicio ≥ fin o franjas solapadas | 422 | `RN-AG-01` | `[{campo: "franjas[i]", mensaje}]` |
| Duración de prestación no múltiplo de 15 | 422 | `RN-AG-03` | `[{campo: "duracionMinutos", mensaje}]` |
| Nombre de prestación repetido para el odontólogo | 400 | `VALIDACION` | `[{campo: "nombre", mensaje}]` |
| Prestación o bloqueo ajeno/inexistente | 404 | `NO_ENCONTRADO` | — |
| Error inesperado | 500 | `ERROR_INTERNO` | — |

Los mensajes de negocio viven en `ReglasAgenda`; los técnicos, en `CodigosError`/`ManejadorErrores`. El 422 para reglas de agenda repite el criterio de C-07 (`RN-AG-05` también es 422): la solicitud está bien formada pero viola una regla de configuración. El 400 para rango de bloqueo invertido se debe a que la KB no le asigna un código `RN-AG` propio; el CHECK de `V2` queda como red de seguridad.

Orden de validación en `PUT /api/horarios`: Bean Validation del DTO → rango de cada franja → solapes dentro del día → escritura. Así el primer error reportado es determinista, aunque haya varios problemas a la vez.

### D4 — Reemplazo semanal atómico

`ServicioHorarios.reemplazar(...)` es `@Transactional`:

1. Resuelve el odontólogo con `OdontologoActual.id()` (nunca del request).
2. Valida la lista con `ValidadorFranjas` (función pura, sin Spring ni reloj). Ante error lanza `ReglasAgenda` con el índice de la franja culpable.
3. Borra las franjas vigentes de ese odontólogo y guarda las nuevas con `saveAll` + `flush`, para que cualquier falla de la base aparezca dentro del servicio y haga rollback de todo.

No hay constraint en la base para el solape de franjas: la KB dice explícitamente "lo valida el servicio" (RN-AG-01). La garantía de la base `turno_sin_solapamiento` es para turnos y no se toca. Dos `PUT` simultáneos pueden terminar con el último escritor: se acepta para la demo, se documenta como deuda y no afecta la seguridad ni la integridad referencial.

### D5 — Prestaciones: baja lógica y nombre repetido

`ServicioPrestaciones` resuelve cada recurso con `findByIdAndOdontologoId(id, OdontologoActual.id())` + `AislamientoOdontologo.exigir(...)`, así un id ajeno y uno inexistente dan el mismo 404.

- La regla de duración se chequea en el servicio (`% 15 == 0`) y responde 422 `RN-AG-03`; Bean Validation solo cubre obligatoriedad y positividad para no duplicar mensajes en el mismo campo.
- Antes de crear o renombrar se consulta `existsByOdontologoIdAndNombre`. Como esa consulta no cierra la carrera entre dos requests, `saveAndFlush` se envuelve en una guardia que traduce una violación `23505` de la UNIQUE de `prestacion (odontologo_id, nombre)` a 400 `VALIDACION` con `campo = "nombre"`, sin filtrar SQL. El nombre exacto de la constraint se toma de `V2` en el apply; cualquier otra violación se relanza y termina en 500 `ERROR_INTERNO`.
- `DELETE` llama a `Prestacion.desactivar()` (mutador que ya trae C-02), hace `flush` y responde 204. Si ya estaba inactiva, no cambia nada y también responde 204. `GET` incluye inactivas; el perfil público de C-07 sigue filtrando por `activa = true`.
- Cambiar la duración no reescribe turnos: RN-TU-02 queda intacto.

### D6 — Bloqueos y turnos afectados

`ServicioBloqueos` valida `inicio < fin` antes de persistir y resuelve el dueño desde la sesión. `crear(...)` guarda el bloqueo con `saveAndFlush` y luego consulta los turnos que se superponen con el rango: mismo odontólogo, estados `RESERVADO|ATENDIDO|AUSENTE` e intervalo semiabierto `[inicio, fin)` (`t.inicio < finDelBloqueo AND t.fin > inicioDelBloqueo`). El resultado se proyecta a un record intermedio con `Instant` ordenado por inicio y el servicio lo convierte con `ZonaNegocio.aLocal(...).toOffsetDateTime()` antes de exponer `TurnoAfectadoDto` (JPQL no puede construir `OffsetDateTime`).

La misma proyección se usa en el alta y en cada elemento de `GET /api/bloqueos`, para que C-13 pueda mostrar el listado también después de recargar. Es una consulta por bloqueo (patrón N+1), aceptada por el volumen minúsculo de la demo; la alternativa de un endpoint adicional agregaba un round trip a la UI y más contratos que mantener.

No se cancela nada ni se publican eventos: RN-AG-02 dice explícitamente que el odontólogo decide después. C-12 publicará `TurnoCancelado` cuando corresponda.

### D7 — Aislamiento y regresión de seguridad

No se modifica `ConfiguracionSeguridad`. Los DTOs no tienen campo `odontologoId`; si llega una propiedad extra en JSON se ignora (comportamiento por defecto de Jackson en el proyecto), y en todos los casos el id que se pasa a repositorios/servicios es `OdontologoActual.id()`. Las consultas de listado filtran por ese id y las de un recurso por `findByIdAndOdontologoId` + `exigir(...)`.

Un IT por módulo verifica: sin sesión → 401, escritura sin CSRF → 403, `odontologoId` ajeno en el body/query ignorado, recurso de otro odontólogo → 404 con el mismo cuerpo que un id inexistente. Se reutiliza `AutenticacionDePrueba` de C-03 para armar la sesión y el header CSRF.

### D8 — Estrategia de tests

**Unitarios (`./gradlew test -PskipFrontend`, sin base):**

| Clase | Cubre |
|---|---|
| `ValidadorFranjasTest` | Días 1/7 y bordes 0/8, horas iguales/invertidas, solapes, contiguos, misma hora en días distintos, orden de errores y mensajes con índice. |
| `ReglasAgendaTest` | Códigos `RN-AG-01`/`RN-AG-03`, estados 422 y que `detalles` no exponga SQL. |
| `PeticionPrestacionTest`, `PeticionHorarioTest`, `PeticionBloqueoTest` | Bean Validation con `Validator`: obligatorios, longitudes, formato `HH:mm`, rango de día, positividad; un detalle por campo inválido. |
| `ServicioPrestacionesTest` (Mockito) | Duración inválida, nombre repetido, baja idempotente, `exigir` ante recurso ajeno, sin mutación de turnos. |
| `ServicioBloqueosTest` (Mockito) | Rango invertido, proyección ordenada de afectados, estados incluidos/excluidos, dueño desde la sesión. |

**Integración (`./gradlew integrationTest -PskipFrontend`, PostgreSQL real `turnotarde_test`):**

| Clase | Cubre |
|---|---|
| `HorariosApiIT` | GET ordenado, PUT con varias franjas y días vacíos, reemplazo atómico ante franja inválida, solape rechazado 422, `odontologoId` ignorado, 401/403, 404 ajeno. |
| `PrestacionesApiIT` | Alta 201 con `activa: true`, edición 200, duración 20 → 422 `RN-AG-03`, nombre repetido → 400 `VALIDACION`, baja 204 con fila conservada, listado con inactivas, perfil público sin la dada de baja, aislamiento y CSRF. |
| `BloqueosApiIT` | Alta 201 con `turnosAfectados`, turno `RESERVADO` sigue activo y ocupando horario, cancelado no se lista, rango invertido 400, GET con el listado, DELETE 204, 404 ajeno, disponibilidad pública sin el rango bloqueado. |
| `AislamientoConfiguracionIT` | Los tres recursos de otro odontólogo devuelven 404 con el mismo cuerpo; el id del request no cambia el dueño. |
| `RegresionConfiguracionIT` | Los ITs públicos de C-07 (`PerfilPublicoIT`, `DisponibilidadPublicaIT`, `ReservaOnlineIT`) y de sesión de C-03 (`SesionIT`, `CsrfIT`) siguen en verde sin modificarlos. |

Todos los IT usan `LimpiezaBase` en `@BeforeEach`, reloj fijo por `ConfiguracionRelojDePrueba` cuando intervene una fecha, y MockMvc con filtros activos. Se corre la suite completa (`./gradlew build`) antes de archivar; no se omiten ni reemplazan tests.

## UI Mockups

- Design system revision: 2
- Decision: no (no UI) — 2026-11-22. C-05 expone solo API; las pantallas de configuración y agenda con sus mockups son C-06 y C-13.
- Mockups: —
- Approval: no aplica
- Preview: no aplica
- Visual verify: no aplica

## Risks / Trade-offs

- **R1** [Dos altas de prestación con el mismo nombre en paralelo burlan la preconsulta] → guardia de `23505` sobre la constraint de `V2` que traduce a 400 `VALIDACION`; cualquier otra integridad falla como 500 sin filtrar SQL.
- **R2** [El solape de franjas solo se valida en Java] → es lo que pide la KB (RN-AG-01); la disponibilidad de C-07 lee las mismas filas, así que no puede ofrecer horarios de una franja inválida rechazada. Queda como deuda agregar una constraint si el volumen de escritura lo justificara.
- **R3** [Dos reemplazos de horario concurrentes] → último escritor gana; se acepta para la demo (una sola cuenta real). No afecta turnos ni seguridad.
- **R4** [N+1 en `GET /api/bloqueos`] → volumen minúsculo (uno o dos bloqueos en la demo); se prefiere un solo contrato que ya trae `turnosAfectados` antes que otro endpoint.
- **R5** [Cambiar la duración de una prestación no reescribe turnos] → es RN-TU-02; C-06 debe avisar en su UI que los turnos existentes conservan la duración original.
- **R6** [Los tests con MockMvc pueden perder el CSRF o la sesión] → se usa `AutenticacionDePrueba` y se afirma explícitamente 401/403; si hiciera falta, se reparte con Tomcat real como en C-07.
- **R7** [Una base local ya migró `V7` y después llega `V5`] → `out-of-order: true` de C-02; si finalmente se necesita una migración, se agrega `V5__` nueva y nunca se editan `V2`/`V3`.

## Migration Plan

1. Rama `feat/c-05-configuracion-agenda-api`, creada desde `main` actualizado con C-01, C-02, C-03, C-04, C-07 y C-08.
2. Orden sugerido del apply: validadores y DTOs → repositorios → servicios → controladores → tests unitarios → ITs → verificación de regresión → README si hace falta documentar los endpoints.
3. **Sin migración de esquema:** `V2` ya da todas las columnas, CHECKs e índices. Si el apply descubre una necesidad, se crea `V5__<descripcion>.sql` en `db/migration` (o `V5_1__`, `V5_2__`); no se editan `V2`, `V3` ni ninguna migración mergeada, y se registra en "Desvíos del apply".
4. Rollback: revertir el código. No hay datos nuevos ni cambios de esquema que deshacer; si se agregó una `V5`, el procedimiento local es reiniciar el esquema con `DROP SCHEMA public CASCADE; CREATE SCHEMA public;` y volver a arrancar.
5. Después del merge: avisar a C-06 y C-13 que los contratos están en D2 y que toda escritura exige `X-XSRF-TOKEN`; recordar que `GET /api/prestaciones` incluye inactivas y que `turnosAfectados` viene en el mismo DTO del bloqueo.
