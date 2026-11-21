# Tasks — C-07 `disponibilidad-y-reserva-api`

> Governance ALTO: no empezar el apply hasta que el usuario apruebe `design.md` (sección "Decisiones que requieren aprobación"). Cada tarea es ≤ 2 h y sigue TDD: primero el test en rojo, después el código. Antes de codear, cargá `java-springboot`, `springboot-patterns` y `java-junit`. Sin migraciones (D-Migration Plan); sin `spring-boot-starter-security` (D10). Unitarios con `./gradlew test -PskipFrontend`, integración con `./gradlew integrationTest -PskipFrontend` contra `turnotarde_test`.

## 1. Contratos base: token, eventos y traducción de conflictos

- [x] 1.1 Escribir `TokenTurnoTest` (43 caracteres base64url sin padding que decodifican a 32 bytes, SHA-256 hex minúscula de un vector conocido, dos tokens distintos, `toString` sin el plano) y después implementar `turno.TokenTurno` (`generar()`, `hashear(String)`) según D7; verificar con `./gradlew test --tests '*TokenTurnoTest' -PskipFrontend` en verde.
- [x] 1.2 Escribir `EventosTurnoTest` (el texto de `TurnoReservado` y `TurnoReprogramado` no contiene el token) y crear en `turno.eventos` los records `ResumenTurno`, `TurnoReservado`, `TurnoCancelado`, `TurnoReprogramado` con el payload exacto de D9 y `toString` que oculta el token; verificar el test en verde.
- [x] 1.3 Escribir `ConflictoTurnosTest` y `GuardiaSolapamientoTest` (`23P01` + `turno_sin_solapamiento` → 409 `RN-TU-01`; `23P01` de otra constraint y `23505` → se relanza; `CannotAcquireLockException` y SQLState `40P01`/`55P03` → `RN-TU-01` en la guardia) e implementar `shared.error.ConflictoTurnos`, `turno.ReglasTurno` (códigos y mensajes de la tabla D4) y `turno.GuardiaSolapamiento` según D8; verificar los tests en verde.
- [x] 1.4 Ampliar `ManejadorErroresTest` vía `ControladorDePrueba` (violación `23P01 turno_sin_solapamiento` → 409 `RN-TU-01` sin el nombre de la constraint ni texto de la base en el cuerpo; otra `DataIntegrityViolationException` → 500 `ERROR_INTERNO`) y agregar el handler de `DataAccessException` al único `ManejadorErrores` (D8, sin advice nuevo); verificar el test en verde y que los escenarios existentes de `errores-api` siguen pasando.

## 2. Cálculo de disponibilidad (función pura)

- [x] 2.1 Escribir `CalculadoraDisponibilidadTest` en rojo con todos los escenarios de `specs/disponibilidad-turnos` (pasos de 15 min, cierre de franja, varias franjas, día sin franjas, borde exacto de 24 h, fecha pasada, día 60 y 61, bloqueo medio y de varios días, contiguos, turno activo y cancelado) y los casos de `evaluar` con su orden (`ANTICIPACION` > `HORIZONTE` > `FUERA_DE_AGENDA` > `OCUPADO`); verificar que compila y falla por falta de implementación.
- [x] 2.2 Implementar en `agenda` los records `Franja`, `Intervalo`, `AgendaDelDia`, el enum `MotivoRechazo` y `CalculadoraDisponibilidad` (`horariosLibres`, `evaluar`, un único predicado, aritmética en minutos, comparaciones en `Instant`, `anticipacionMinima` como parámetro) según D2; verificar `CalculadoraDisponibilidadTest` en verde.
- [x] 2.3 Agregar a `CalculadoraDisponibilidadTest` el caso DST con `Europe/Madrid` el 2027-03-28 (sin duplicados ni candidatos superpuestos, fin dentro de la franja) y ajustar si hace falta; verificar en verde y que `SinHoraDelSistemaTest` sigue pasando.

