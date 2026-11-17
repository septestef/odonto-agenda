# Tasks — C-01 `foundation-setup`

> Specs: `specs/errores-api`, `specs/tiempo-servidor`, `specs/entrega-frontend`, `specs/configuracion-entorno`. Diseño: `design.md` (D1–D11). Cada tarea ≤ 2 h. Donde aplica, el test se escribe primero (rojo) y después el código (verde).

## 1. Repo y entorno local

- [x] 1.1 Crear `.gitignore` en la raíz según D6 (incluye `.env`, `backend/build/`, `backend/.gradle/`, `backend/src/main/resources/static/`, `frontend/node_modules/`, `frontend/dist/`, `!.env.example`) y verificar leyéndolo que cubre cada ruta generada listada en D6/D10
- [x] 1.2 Crear `.env.example` en la raíz con las 13 variables de `08_arquitectura_propuesta.md` §Variables de entorno más `TEST_DB_URL`, `TEST_DB_USER`, `TEST_DB_PASSWORD`, con las sensibles vacías y un comentario por variable; verificar que no tenga ningún valor sensible cargado
- [x] 1.3 Escribir `backend/scripts/crear-bases-locales.sql` (D8: `\prompt` para la contraseña, rol `turnotarde`, bases `turnotarde` y `turnotarde_test` con dueño `turnotarde`, idempotente con `\gexec`); correrlo con `"C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -f backend/scripts/crear-bases-locales.sql` (lo corre el usuario, que tipea las contraseñas) y verificar que `psql -U turnotarde -d turnotarde_test -c "select 1"` responde; correrlo dos veces para comprobar que es idempotente

## 2. Esqueleto del backend (Gradle + Boot 4.1.2 + Java 25)

- [x] 2.1 **Pedir confirmación al usuario** y descargar de start.spring.io el proyecto `gradle-project-kotlin`, Boot `4.1.2`, Java `25`, `ar.turnotarde` / `backend`, deps `web,data-jpa,validation,flyway,postgresql` (D2) a una carpeta temporal; copiar a `backend/` y verificar que `gradle/wrapper/gradle-wrapper.properties` fija Gradle ≥ 9.1 (si no, usar la alternativa de D2) y anotar la versión en el README
- [x] 2.2 Agregar `distributionSha256Sum` al `gradle-wrapper.properties` con el checksum oficial de esa versión de Gradle y verificar con `./gradlew --version` (desde `backend/`) que descarga, valida y muestra JVM 25
- [x] 2.3 Ajustar `build.gradle.kts` a D1: toolchain 25, starters de Boot 4 (`starter-webmvc`, `starter-data-jpa`, `starter-validation`, `starter-flyway` + `flyway-database-postgresql`, driver PostgreSQL), test (`starter-test`, `starter-webmvc-test`, ArchUnit JUnit 5); verificar con `./gradlew dependencies --configuration runtimeClasspath` que aparecen Spring Boot 4.1.2, Jackson 3 (`tools.jackson`) y que no hay H2 ni `starter-security`
- [x] 2.4 Configurar en `build.gradle.kts` las tareas `test` (excluye `*IT`) e `integrationTest` (solo `*IT`, `shouldRunAfter(test)`, `check` depende de ella), ambas con `useJUnitPlatform()` y `user.timezone=UTC` (D8); verificar con `./gradlew tasks --all` que existe `integrationTest` y que `check` la incluye (`./gradlew check --dry-run`)
- [x] 2.5 Crear `TurnoTardeApplication` y los paquetes `shared` (`error`, `tiempo`, `web`, `config`) y `auth`, `odontologo`, `agenda`, `prestacion`, `paciente`, `turno`, `publico`, `notificacion` con `package-info.java` que documenta cada uno (D11); borrar el test de ejemplo de Initializr y verificar con `./gradlew compileJava -PskipFrontend`

## 3. Configuración, perfiles y persistencia

