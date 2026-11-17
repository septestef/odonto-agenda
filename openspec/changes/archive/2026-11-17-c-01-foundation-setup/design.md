# Design — C-01 `foundation-setup`

## Context

Repo greenfield: no hay `backend/` ni `frontend/`, ni repo git. Motivación y alcance en `proposal.md`; requisitos en `specs/` (`errores-api`, `tiempo-servidor`, `entrega-frontend`, `configuracion-entorno`).

Entorno verificado el 2026-11-17:

| Herramienta | Estado |
|---|---|
| JDK | Temurin 25.0.4.1 LTS (único JDK instalado) |
| Gradle | **No instalado** globalmente (hay una entrada `gradle-9.7.1` en el `PATH` que apunta a una carpeta inexistente) |
| Node / npm | 24.20.0 / 12.1.0 |
| PostgreSQL | 18, servicio `postgresql-x64-18` corriendo; `psql` fuera del `PATH` (`C:\Program Files\PostgreSQL\18\bin\psql.exe`) |
| Docker | No hay (PA-04) |

Restricciones que mandan sobre los defaults de las skills: las reglas duras de `AGENTS.md` (formato de error, `Clock`, sin H2, sin `any`, secretos por entorno). Governance **BAJO**.

## Goals / Non-Goals

**Goals:**
- Esqueleto compilable y testeable de backend y frontend que C-02 (schema) y C-04 (shell UI) puedan extender sin tocar la base.
- Piezas transversales de `shared/` listas para usar: `Clock`, zona horaria del negocio, excepción de regla de negocio y advice de errores.
- Pipeline de tests separado (unitarios / integración) y verificable con un solo `./gradlew build`.

**Non-Goals:**
- Tablas, entidades, seed (C-02). Spring Security, sesión, CSRF (C-03). Router, layout, estilos y uso de `tokens.css` (C-04). Spring Mail (C-08).
- Pipeline de CI, perfiles de producción, Docker.

## Decisions

