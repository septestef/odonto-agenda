# Design — C-02 `schema-y-seed-base`

## Context

Motivación y alcance en `proposal.md`; requisitos en `specs/` (`modelo-datos`, `turnos-sin-solapamiento`, `datos-demo` y el delta de `configuracion-entorno`).

Estado que deja C-01 (archivado el 2026-11-17, ver su `design.md` y "Desvíos del apply"):

| Pieza | Estado actual |
|---|---|
| Stack | Spring Boot 4.1.2 (Hibernate 7, Jackson 3), Java 25, Gradle 9.9.1. `spring-boot-starter-flyway` + `flyway-database-postgresql` (Flyway 12.4.0, acepta PostgreSQL 18.6). |
| JPA | `ddl-auto: validate`, `open-in-view: false`, `hibernate.jdbc.time_zone: UTC`. Sin entidades. |
| Flyway | `locations: classpath:db/migration` (solo `.gitkeep`). |
| Base | PostgreSQL 18 local. El rol `turnotarde` es **dueño** de `turnotarde` y `turnotarde_test`, así que puede `CREATE EXTENSION btree_gist` (extensión *trusted* desde PG 13). |
| Tests | `*Test` unitarios en `test`; `*IT` en `integrationTest` contra `turnotarde_test` (nunca H2), base `soporte.IntegracionTest` con chequeo previo de conexión. ArchUnit `SinHoraDelSistemaTest` rompe la build si el código de producción llama a `now()` sin `Clock`. |
| Tiempo | Bean `Clock` UTC en `shared.tiempo.ConfiguracionTiempo`; `ZonaNegocio` para convertir con `APP_TIMEZONE`. |
| Perfil demo | `application-demo.yml` solo ajusta logging y deja el marcador para el seed. |

Pendiente heredado de la verificación de C-01: el escenario "Mapeo que no coincide" de `configuracion-entorno` quedó **sin test automático** porque no había entidades. C-02 lo cubre (D10).

Governance **ALTO**: este documento se revisa antes del apply. Equipo de 6 personas trabajando en paralelo, 2 días de atraso sobre el camino crítico: el alcance se limita a CHANGES §C-02.

**Aprobación ALTO: aprobado por el usuario el 2026-11-21** (incluye D1, D6, D7 y D8).

## Goals / Non-Goals

**Goals:**
- Esquema completo y estable para que C-03, C-05, C-07, C-08 y C-14 trabajen sin tocar migraciones ajenas.
- RN-TU-01 garantizada por la base y demostrada con tests, incluido el de concurrencia.
- Una convención de migraciones que seis personas puedan seguir sin pisarse.
- Seed de demo reproducible, sin secretos en el repo.

**Non-Goals:**
- Servicios de negocio, DTOs, controladores o validaciones Bean Validation de entrada (C-05, C-07…). C-02 no expone nada por HTTP.
- Traducir la violación de `turno_sin_solapamiento` a 409 `RN-TU-01` (C-07, RN-GL-01).
- Spring Security, `PasswordEncoder` como bean, login (C-03).
- Validar en la base el formato de email o teléfono (lo hace Bean Validation en C-07, RN-PA-04).
- Métodos de dominio (cancelar, reprogramar, marcar atendido): los agrega el change que los usa.

## Decisions