- [x] 3.1 Escribir `application.yml` (D5–D7: `spring.config.import` de `.env`, datasource por `DB_*`, `ddl-auto=validate`, `open-in-view=false`, `hibernate.jdbc.time_zone=UTC`, Flyway en `classpath:db/migration`, `app.*`), `application-demo.yml` y `src/main/resources/db/migration/.gitkeep`; verificar que no hay ninguna contraseña literal con una búsqueda de `password:` en `src/`
- [x] 3.2 Test primero: `AppPropertiesTest` (unitario, binder sobre un `MapConfigurationPropertySource`) que valida defaults (`America/Argentina/Buenos_Aires`, `60`, `http://localhost:8080`) y que una zona inválida falla; implementar `AppProperties` + `@Configuration` que lo habilita (D5) y verificar con `./gradlew test -PskipFrontend`
- [x] 3.3 Crear `src/test/resources/application-test.yml` (D8) y la clase base abstracta `IntegracionTest` con el chequeo de conexión en `@BeforeAll` y su mensaje de ayuda; verificar el escenario "Base de test ausente" corriendo una vez con `TEST_DB_URL` apuntando a una base inexistente: el IT falla con el mensaje que nombra la base y el script
- [x] 3.4 Test primero: `ArranqueAplicacionIT` (`@SpringBootTest`, perfil `test`) que verifica que el contexto levanta, que existe `flyway_schema_history` en `turnotarde_test` y que no hay tablas de negocio; verificar con `./gradlew integrationTest -PskipFrontend` (si Flyway rechaza PostgreSQL 18, aplicar la mitigación de D7 y registrarla en design.md)

## 4. Tiempo del servidor (`shared.tiempo`)

- [x] 4.1 Test primero: `ZonaNegocioTest` con `Clock.fixed` cubriendo los escenarios de `specs/tiempo-servidor` (09:00 local → `12:00Z`; `23:30Z` → 20:30 local; `hoy()` con `2026-11-26T01:00Z` → 2026-11-25; resultado igual con otra `TimeZone` por defecto, restaurada en `finally`); implementar `ConfiguracionTiempo` (bean `Clock.systemUTC()`) y `ZonaNegocio` (D4); verificar con `./gradlew test -PskipFrontend`
- [x] 4.2 Escribir `RelojInyectadoIT` que reemplaza el `Clock` por uno fijo (`@TestConfiguration` + `@Primary`) y verifica que `ZonaNegocio` del contexto devuelve ese instante, y que el `Clock` por defecto (sin override, en `ArranqueAplicacionIT`) está en `ZoneOffset.UTC`; verificar con `./gradlew integrationTest -PskipFrontend`
- [x] 4.3 Escribir `SinHoraDelSistemaTest` (ArchUnit, D4) que prohíbe `now()` sin argumentos, `System.currentTimeMillis()` y `new Date()` en las clases de producción de `ar.turnotarde`; verificar que pasa y, como prueba manual, que falla al agregar temporalmente un `Instant.now()` (quitarlo después)

## 5. Errores de la API (`shared.error`)

- [x] 5.1 Test primero: `ManejadorErroresTest` (`@WebMvcTest` con un controlador de prueba en `src/test`) cubriendo cada escenario de `specs/errores-api`: regla `RN-TU-01` → 409 con su mensaje; campo vacío → 400 `VALIDACION` con `detalles[{campo, mensaje}]`; JSON roto → 400 `SOLICITUD_INVALIDA`; método no soportado → 405 `METODO_NO_PERMITIDO`; excepción inesperada → 500 `ERROR_INTERNO` sin el mensaje interno ni stack trace en el cuerpo; error sin detalles → sin clave `detalles`; `Content-Type: application/json` en todos
- [x] 5.2 Implementar `ErrorApi`, `CodigosError`, `ReglaNegocioException` y el único `@RestControllerAdvice` `ManejadorErrores` según la tabla de D3 (log SLF4J del 500 con stack trace); verificar que `ManejadorErroresTest` pasa con `./gradlew test -PskipFrontend` y que hay un solo `@RestControllerAdvice` en `src/main` (búsqueda)

## 6. Frontend (Vite + TS strict + Vitest)