## 3. Consultas y servicio de disponibilidad

- [x] 3.1 Agregar al final de los repositorios los métodos de D1 (`findBySlugAndActivoTrue`, `findByIdAndOdontologoIdAndActivaTrue`, `findByOdontologoIdAndActivaTrueOrderByNombre`, `findByOdontologoIdAndDiaSemanaOrderByHoraInicio`, intervalos de bloqueos y de turnos activos que cruzan `[inicioDia, finDia)` proyectados a `Intervalo`, `contarReservadosFuturos`) con un IT `ConsultasAgendaIT` que cubra cruce de días, estados activos vs `CANCELADO` y aislamiento por odontólogo; verificar `./gradlew integrationTest --tests '*ConsultasAgendaIT' -PskipFrontend` en verde.
- [x] 3.2 Implementar `agenda.DisponibilidadService` (`horariosLibres(odontologoId, prestacion, fecha)` y `validarInicio(odontologoId, prestacion, inicio)` que lanza la `ReglaNegocioException` de `ReglasTurno` según `MotivoRechazo`) usando `ZonaNegocio` y `AppProperties.reservaMaxDias`, con límites del día por `atStartOfDay(zona)`; verificar con un IT `DisponibilidadServiceIT` (reloj fijo, datos con `FixturesJdbc`) que cubra un caso por motivo.

## 4. API pública de lectura

- [x] 4.1 Escribir `PerfilPublicoIT` y `DisponibilidadPublicaIT` (MockMvc, `@Import(ConfiguracionRelojDePrueba.class)`, sin sesión) con el contrato JSON exacto de D3 (nombres de campos, `inicio` con `-03:00`, `hora` `HH:mm`, `fechaMinima` 2026-11-26 y `fechaMaxima` 2027-01-24), prestaciones de baja ocultas, sin email del odontólogo, sin DNI/nombre/email/id de turno ajenos ni motivo de bloqueo, 404 `NO_ENCONTRADO` idéntico para slug inexistente, odontólogo inactivo y prestación ajena/de baja, y 400 por parámetros faltantes o `fecha` mal formada; verificar que fallan.
- [x] 4.2 Implementar `publico.PublicoService` (resolución de slug y prestación activos con 404 uniforme) y `publico.ControladorPublico` con los DTOs `record` de lectura (`PerfilPublico`, `PrestacionPublica`, `DisponibilidadPublica`, `HorarioLibre`) sin exponer entidades; verificar `PerfilPublicoIT` y `DisponibilidadPublicaIT` en verde (si Jackson normaliza el offset, formatear en el DTO y anotarlo en "Desvíos del apply", R8).

## 5. Reserva online