### D1 — Numeración de migraciones por change y `out-of-order` (desvío de CHANGES.md)
- **Qué**: cada change usa **su propio número** como versión de Flyway: C-02 → `V2__esquema_base.sql`, C-07 → `V7__...`, C-14 → `V14__...`. Si un change necesita más de una migración: `V<NN>_1__`, `V<NN>_2__` (Flyway lee `V7_1` como versión 7.1). Las versiones **≥ 900 quedan reservadas para datos de demo** y viven en otra carpeta (D8). No existe `V1`: Flyway admite huecos.
- **`spring.flyway.out-of-order: true` en `application.yml` (base)**, así lo heredan el uso sin perfil (desarrollo), `test` y `demo`. Con seis personas mergeando en cualquier orden, una base local que ya aplicó `V7` tiene que poder aplicar después un `V5` recién integrado; sin `out-of-order`, Flyway lo rechaza en la validación.
- **Demo**: la demo corre sobre la base local `turnotarde`, la misma que se usó para desarrollar, así que necesita la misma tolerancia. **Producción**: no existe perfil `prod` en la v0.1. Cuando exista (post-demo), MUST poner `out-of-order: false` y consolidar el esquema con un baseline; queda anotado como deuda.
- **Por qué no `V1` como dice CHANGES.md**: con un número por change no hay que coordinar "quién toma el próximo número" ni renumerar al mergear, que es donde aparecen los choques en trabajo paralelo. `V2` es el número de C-02. Se registra como desvío y conviene corregir CHANGES.md (fuera del write set).
- **Reglas para el equipo** (van al README, tarea 7.1):
  1. Nunca editar una migración ya mergeada; se corrige con otra migración.
  2. Una migración posterior que agregue una columna `NOT NULL` a una tabla con datos de demo MUST traer `DEFAULT` o completar los datos, porque el seed `V900` puede haberse aplicado antes.
  3. Las versiones 900+ solo en `db/demo`.
- **Alternativas**: numeración por timestamp (`V20261121_1030__`): evita choques pero se lee peor y no indica el change; un único dueño de migraciones que serializa todo: cuello de botella con el atraso actual.

### D2 — Migración `V2__esquema_base.sql`
- `CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;` — `WITH SCHEMA public` fija dónde quedan los operadores, para que un test que migra en otro esquema (D9) no los "esconda" del resto.
- Convenciones: `id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY`; instantes en `timestamptz`; `creado_en timestamptz NOT NULL`, `actualizado_en timestamptz NOT NULL` **sin `DEFAULT now()`**: los completa la aplicación con el `Clock` (D5) y, si alguien se lo olvida, la inserción falla en vez de guardar la hora del servidor de base. FKs sin `ON DELETE CASCADE` (borrar en cascada turnos u odontólogos no es una operación del negocio).
- Tablas, tal cual `04_modelo_de_datos.md`, más estos CHECKs explícitos:

  | Tabla | Restricciones |
  |---|---|
  | `odontologo` | `UNIQUE(email)`, `UNIQUE(slug)`, `CHECK (slug ~ '^[a-z0-9-]+$')`, `activo DEFAULT true` |
  | `horario_atencion` | `CHECK (dia_semana BETWEEN 1 AND 7)`, `CHECK (hora_inicio < hora_fin)` |
  | `bloqueo` | `CHECK (inicio < fin)`, índice (`odontologo_id`, `inicio`) |
  | `prestacion` | `CHECK (duracion_minutos > 0 AND duracion_minutos % 15 = 0)`, `UNIQUE(odontologo_id, nombre)`, `activa DEFAULT true` |
  | `paciente` | `UNIQUE(odontologo_id, dni)`, `CHECK (dni ~ '^[0-9]{7,8}$')` |
  | `turno` | `CHECK (inicio < fin)`, `CHECK estado IN (...)`, `CHECK origen IN (...)`, `CHECK cancelado_por IN (...)` (NULL permitido), `token_hash char(64) NOT NULL UNIQUE`, `reprogramado_desde_id` FK a `turno`, índices (`odontologo_id`, `inicio`) y (`estado`, `inicio`), constraint `turno_sin_solapamiento` |
  | `notificacion` | `CHECK tipo IN (...)`, `CHECK estado IN (...)`; `tipo`, `destinatario`, `enviado_en`, `estado` `NOT NULL` |