- [x] 6.1 Generar `frontend/` con `npm create vite@latest frontend -- --template vanilla-ts` (descarga de npm: confirmar con el usuario), quitar el ejemplo (contador, logos), dejar `index.html` mínimo (`lang="es-AR"`, título OdontoAgenda, `<main id="app">`) y `src/main.ts` mínimo; verificar con `npm run build`
- [x] 6.2 Endurecer `tsconfig.json` (D9) y sumar ESLint flat config + `typescript-eslint` con `no-explicit-any: error` y script `lint`; verificar que `npm run lint` pasa y que falla al agregar temporalmente un `any` (quitarlo después)
- [x] 6.3 Configurar `vite.config.ts` con el proxy `/api → http://localhost:8080` y Vitest (`environment: 'node'`), script `test` = `vitest run`; verificar con `npm test` (sin tests todavía, con `passWithNoTests` o el primer test de 6.4)
- [x] 6.4 Test primero: `src/shared/error-api.test.ts` con los escenarios de "Interpretación tipada de errores" (válido, con `detalles`, no-objeto, falta `codigo`, `codigo` no string → `ERROR_DESCONOCIDO`); implementar `src/shared/error-api.ts` sin `any`; verificar con `npm test` y `npm run lint`

## 7. Entrega de la SPA desde Spring

- [x] 7.1 Test primero: `EntregaSpaIT` (`@SpringBootTest` + MockMvc, perfil `test`, `app.frontend.ubicacion=classpath:/spa-prueba/` con fixture `index.html` y `assets/app.js`): `GET /` → 200 `index.html`; `GET /assets/app.js` → 200; `GET /turno/abc123` → 200 con el contenido de `index.html`; `GET /api/no-existe` → 404 `NO_ENCONTRADO` en JSON
- [x] 7.2 Implementar `ConfiguracionSpa` con el `PathResourceResolver` de fallback (D10) y la propiedad `app.frontend.ubicacion`; verificar que `EntregaSpaIT` pasa con `./gradlew integrationTest -PskipFrontend`
- [x] 7.3 Agregar en `build.gradle.kts` las tareas `npmInstall`, `npmBuild` y `copyFrontend` (`Sync` a `src/main/resources/static`), `processResources` dependiendo de `copyFrontend` salvo `-PskipFrontend`, con `npm.cmd` en Windows (D10); verificar que `./gradlew copyFrontend` deja `index.html` en `backend/src/main/resources/static/` y que una segunda corrida queda `UP-TO-DATE`

## 8. Documentación de arranque

- [x] 8.1 Escribir `README.md` en la raíz: requisitos (JDK 25, Node 24, PostgreSQL 18), creación de bases con el script, copia de `.env.example` a `.env`, modos demo (`./gradlew bootRun`) y desarrollo (`npm run dev` + `bootRun -PskipFrontend`), tareas de test, flag `-PskipFrontend`, tabla de códigos de error técnicos (D3) y versión de Gradle fijada; verificar siguiendo los comandos tal como están escritos en la tarea 9

## 9. Verificación integral

- [x] 9.1 Desde `backend/`, `./gradlew clean build` en verde (incluye `copyFrontend`, `test` e `integrationTest` contra `turnotarde_test`) y, desde `frontend/`, `npm run lint`, `npm test` y `npm run build` en verde
- [x] 9.2 Con un `.env` local completo, `./gradlew bootRun` levanta en `:8080` y se verifica con `curl`: `GET /` → 200 con el `index.html` de Vite; `GET /turno/x` → 200 `index.html`; `GET /api/no-existe` → 404 `{ "codigo": "NO_ENCONTRADO", ... }`; el log no muestra contraseñas; detener el servidor
- [x] 9.3 Con `bootRun -PskipFrontend` y `npm run dev` en paralelo, `curl http://localhost:5173/api/no-existe` devuelve el 404 JSON del backend (proxy de Vite); detener ambos procesos
- [x] 9.4 Correr `openspec validate c-01-foundation-setup` sin errores y repasar que cada escenario de las cuatro specs quedó cubierto por un test o por las verificaciones 9.2/9.3

## Workflow follow-up

- Sugerir al usuario actualizar la KB (`02_descripcion_general.md`) y `AGENTS.md`: Gradle 9.x en lugar de 8.x, Spring Boot 4.1.x fijado, PA-04 resuelta (sin Docker, base local), códigos de error técnicos.
- Archivar con `/opsx:archive c-01-foundation-setup` cuando los tests estén en verde y marcar `[x]` C-01 en `CHANGES.md`.