- [x] 5.1 Escribir `SolicitudReservaTest` con `Validator` (DNI 7/8 dígitos y rechazo de `12.345.678`, teléfono E.164 y rechazo de `3511234567`, email `luis@` y `a@b` inválidos, nombre/apellido vacíos o > 100) y crear el record `publico.SolicitudReserva` con las anotaciones y mensajes en voseo de D3; verificar en verde.
- [x] 5.2 Escribir `RegistroPacientesIT` (DNI nuevo crea, DNI repetido actualiza nombre/apellido/teléfono/email y `actualizado_en` con el reloj fijo sin tocar `creado_en`, mismo DNI en otro odontólogo crea otro paciente) e implementar `paciente.RegistroPacientes` con el `INSERT ... ON CONFLICT ON CONSTRAINT paciente_dni_por_odontologo_uk ... RETURNING id` de D6 vía `JdbcTemplate`; verificar en verde.
- [x] 5.3 Escribir `ReservaOnlineIT` (MockMvc) en rojo con los escenarios de `specs/reserva-online`: 201 con el cuerpo de D3 y turno `RESERVADO`/`ONLINE` de 12:00Z a 12:30Z, cuerpo sin token ni hash, `token_hash` hex de 64 y distinto entre reservas, 400 `VALIDACION` con `detalles` y sin efectos en la base, 400 por `inicio` sin offset, 422 `RN-TU-03` (reloj en 15:00Z, 11:45 del jueves), 422 `RN-TU-11`, 422 `RN-AG-05` (12:45 y 09:10), 409 `RN-TU-01` por horario ocupado con cuerpo solo `codigo`/`mensaje`, 404 por prestación de baja sin crear paciente, upsert (nuevo, repetido, otro odontólogo), contacto intacto tras un 409, 409 `RN-PA-03` con el tercer turno y los casos que no cuentan; verificar que falla.
- [x] 5.4 Implementar `turno.ReservaService` con el flujo y orden de locks de D5 (validación previa → upsert → límite → token → `saveAndFlush` dentro de `GuardiaSolapamiento`) y el endpoint `POST /api/public/{slug}/turnos` (201, DTO `ReservaConfirmada`); verificar `ReservaOnlineIT` en verde.
- [x] 5.5 Escribir `ReservaEventosIT` con un listener de test `@TransactionalEventListener(AFTER_COMMIT)` (un evento por reserva confirmada con id, inicio `2026-11-27T12:00:00Z`, origen `ONLINE`, email y `sha256(token) == token_hash`; turno visible desde otra conexión al recibirlo; ningún evento ante 409 `RN-TU-01` ni `RN-PA-03`) y publicar `TurnoReservado` con `ApplicationEventPublisher` dentro de la transacción en `ReservaService`; verificar en verde y que ningún log de la corrida contiene el token.

## 6. Concurrencia (test obligatorio de reglas duras)

- [x] 6.1 Escribir `ReservasConcurrentesApiIT` (`@SpringBootTest(webEnvironment = RANDOM_PORT)`, `RestClient`, dos hilos con `CountDownLatch`, `@RepeatedTest(5)`): dos DNIs distintos reservan el viernes 2026-11-27 09:00 a la vez → exactamente un 201 y un 409 con `{codigo: RN-TU-01, mensaje: "Ese horario acaba de ser tomado, elegí otro"}` y un único turno activo en la base; verificar 5/5 en verde y que `ReservasConcurrentesIT` de C-02 sigue en verde.
- [x] 6.2 Agregar a `ReservasConcurrentesApiIT` el caso `@RepeatedTest(5)` del mismo DNI con 1 turno futuro reservando dos horarios distintos a la vez → un 201 y un 409 `RN-PA-03`, 2 turnos futuros finales y sin `23505` ni 500; verificar 5/5 en verde (si falla, revisar D6 antes de tocar el test).

## 7. Verificación integral

- [x] 7.1 Correr `./gradlew clean build` desde `backend/` (incluye frontend, unitarios, ArchUnit e integración contra `turnotarde_test`) y verificar que termina en `BUILD SUCCESSFUL`; correr `openspec validate c-07-disponibilidad-y-reserva-api --strict` y verificar que es válido.
- [x] 7.2 Smoke manual con el perfil `demo` (`bootRun`): `GET /api/public/demo`, `GET /api/public/demo/disponibilidad?prestacionId=<id>&fecha=<un día hábil ≥ mañana+1>`, `POST /api/public/demo/turnos` con datos válidos → 201 y el mismo POST de nuevo → 409 `RN-TU-01`; verificar que las respuestas coinciden con los JSON de D3 y registrar cualquier diferencia en `design.md` §"Desvíos del apply".

## Workflow follow-up

- Avisar a C-03 (Marcos) la recomendación de D10 y a C-08 (Lauti), C-09 y C-10 (Jero) que los contratos de D3, D4 y D9 quedaron publicados.
- Pedir revisión ALTO del usuario antes del apply; registrar la aprobación en `design.md`.
- Tras el merge: `/opsx:archive c-07-disponibilidad-y-reserva-api` y marcar `[x]` C-07 en `CHANGES.md`.