- **Agregados respecto de la KB** (no cambian el modelo, lo endurecen): `CHECK` de formato de `dni` (la KB dice "solo dígitos, 7 u 8" pero no lo pone como constraint), `CHECK (inicio < fin)` en `turno` (sin él, `tstzrange` con extremos invertidos tira un error genérico en vez de uno nombrado) y `NOT NULL` en las columnas de `notificacion` (un registro de envío sin tipo ni destinatario no sirve para evitar duplicados).
- **Constraint central** (DD-03), literal de la KB:
  ```sql
  ALTER TABLE turno ADD CONSTRAINT turno_sin_solapamiento
    EXCLUDE USING gist (odontologo_id WITH =, tstzrange(inicio, fin, '[)') WITH &&)
    WHERE (estado IN ('RESERVADO','ATENDIDO','AUSENTE'));
  ```
  El rango semiabierto `[)` permite turnos contiguos (10:00–10:30 y 10:30–11:00). La violación llega a JDBC con SQLState `23P01` (`exclusion_violation`) y el nombre de la constraint en el mensaje: C-07 se apoya en ambos para responder 409 `RN-TU-01`. El nombre `turno_sin_solapamiento` es contrato con C-07 y no se cambia.

### D3 — Enums: `varchar` + `CHECK` + `@Enumerated(STRING)`
- **Qué**: `estado`, `origen`, `cancelado_por` (turno) y `tipo`, `estado` (notificación) son `varchar(20)`/`varchar(30)` con `CHECK ... IN (...)`, mapeados con `@Enumerated(EnumType.STRING)` a enums Java (`EstadoTurno`, `OrigenTurno`, `CanceladoPor`, `TipoNotificacion`, `EstadoNotificacion`) con los mismos nombres.
- **Por qué**: es lo que dice la KB (`varchar(20)`) y la constraint de solapamiento filtra por los literales. Un tipo `ENUM` de PostgreSQL exige `@JdbcTypeCode(NAMED_ENUM)`, complica `ALTER` para agregar valores y no aporta más que el `CHECK`. `ORDINAL` queda descartado: rompe si se reordena el enum.

### D4 — Entidades y repositorios
- **Ubicación** (paquetes de C-01): `odontologo.Odontologo`; `agenda.HorarioAtencion`, `agenda.Bloqueo`; `prestacion.Prestacion`; `paciente.Paciente`; `turno.Turno` + enums; `notificacion.Notificacion` + enums. Un `JpaRepository<X, Long>` por entidad en el mismo paquete. Visibilidad pública porque C-03/C-05/C-07 los usan desde sus servicios.
- **Relaciones**: `odontologoId` se mapea como `Long` simple en todas las entidades (el aislamiento RN-SE-01 filtra por id y nunca hace falta navegar al odontólogo). `Turno → Paciente`, `Turno → Prestacion` y `Notificacion → Turno` son `@ManyToOne(fetch = LAZY)`. `reprogramadoDesdeId` es `Long`. Sin colecciones `@OneToMany` (evitan cargas accidentales con `open-in-view: false`).
- **Tipos**: instantes `Instant`; franjas `LocalTime`; `dia_semana`/`duracion_minutos` `short`; `token_hash` `String` con `@JdbcTypeCode(SqlTypes.CHAR) @Column(length = 64)` para que `validate` acepte `char(64)` (riesgo R2).
- **API mínima**: constructor protegido sin argumentos (JPA), constructor público con los campos obligatorios, getters. Mutadores solo los que C-02 necesita: `Odontologo.cambiarPasswordHash(String)` (D7) y `Prestacion.desactivar()` (RN-AG-04, usado en el test de auditoría). Los demás (cancelar, reprogramar…) los agrega el change dueño de la regla.
- **Repositorios**: solo `OdontologoRepository.findBySlug(String)`, que usa D7 y después C-07. Cada change agrega sus consultas.
- Las entidades nunca salen por la API: C-02 no tiene controladores; los changes siguientes mapean a `record` DTOs.