### D1 — Spring Boot 4.1.x (4.1.2 al día de hoy) sobre Java 25
- **Qué**: fijar `org.springframework.boot` en **4.1.2** (la versión estable por defecto de start.spring.io el 2026-11-17; 4.2.0 está en RC). Toolchain Java 25.
- **Por qué**: la página oficial de requisitos de Boot 4.1.2 dice que soporta Java 17 hasta 27 inclusive, con Spring Framework 7.0.10+, y Gradle 8.14+ o 9.x. Es la línea estable más nueva con soporte OSS vigente; arrancar un greenfield en 3.5.x obligaría a migrar Jackson 2→3 y los starters modulares más adelante.
- **Alternativas**: 3.5.x (más material en la web, pero línea en fin de soporte OSS y migración diferida); 4.2.0-RC (no es GA, riesgo innecesario a 8 días de la demo).
- **Diferencias de Boot 4 que impactan el código** (las skills las detectan por el build):
  - Starters modulares: `spring-boot-starter-webmvc` (no `starter-web`), `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, **`spring-boot-starter-flyway`** (en Boot 4 la auto-configuración de Flyway necesita su starter, no alcanza con `flyway-core`) + `org.flywaydb:flyway-database-postgresql`, `org.postgresql:postgresql` (runtime).
  - Tests por módulo: `spring-boot-starter-webmvc-test` (MockMvc, `@WebMvcTest`, `@AutoConfigureMockMvc` en paquetes `org.springframework.boot.webmvc.test.autoconfigure`), `spring-boot-starter-test`. Verificar imports al compilar en vez de copiar ejemplos de Boot 3.
  - **Jackson 3** (`tools.jackson.*`, bean `JsonMapper`). Las anotaciones (`@JsonInclude`, etc.) siguen en `com.fasterxml.jackson.annotation`. Nada de `ObjectMapper` de Jackson 2.
  - Hibernate 7 / Jakarta EE 11.
- No se agrega `spring-boot-starter-security` en C-01 (lo trae C-03; agregarlo ahora cerraría todos los endpoints).

### D2 — Gradle 9.x por wrapper, obtenido desde Spring Initializr
- **Qué**: generar `backend/` desde start.spring.io (`type=gradle-project-kotlin`, `bootVersion=4.1.2`, `javaVersion=25`, `groupId=ar.turnotarde`, `artifactId=backend`, `packageName=ar.turnotarde`, deps `web,data-jpa,validation,flyway,postgresql`), que trae `gradlew`, `gradlew.bat` y `gradle/wrapper/*` con una versión de Gradle fijada. Después se ajusta `build.gradle.kts` (starters de Boot 4 de D1, ArchUnit, tareas de D8/D9) y se agrega `distributionSha256Sum` al `gradle-wrapper.properties` con el checksum publicado por Gradle para esa versión.
- **Por qué**: Gradle no está instalado; Initializr es fuente oficial de Spring y deja el wrapper consistente con la versión de Boot. Con un único JDK 25 instalado, el daemon de Gradle tiene que correr sobre Java 25: eso exige **Gradle ≥ 9.1**. La KB y `AGENTS.md` dicen "Gradle 8.x": queda como hallazgo para actualizar la KB (fuera del write set de este change).
- **Alternativas**: descargar la distribución binaria de Gradle 9.x de services.gradle.org y correr `gradle wrapper --gradle-version <x>` (válida si Initializr no está disponible; mismo resultado); copiar un wrapper de otro proyecto (sin trazabilidad, descartada).
- **Nota operativa**: bajar el zip es una descarga de red; en el apply hay que pedir confirmación al usuario antes de hacerla. El zip se extrae en una carpeta temporal y solo se copian los archivos necesarios a `backend/`.

### D3 — Formato de error propio, sin `ProblemDetail`
- **Qué**: un record `ErrorApi(String codigo, String mensaje, @JsonInclude(NON_EMPTY) Object detalles)` y **un solo** `@RestControllerAdvice` (`shared.error.ManejadorErrores`) que no extiende `ResponseEntityExceptionHandler` y maneja explícitamente:

  | Excepción | HTTP | `codigo` |
  |---|---|---|
  | `ReglaNegocioException` (base para C-02+: `codigo`, `mensaje`, `HttpStatus`, `detalles` opcional) | el de la regla | `RN-XX-NN` |
  | `MethodArgumentNotValidException`, `HandlerMethodValidationException` | 400 | `VALIDACION` (`detalles`: lista de `{campo, mensaje}`) |
  | `HttpMessageNotReadableException` | 400 | `SOLICITUD_INVALIDA` |
  | `NoResourceFoundException`, `NoHandlerFoundException` | 404 | `NO_ENCONTRADO` |
  | `HttpRequestMethodNotSupportedException` | 405 | `METODO_NO_PERMITIDO` |
  | `Exception` (resto) | 500 | `ERROR_INTERNO`, mensaje genérico; se loguea con SLF4J a nivel ERROR con stack trace |
- **Por qué**: la regla dura exige `{codigo, mensaje, detalles?}` en el nivel superior. `ProblemDetail` (sugerido por la skill `java-springboot`) serializa `type/title/status/detail` y obliga a meter `codigo` como propiedad extendida: más ruido y riesgo de que el frontend dependa de dos formatos. `spring.mvc.problemdetails.enabled` queda en `false` (default) y se documenta.
- **Códigos técnicos**: la KB solo define códigos `RN-*`. Para errores que no son reglas se introducen `VALIDACION`, `SOLICITUD_INVALIDA`, `NO_ENCONTRADO`, `METODO_NO_PERMITIDO`, `ERROR_INTERNO` (y `ERROR_DESCONOCIDO` solo en el frontend). Constantes en una clase `CodigosError`.
- **Límite conocido**: errores que ocurren fuera de Spring MVC (filtros de seguridad de C-03, errores del contenedor) no pasan por el advice y los resuelve `/error` de Boot con otro formato. C-03 debe mapear 401/403 con `AuthenticationEntryPoint`/`AccessDeniedHandler` que escriban `ErrorApi`. Queda anotado como riesgo.

### D4 — `Clock` UTC como bean y utilidad de zona del negocio
- **Qué**: `shared.tiempo.ConfiguracionTiempo` (`@Configuration`) expone `@Bean Clock clock()` = `Clock.systemUTC()`. `shared.tiempo.ZonaNegocio` (componente) recibe `Clock` y `AppProperties` y ofrece: `ZoneId zona()`, `LocalDate hoy()`, `ZonedDateTime aLocal(Instant)`, `Instant aInstante(LocalDate, LocalTime)`. Hibernate con `hibernate.jdbc.time_zone=UTC`.
- **Tests**: unitarios con `Clock.fixed(...)` construyendo `ZonaNegocio` a mano; en ITs, un `@TestConfiguration` con `@Primary Clock` fijo. Los tests no dependen de `TimeZone.getDefault()`: un test corre la conversión con la zona por defecto cambiada y la restaura en `finally`.
- **Guardia automática**: test ArchUnit (`*Test`, corre en `./gradlew test`) que prohíbe en `ar.turnotarde..` (excluyendo tests) las llamadas a `now()` sin argumentos de `Instant`, `LocalDate`, `LocalDateTime`, `LocalTime`, `ZonedDateTime`, `OffsetDateTime`, `System.currentTimeMillis()` y `new Date()`.
- **Alternativas**: grep en Gradle (frágil, falsos positivos en comentarios); revisión manual (no escala con 4 agentes en paralelo). Se elige ArchUnit (dependencia solo de test).

### D5 — Propiedades de la app tipadas
- `@ConfigurationProperties("app") record AppProperties(@NotNull ZoneId timezone, @NotNull URI baseUrl, @Min(1) int reservaMaxDias)` con `@Validated`, habilitado desde una `@Configuration` dedicada (no en la clase `@SpringBootApplication`). Mapeo en `application.yml`: `app.timezone: ${APP_TIMEZONE:America/Argentina/Buenos_Aires}`, `app.base-url: ${APP_BASE_URL:http://localhost:8080}`, `app.reserva-max-dias: ${APP_RESERVA_MAX_DIAS:60}`.
- Las variables de mail y `DEMO_PASSWORD` se documentan en `.env.example` pero no se consumen hasta C-08 / C-02.

### D6 — Variables de entorno y `.env`
- `application.yml` importa `spring.config.import: optional:file:.env[.properties],optional:file:../.env[.properties]` (cubre correr desde `backend/` o desde la raíz). Spring trata el `.env` como properties y sus claves resuelven los `${DB_URL}`, etc. Las variables reales del entorno tienen precedencia.
- Datasource: `url: ${DB_URL}`, `username: ${DB_USER}`, `password: ${DB_PASSWORD}` sin defaults para la contraseña. Sin logs de propiedades sensibles (no se loguea el datasource ni se activa `debug` por defecto).
- `.env.example` (raíz): las 13 variables de la KB §Variables de entorno, más `TEST_DB_URL`, `TEST_DB_USER`, `TEST_DB_PASSWORD`. Sensibles vacías.
- `.gitignore` (raíz): `.env`, `backend/build/`, `backend/.gradle/`, `backend/src/main/resources/static/`, `frontend/node_modules/`, `frontend/dist/`, `frontend/coverage/`, IDE (`.idea/`, `*.iml`, `.vscode/` salvo settings compartidos), logs. `!.env.example`.
- Perfiles: `application.yml` (base), `application-demo.yml` (por ahora solo `logging` legible y marcador para el seed de C-02), `src/test/resources/application-test.yml` (datasource de test).

### D7 — Persistencia: Flyway sin tablas, `ddl-auto=validate`
- `spring.flyway.enabled=true`, `locations=classpath:db/migration` con `.gitkeep` (Flyway ignora archivos no `.sql`); sin migraciones en C-01, solo se crea `flyway_schema_history`. `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false`.
- **PostgreSQL 18 y Flyway**: si la versión de Flyway gestionada por Boot 4.1.2 no reconoce PG 18, emite una advertencia o falla. Mitigación en la tarea correspondiente: probar el arranque; si falla, subir `flyway.version` dentro de lo que admita Boot y registrarlo.

### D8 — Tests de integración contra PostgreSQL local (resuelve PA-04)
- **Sin Docker → sin Testcontainers; nunca H2** (regla dura). Base `turnotarde_test` en el servicio `postgresql-x64-18`.
- **Creación**: script versionado `backend/scripts/crear-bases-locales.sql`, que se corre una vez como superusuario:
  `"C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -f backend/scripts/crear-bases-locales.sql`.
  Usa `\prompt` para pedir la contraseña del rol `turnotarde` (no queda escrita en ningún archivo), crea el rol si no existe y las bases `turnotarde` y `turnotarde_test` con dueño `turnotarde` (así C-02 puede `CREATE EXTENSION btree_gist`, que es *trusted* desde PG 13). Idempotente con `\gexec`.
- **Configuración**: `application-test.yml` → `url: ${TEST_DB_URL:jdbc:postgresql://localhost:5432/turnotarde_test}`, `username: ${TEST_DB_USER:${DB_USER:turnotarde}}`, `password: ${TEST_DB_PASSWORD:${DB_PASSWORD:}}`. Los ITs activan `@ActiveProfiles("test")`.
- **Si falta la base**: clase base `IntegracionTest` (abstracta) con un `@BeforeAll` que abre una conexión JDBC con esas propiedades y, si falla, lanza `IllegalStateException` con el mensaje: base, URL (sin contraseña) y el comando del script. Los tests fallan, nunca se saltean.
- **Gradle**: `test` corre `**/*Test.class` (excluye `*IT`); tarea `integrationTest` (tipo `Test`, mismo source set, `include("**/*IT.class")`, `shouldRunAfter(test)`), y `check` depende de ella → `./gradlew build` ejecuta ambas. Ambas con `useJUnitPlatform()` y `systemProperty("user.timezone", "UTC")` para no heredar la zona de Windows.

### D9 — Frontend: Vite + TS strict + Vitest + ESLint
- Scaffold con `npm create vite@latest frontend -- --template vanilla-ts` (versiones estables actuales, compatibles con Node 24; la KB pide "Vite 5.x o superior"). Se quitan los archivos de ejemplo (contador, logo).
- `tsconfig.json`: `strict: true`, `noUncheckedIndexedAccess`, `noImplicitOverride`, `noUnusedLocals`, `noUnusedParameters`.
- **"Sin `any`"**: `strict` no prohíbe el `any` explícito, así que se agrega ESLint (flat config) + `typescript-eslint` con `@typescript-eslint/no-explicit-any: error`. Scripts: `dev`, `build` (`tsc --noEmit && vite build`), `test` (`vitest run`), `lint`.
- `vite.config.ts`: `server.proxy['/api'] = { target: 'http://localhost:8080', changeOrigin: false }`; `test.environment = 'node'` (sin DOM en C-01).
- `src/shared/error-api.ts`: `interface ErrorApi { codigo: string; mensaje: string; detalles?: unknown }` y `function interpretarErrorApi(cuerpo: unknown): ErrorApi` con validación de tipos; ante cualquier forma inválida devuelve `{ codigo: 'ERROR_DESCONOCIDO', mensaje: 'Ocurrió un error inesperado. Probá de nuevo en unos minutos.' }`. Con tests Vitest. Base para el `api-client.ts` de C-04.
- `index.html` mínimo (`lang="es-AR"`, título "OdontoAgenda", un `<main id="app">`) sin estilos propios: los estilos y `tokens.css` llegan en C-04 (regla de no hardcodear valores visuales).

### D10 — `copyFrontend` y fallback de la SPA
- **Gradle** (`backend/build.gradle.kts`):
  - `npmInstall` (`Exec`, `npm ci`, en `../frontend`, inputs `package-lock.json`, output `node_modules/.package-lock.json`) y `npmBuild` (`Exec`, `npm run build`, inputs `src/`, `index.html`, configs; output `dist/`). En Windows el ejecutable es `npm.cmd`: se elige según `OperatingSystem`/`os.name`.
  - `copyFrontend` (`Sync`) de `../frontend/dist` a `src/main/resources/static` (carpeta generada e ignorada, como dice la KB).
  - `processResources` depende de `copyFrontend`, salvo con `-PskipFrontend` (útil para iterar solo el backend). Gracias a inputs/outputs, Gradle lo marca *up-to-date* si el frontend no cambió.
- **Fallback**: `shared.web.ConfiguracionSpa` (`WebMvcConfigurer`) registra un handler de recursos `/**` sobre `app.frontend.ubicacion` (default `classpath:/static/`) con un `PathResourceResolver` propio: si el recurso pedido existe lo devuelve; si no existe y el path no empieza con `api/`, devuelve `index.html`; para `api/**` devuelve `null` → `NoResourceFoundException` → 404 `NO_ENCONTRADO` del advice.
- **Por qué un resolver y no un controlador con regex**: con `PathPatternParser` (default en Boot 4) no se puede combinar `/**` con una regex que excluya `api`, y el resolver respeta los archivos estáticos reales sin listas de extensiones.
- **Testabilidad**: los ITs apuntan `app.frontend.ubicacion` a `classpath:/spa-prueba/` (fixture `src/test/resources/spa-prueba/index.html` y un `assets/app.js`), así no dependen de que se haya corrido el build de Vite.

### D11 — Estructura de paquetes
`ar.turnotarde.TurnoTardeApplication` y paquetes `shared` (`error`, `tiempo`, `web`, `config`), `auth`, `odontologo`, `agenda`, `prestacion`, `paciente`, `turno`, `publico`, `notificacion`. Los paquetes de negocio se crean con un `package-info.java` cada uno (Java no versiona carpetas vacías y documenta el propósito). Inyección por constructor, sin `@Autowired` en campos.

## UI Mockups

- Design system revision: 2
- Decision: no (no UI) — 2026-11-17. C-01 solo crea el scaffold de Vite con un `index.html` sin pantallas ni estilos; las pantallas y el uso de `tokens.css` empiezan en C-04.
- Mockups: —
- Approval: no aplica
- Preview: no aplica
- Visual verify: no aplica

## Risks / Trade-offs

- [Boot 4 es reciente; muchos ejemplos de la web son de Boot 3 (paquetes de test, `starter-web`, Jackson 2)] → D1 lista las diferencias; las tareas verifican compilando y corriendo tests, no copiando snippets.
- [Descarga del proyecto de Initializr / distribución de Gradle] → requiere confirmación del usuario en el apply; alternativa documentada en D2. Se agrega `distributionSha256Sum`.
- [Flyway de Boot 4.1.2 vs PostgreSQL 18] → probar el arranque en la tarea de persistencia; override de versión si hace falta.
- [La base de test no existe o la contraseña no coincide] → script idempotente + mensaje explícito de la clase base; los tests fallan (no se saltean).
- [`./gradlew build` ahora depende de npm] → `-PskipFrontend` para iterar backend; inputs/outputs para que sea incremental.
- [`copyFrontend` escribe dentro de `src/main/resources/static`] → está en `.gitignore`; se hace con `Sync` para no dejar archivos viejos.
- [Errores fuera de MVC no usan `ErrorApi`] → queda a cargo de C-03 (entry point y access denied handler); anotado en D3.
- [Códigos técnicos no-RN no están en la KB] → se definen en `CodigosError` y se documentan en el README; sugerir sumarlos a `05_reglas_de_negocio.md`.
- [KB dice Gradle 8.x, se usa 9.x] → hallazgo para actualizar `02_descripcion_general.md` y `AGENTS.md` fuera de este change.

## Migration Plan

No hay nada previo que migrar. Arranque local documentado en `README.md`: crear bases con el script, copiar `.env.example` a `.env` y completarlo, `cd backend && ./gradlew bootRun` (demo, con frontend embebido) o `npm run dev` en `frontend/` + `bootRun -PskipFrontend` (desarrollo). Rollback: borrar `backend/` y `frontend/`.

## Open Questions

- Ninguna que cambie specs o tareas. La versión exacta de Gradle 9.x la fija Initializr al generar el proyecto (se registra en la tarea correspondiente).

## Desvíos del apply

Registrados al aplicar el change (2026-11-17):

- **`.env` y el directorio de trabajo (D5/D6/D8)**: `./gradlew` corre desde `backend/`, así que `application.yml` importa `optional:file:.env[.properties],optional:file:../.env[.properties]` (ya previsto en D6) y, para los tests, `application-test.yml` resuelve `TEST_DB_PASSWORD` → `DB_PASSWORD` desde ese mismo `.env` de la raíz (el directorio de trabajo de `test`/`integrationTest` es `backend/`). Verificado: los ITs y `bootRun` toman la contraseña del `.env` raíz sin que aparezca en logs.
- **`TEST_DB_PASSWORD` vacía pisa el fallback**: una variable definida pero vacía no activa el valor por defecto de `${TEST_DB_PASSWORD:${DB_PASSWORD:}}`. Por eso en `.env.example` la línea `TEST_DB_PASSWORD` va comentada (se descomenta solo si la base de test usa otra contraseña).
- **Versión de Gradle y Flyway**: Initializr fijó Gradle **9.9.1** (con `distributionSha256Sum`). Flyway 12.4.0 (gestionado por Boot 4.1.2) acepta PostgreSQL 18.6: no hizo falta la mitigación de D7.
- **TypeScript 6.0.3 en vez de 7.x**: `create-vite` 9.3.4 generó `typescript ~7.0.2`, pero `typescript-eslint` 8.73 exige `typescript <6.1` y necesita la API JS del compilador. Se fijó `typescript ~6.0` (Vite 8.3, Vitest 5.1, ESLint 10 sin cambios). Revisar cuando `typescript-eslint` soporte TS 7.
- **Chequeo de la base de test en `@BeforeAll` estático**: `IntegracionTest` resuelve el entorno con `ConfigDataEnvironmentPostProcessor.applyTo` (mismos `application*.yml` y `.env`) antes de que Spring levante el contexto, para que el mensaje de ayuda salga en lugar de un fallo opaco de Flyway/Hikari.
- **`ManejadorErrores` cubre más excepciones que la tabla de D3**: además de las listadas maneja `MissingServletRequestParameterException` y `MethodArgumentTypeMismatchException` (400 `SOLICITUD_INVALIDA`) y `HttpMediaTypeNotSupportedException` (415 `SOLICITUD_INVALIDA`), para que errores del cliente no caigan en 500. `HandlerMethodValidationException` cubre además `@RequestParam` con restricciones.
- **Raíz `/` de la SPA**: con `spring.web.resources.add-mappings=false` (para que solo actúe el handler propio de D10) la raíz se resuelve con un `ViewController` `forward:/index.html`. MockMvc no ejecuta forwards: `EntregaSpaIT` verifica el destino del forward y que `/index.html` responda; el `GET /` real se comprobó con `bootRun` + `curl` (tarea 9.2).
- **`tasks.registering(Type::class)` deprecado en Gradle 9.6**: las tareas se declaran con `tasks.register<Type>("nombre")`.
- **Logs de `bootRun`**: la JVM de `bootRun` usa la zona de Windows para el formato del log (no afecta al `Clock`, que es UTC); si molesta, agregar `-Duser.timezone=UTC` a `bootRun` en un change posterior.