### D5 — Auditoría con el `Clock` inyectado
- `shared.persistencia.EntidadAuditada`: `@MappedSuperclass` con `@EntityListeners(AuditingEntityListener.class)`, `@CreatedDate Instant creadoEn` (`updatable = false`) y `@LastModifiedDate Instant actualizadoEn`. Todas las entidades la extienden.
- `shared.persistencia.ConfiguracionAuditoria`: `@Configuration` con `@EnableJpaAuditing(dateTimeProviderRef = "proveedorFechaAuditoria")` y `@Bean DateTimeProvider proveedorFechaAuditoria(Clock reloj)` → `Optional.of(reloj.instant())`.
- **Por qué una `@Configuration` dedicada** y no en `TurnoTardeApplication`: si `@EnableJpaAuditing` está en la clase principal, los tests de slice (`@WebMvcTest` de `ManejadorErroresTest`) intentan levantar el contexto JPA y fallan. Por qué `DateTimeProvider`: sin él, Spring Data usa `CurrentDateTimeProvider` (hora del sistema), lo que viola la regla dura del `Clock` y haría no deterministas los tests. `SinHoraDelSistemaTest` sigue en verde porque nuestro código solo llama a `reloj.instant()`.
- **Alternativa descartada**: `@PrePersist`/`@PreUpdate` en la superclase: no puede recibir el `Clock` por inyección sin un holder estático.

### D6 — Dependencia BCrypt: `spring-security-crypto`, no el starter
- **Qué**: agregar `implementation("org.springframework.security:spring-security-crypto")` (versión del BOM de Boot 4.1.2) a `backend/build.gradle.kts`.
- **Por qué**: `spring-boot-starter-security` activa la autoconfiguración de seguridad y cierra todos los endpoints con un login por defecto: eso es de C-03 (governance CRÍTICO). `spring-security-crypto` trae solo `BCryptPasswordEncoder`, sin filtros ni autoconfiguración.
- **Coordinación con C-03 (Marcos)**:
  - El hash se guarda como BCrypt "pelado" (`$2a$10$…`), **sin** prefijo `{bcrypt}`. El `PasswordEncoder` de C-03 MUST ser `BCryptPasswordEncoder`, o un `DelegatingPasswordEncoder` con `setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder())`.
  - C-02 **no** declara un bean `PasswordEncoder` (sería de C-03); el inicializador de D7 usa una instancia propia. Cuando C-03 defina el bean, puede inyectarlo en el inicializador. Con el starter, `spring-security-crypto` llega transitivo y la línea explícita se puede quitar.
  - `password_hash varchar(100) NOT NULL` sobra para BCrypt (60 caracteres).

### D7 — Contraseña de demo: hash inutilizable en SQL + inicializador del perfil `demo`
- **Problema**: CHANGES pide "hash BCrypt generado al arranque desde `DEMO_PASSWORD`". Una migración SQL no puede leer variables de entorno de Spring ni calcular BCrypt (`pgcrypto` no está y sería otra extensión).
- **Qué**:
  1. `V900__seed_demo.sql` inserta al odontólogo `demo` con `password_hash = '!PENDIENTE-DEMO_PASSWORD'`: no tiene formato BCrypt, así que `BCryptPasswordEncoder.matches` siempre devuelve `false` (nadie puede entrar antes del paso 2).
  2. `odontologo.demo.PropiedadesDemo`: `@ConfigurationProperties("app.demo") @Validated record PropiedadesDemo(@NotBlank(message = "Falta DEMO_PASSWORD: definila en el entorno o en el .env para el perfil demo") String password)`, habilitada por `odontologo.demo.ConfiguracionDemo` (`@Configuration @Profile("demo") @EnableConfigurationProperties`). `application-demo.yml`: `app.demo.password: ${DEMO_PASSWORD:}`.
  3. `odontologo.demo.ContrasenaDemoInicializador` (`ApplicationRunner`, `@Profile("demo")`, transaccional): busca el odontólogo `demo` por slug (si no existe, falla el arranque con un mensaje claro); si el hash actual no verifica `DEMO_PASSWORD`, guarda `BCrypt(DEMO_PASSWORD)`. Loguea "Contraseña de demo aplicada" sin el valor.
- **Fail-fast**: sin `DEMO_PASSWORD`, la validación de `PropiedadesDemo` falla durante la creación del contexto y la aplicación no arranca; el reporte de Boot muestra el mensaje con el nombre de la variable y no muestra valores.
- **Por qué esta opción**: SQL legible para el resto del seed (horarios, prestaciones, turnos) y el único dato que depende del entorno queda en Java, donde hay `Environment`, validación y BCrypt. Además, cambiar `DEMO_PASSWORD` y reiniciar actualiza el hash (escenario de la spec), cosa que una migración que corre una sola vez no permite.
- **Alternativas**: (a) migración Java de Flyway (`V900__SeedDemo extends BaseJavaMigration`) como bean de Spring: todo el seed pasa a Java con `JdbcTemplate` (más código, menos legible) y corre una sola vez, así que no permite cambiar la contraseña; (b) placeholders de Flyway (`${demoPasswordHash}`) con el hash precalculado en una variable: obliga a calcular BCrypt a mano fuera de la app; (c) hash fijo en el SQL: prohibido por la regla dura.
- **Ventana**: entre la migración y el `ApplicationRunner` el odontólogo existe con un hash que no verifica nada (milisegundos, y el login de C-03 todavía no existe). Aceptado.

### D8 — Seed `V900__seed_demo.sql` solo con perfil `demo`
- **Ubicación**: `src/main/resources/db/demo/V900__seed_demo.sql`. `application-demo.yml` define `spring.flyway.locations: classpath:db/migration,classpath:db/demo`; sin el perfil, Flyway no ve esa carpeta. El seed corre una sola vez (historial de Flyway): reiniciar no duplica.
- **Identidades**: los inserts no fijan ids (`GENERATED ALWAYS`); las referencias se resuelven con subconsultas por `slug`, `dni` y nombre de prestación.
- **Contenido**: odontólogo `demo` (nombre y email ficticios, `demo@example.com`), 10 franjas (lun–vie, 09:00–13:00 y 16:00–20:00), las 4 prestaciones, 4 pacientes ficticios (`@example.com`, DNIs inventados, teléfonos E.164 de ejemplo) y estos turnos:

  | Día (hora AR) | Horario | Prestación | Estado / origen |
  |---|---|---|---|
  | Lun 23/11 | 09:00–09:30 | Consulta | RESERVADO / ONLINE |
  | Lun 23/11 | 16:00–16:45 | Limpieza | RESERVADO / MANUAL |
  | Mar 24/11 | 10:00–11:00 | Extracción | RESERVADO / ONLINE |
  | Mié 25/11 | 09:00–09:30 | Consulta | RESERVADO / ONLINE |
  | Mié 25/11 | 09:30–10:15 | Arreglo de caries | RESERVADO / ONLINE (contiguo al anterior) |
  | Mié 25/11 | 11:00–11:45 | Limpieza | CANCELADO por PACIENTE |
  | Jue 26/11 | 17:00–17:30 | Consulta | RESERVADO / MANUAL |

  Viernes 27 libre y casi todo el jueves 26 libre: durante la demo (miércoles 25) se puede reservar en vivo respetando las 24 h (RN-TU-03). Ningún paciente supera 2 turnos futuros `RESERVADO` (RN-PA-03).
- **Fechas fijas, no relativas al `Clock`**: los instantes se escriben como `timestamptz '2026-11-23 09:00 America/Argentina/Buenos_Aires'`. La KB pide turnos "la semana de la demo", que es una semana concreta; fechas fijas hacen el seed reproducible y verificable con fechas exactas. Calcularlas relativas al reloj exigiría un seed en Java (más código en un change atrasado) y la agenda cambiaría según el día de arranque. Contra: si se reinstala la base después del 27/11 los turnos quedan en el pasado (sin impacto: post-demo). RN-TU-03 no aplica al seed (no es una reserva online), pero las fechas igual están en el futuro respecto del día de hoy (21/11). Todos los turnos quedan `RESERVADO` o `CANCELADO`: `ATENDIDO`/`AUSENTE` exigen un inicio pasado (RN-TU-10).
- **`token_hash`**: `encode(sha256(uuid_send(gen_random_uuid())), 'hex')` (funciones nativas de PG 13+): un hash aleatorio cuyo token en claro nadie conoce, así que los links de esos turnos de ejemplo no se pueden usar. No hay tokens derivables del repo.
- **Auditoría en SQL**: `creado_en`/`actualizado_en` con un instante fijo (`'2026-11-21 00:00:00+00'`). La regla del `Clock` es para el código Java; un valor fijo además es determinista.

### D9 — Estrategia de tests (TDD contra `turnotarde_test`)
- **Aislamiento entre tests**: helper `soporte.LimpiezaBase` que hace `TRUNCATE ... RESTART IDENTITY CASCADE` de las 7 tablas en `@BeforeEach`. No se usa `@Transactional` con rollback porque el test de concurrencia necesita commits reales. Los ITs corren en serie (un solo fork de Gradle).
- **Reloj de prueba**: `soporte.RelojDePrueba` (un `Clock` mutable, fijable y avanzable) expuesto como `@Primary` desde una `@TestConfiguration` para los tests de auditoría.
- **Clases**:
  - `RestriccionesEsquemaIT` — tablas y columnas presentes, FKs, CHECKs (duración, franjas, bloqueo, `dni`, `slug`, estados, `inicio < fin`), UNIQUEs (prestación, DNI por odontólogo, email, slug, `token_hash`), sin columna de token en claro. Inserciones con `JdbcTemplate`; se espera `DataIntegrityViolationException`.
  - `TurnoSinSolapamientoIT` — los escenarios de `turnos-sin-solapamiento`: superposición, contenido, contiguos, otro odontólogo, cancelado superpuesto, cancelar libera, reactivar sobre ocupado. Verifica SQLState `23P01` y el nombre de la constraint.
  - `ReservasConcurrentesIT` — **test obligatorio**: dos hilos, cada uno con su transacción (`TransactionTemplate`) que hace `saveAndFlush` de un `Turno` superpuesto vía repositorio; arrancan juntos con un `CountDownLatch`; se repite 5 veces con datos limpios. Exactamente uno confirma, el otro recibe `DataIntegrityViolationException` con causa `23P01`, y queda un único turno activo. Usa el camino JPA porque es el que va a usar C-07.
  - `MapeoPersistenciaIT` — ida y vuelta de cada entidad por su repositorio (enums, `Instant`, `LocalTime`, `char(64)`), instante UTC estable con otra zona por defecto en la JVM, y auditoría con `RelojDePrueba` (alta y modificación).
  - `MapeoNoCoincideIT` — cierra el pendiente de C-01: una aplicación de prueba mínima en un paquete **fuera** de `ar.turnotarde` (`ar.pruebas.mapeoroto`: `@SpringBootConfiguration @EnableAutoConfiguration @EntityScan`) con una entidad `@Table(name = "tabla_que_no_existe")`; se levanta con `SpringApplicationBuilder` y perfil `test`, y se verifica que falla con `SchemaManagementException` que nombra la tabla. Fuera del paquete base para que ningún otro IT escanee esa entidad.
  - `MigracionesFueraDeOrdenIT` — verifica que la configuración efectiva de Flyway tiene `outOfOrder = true` y, usando esa misma configuración sobre un esquema descartable con migraciones de prueba (`V2`, `V7`, luego `V5` en `src/test/resources/db/prueba-orden/`), que `V5` se aplica.
  - `DatosDemoIT` — perfiles `test` + `demo` en un **esquema aparte** (`demo_seed`, borrado en `@BeforeAll`; `currentSchema=demo_seed,public`, `spring.flyway.schemas`/`default-schema` y `hibernate.default_schema` apuntando ahí) para no ensuciar el esquema `public` que usan los demás ITs ni dejar `V900` en su historial. Verifica odontólogo, 10 franjas, 4 prestaciones, turnos en la semana 23–27/11, uno cancelado, `@example.com`, hash BCrypt que verifica la contraseña de prueba, cambio de contraseña al reejecutar el inicializador, y que un segundo `migrate` no duplica. Otro caso levanta la app con perfil `demo` sin `app.demo.password` y espera el fallo que nombra `DEMO_PASSWORD`.
  - `ArranqueAplicacionIT` (existente) — `noHayTablasDeNegocioTodavia` se reemplaza por "existen las 7 tablas" y "sin perfil demo no se aplicó `V900`".
- **Orden TDD**: primero el test, se ve en rojo (no existe la tabla/entidad), después la migración o la clase.

### D10 — Archivos compartidos que toca C-02
| Archivo | Cambio | Dueño / aviso |
|---|---|---|
| `backend/build.gradle.kts` | + `spring-security-crypto` | C-02 es dueño; los demás piden dependencias. |
| `backend/src/main/resources/application.yml` | `spring.flyway.out-of-order: true` | Avisar al equipo (D1). |
| `backend/src/main/resources/application-demo.yml` | `spring.flyway.locations` con `db/demo`; `app.demo.password` | — |
| `backend/src/test/java/ar/turnotarde/ArranqueAplicacionIT.java` | reemplaza el test "sin tablas de negocio" | — |
| `README.md` | sección "Migraciones" con la convención de D1 y cómo correr la demo | — |
| `.env.example` | sin cambios (C-01 ya documentó `DEMO_PASSWORD`) | — |

## UI Mockups

- Design system revision: 2
- Decision: no (no UI) — 2026-11-21. C-02 solo agrega esquema, entidades, repositorios y seed; no hay pantallas ni estilos.
- Mockups: —
- Approval: no aplica
- Preview: no aplica
- Visual verify: no aplica

## Risks / Trade-offs

- **R1** [`out-of-order` oculta errores de orden: una migración vieja puede depender de algo que todavía no existe en otra base] → regla 1 y 2 de D1; cada change migra solo sus tablas; `MigracionesFueraDeOrdenIT` fija la configuración. Desactivarlo al crear un perfil de producción.
- **R2** [Hibernate 7 en `validate` puede rechazar `char(64)` contra `String` o `smallint` contra `short`] → `@JdbcTypeCode(SqlTypes.CHAR)` y tipos `short`; el arranque de cualquier IT lo detecta en la primera corrida. Si persiste, `@Column(columnDefinition = "char(64)")` y registrarlo en "Desvíos del apply".
- **R3** [El test de concurrencia puede pasar "por suerte" si los hilos no se solapan] → el resultado correcto (exactamente uno gana) vale para cualquier intercalado; se repite 5 veces y se verifica el SQLState, no solo la cantidad de filas.
- **R4** [`DatosDemoIT` con esquema aparte agrega configuración de test más compleja] → acotado a una clase; `btree_gist` fijado en `public` (D2) para que funcione en cualquier orden de ejecución.
- **R5** [Fechas fijas del seed quedan en el pasado después del 27/11] → aceptado (post-demo). Si se quiere un seed permanente, un change post-demo lo pasa a fechas relativas.
- **R6** [Desvío de CHANGES.md (`V2` en vez de `V1`) y CHANGES no menciona `out-of-order`] → registrado acá; actualizar CHANGES.md y avisar al equipo antes de que C-07 proponga su migración.
- **R7** [C-03 podría usar `DelegatingPasswordEncoder` por defecto y no reconocer el hash sin prefijo] → contrato explícito en D6; el login de demo lo detecta de inmediato.
- **R8** [Columnas de auditoría sin `DEFAULT`: los inserts de tests con `JdbcTemplate` tienen que pasarlas] → helper de fixtures en `soporte` con un instante fijo.

## Migration Plan

1. Rama `feat/c-02-schema-y-seed-base`. Bases locales ya creadas por C-01 (`backend/scripts/crear-bases-locales.sql`); nada nuevo que crear a mano.
2. Al arrancar, Flyway aplica `V2` en `turnotarde` (y `V900` si el perfil es `demo`). Para la demo: `.env` con `DEMO_PASSWORD`, `SPRING_PROFILES_ACTIVE=demo`, `./gradlew bootRun`.
3. **Rollback** en desarrollo: no hay `undo` en Flyway Community. Para volver atrás en una base local: `DROP SCHEMA public CASCADE; CREATE SCHEMA public;` como dueño de la base y volver a arrancar. No hay datos reales que preservar antes de la demo.
4. Si una base local ya tenía `V900` y se quiere reseedear: mismo procedimiento que el rollback.

## Open Questions

- ¿`reprogramado_desde_id` debería ser `UNIQUE` (un turno se reprograma una sola vez, como sugiere el ERD `0..1`)? No cambia las specs de C-02; se puede agregar en C-09 con su propia migración si hace falta.

## Desvíos del apply

- **Trigger de serialización en `V2` (nuevo, no estaba en D2)**: en la primera corrida de `ReservasConcurrentesIT`, 2 de 5 repeticiones fallaron porque la transacción perdedora recibió `40P01 deadlock_detected` (`CannotAcquireLockException`) en lugar de `23P01`. Con inserciones realmente simultáneas, las dos transacciones se esperan mutuamente al verificar la exclusion constraint y PostgreSQL aborta una por deadlock. La constraint sigue garantizando que gana exactamente una, pero la spec ("falla por `turno_sin_solapamiento`") y el contrato con C-07 (`23P01`) exigen un error determinista. Se agregó a `V2` el trigger `turno_serializar_por_odontologo` (`BEFORE INSERT OR UPDATE OF estado, inicio, fin, odontologo_id`, solo para estados activos) que toma `pg_advisory_xact_lock(hashtext('turno_sin_solapamiento'), hashtext(odontologo_id))`: la segunda transacción espera a que la primera confirme y recién entonces la constraint la rechaza con `23P01`. La constraint literal de la KB no cambia. Resultado: `ReservasConcurrentesIT` 5/5 en verde y 15 repeticiones más estables. **Alternativa descartada**: aceptar `23P01` o `40P01` en el test (debilita el escenario). **Aviso a C-07**: igual conviene mapear `CannotAcquireLockException`/`40P01` defensivamente a `409 RN-TU-01`.
- **Reinicio del esquema de test**: al editar `V2` (todavía sin mergear) se reinició el esquema `public` de `turnotarde_test` con un test temporal, ya borrado (procedimiento de la nota de `tasks.md`).
- **R4 (`DatosDemoIT` en esquema aparte)**: funcionó sin simplificar. El esquema `demo_seed` se logra con `currentSchema=demo_seed,public` en la URL (`public` queda en el `search_path` por los operadores de `btree_gist`), `spring.flyway.schemas`/`default-schema` y `hibernate.default_schema`. `spring.flyway.init-sqls` no sirve (separa por coma). La contraseña de prueba se genera con `UUID` en cada corrida; se inyecta como argumento de línea de comandos (`--app.demo.password=`) para que gane sobre cualquier `DEMO_PASSWORD` del entorno.
- **R2 (`char(64)`)**: no hizo falta la mitigación; `@JdbcTypeCode(SqlTypes.CHAR) @Column(length = 64)` pasa `ddl-auto=validate` con Hibernate 7.
- **Índices extra en `V2`** (no estaban en la tabla de D2): `horario_atencion_odontologo_idx (odontologo_id, dia_semana)` y `notificacion_turno_idx (turno_id, tipo)`, para las consultas de disponibilidad (C-05/C-07) y para evitar recordatorios duplicados (C-14).
- **Extras menores del apply**: `PropiedadesDemo.toString()` enmascara la contraseña; `ContrasenaDemoInicializador` loguea "ya vigente" cuando el hash ya verifica (no regraba); `ContrasenaDemoInicializadorTest` (unitario, Mockito sobre el repositorio) cubre el caso "no existe el odontólogo demo"; `MapeoPersistenciaIT` lee un turno cancelado insertado por JDBC porque `Turno` no tiene mutadores en C-02 (D4).
- **Prueba manual (4.4)** hecha con `SPRING_PROFILES_ACTIVE=demo` y una `DEMO_PASSWORD` descartable pasada solo como variable del proceso: el primer arranque aplicó `V2` y `V900` sobre `turnotarde` y "Contraseña de demo aplicada"; el segundo no reaplicó nada ("up to date") y ningún log contiene la contraseña. La base local `turnotarde` queda con los datos de demo y el hash de esa contraseña descartable: al arrancar con la `DEMO_PASSWORD` real, el inicializador lo reemplaza.
