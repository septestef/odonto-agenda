# OdontoAgenda

Agenda de turnos online para un consultorio odontológico: el paciente reserva sin cuenta y el odontólogo gestiona su agenda. Backend Spring Boot 4.1.2 (Java 25) + frontend Vite/TypeScript servido desde el mismo origen.

La documentación de producto está en `knowledge-base/`; el trabajo se organiza en changes de OpenSpec (`openspec/`, índice en `CHANGES.md`).

## Requisitos

| Herramienta | Versión |
|---|---|
| JDK | 25 (Temurin) |
| Node.js / npm | 24 / 11+ |
| PostgreSQL | 18 (local, sin Docker) |
| Gradle | no hace falta instalarlo: el wrapper del repo fija **Gradle 9.9.1** (con `distributionSha256Sum`) |

En Windows, `psql` suele estar en `C:\Program Files\PostgreSQL\18\bin\psql.exe` (fuera del `PATH`).

## Puesta en marcha

1. **Crear las bases locales** (una sola vez, como superusuario `postgres`). El script pide la contraseña del rol `turnotarde` y es idempotente:

   ```
   "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -f backend/scripts/crear-bases-locales.sql
   ```

   Crea el rol `turnotarde` y las bases `turnotarde` (app) y `turnotarde_test` (tests de integración).

2. **Configurar el entorno**: copiá `.env.example` a `.env` en la raíz y completá al menos `DB_PASSWORD` (la que elegiste en el paso 1). El `.env` está en `.gitignore`: nunca se versiona. Las variables reales del entorno tienen precedencia sobre el `.env`.

3. **Modo demo** (el backend sirve el frontend empaquetado en `http://localhost:8080`):

   ```
   cd backend
   ./gradlew bootRun
   ```

   La tarea compila el frontend (`npm ci` + `npm run build`) y lo copia a `backend/src/main/resources/static/` (carpeta generada e ignorada por git).

4. **Modo desarrollo** (hot reload del frontend; Vite en `http://localhost:5173` reenvía `/api` al backend en `:8080`):

   ```
   cd backend
   ./gradlew bootRun -PskipFrontend
   ```

   y en otra terminal:

   ```
   cd frontend
   npm run dev
   ```

## Tests y build

Desde `backend/`:

| Comando | Qué hace |
|---|---|
| `./gradlew test -PskipFrontend` | Unitarios (`*Test`), sin base de datos |
| `./gradlew integrationTest -PskipFrontend` | Integración (`*IT`) contra PostgreSQL local `turnotarde_test` (nunca H2) |
| `./gradlew build` | Todo: frontend, `test` e `integrationTest` (`check` depende de ambas) |

`-PskipFrontend` omite `npmInstall`/`npmBuild`/`copyFrontend`: sirve para iterar solo el backend. Si falta la base de test o no coincide la contraseña, los ITs fallan (no se saltean) con un mensaje que nombra la base y el script que la crea. Los tests de integración toman la contraseña de `TEST_DB_PASSWORD` o, si no está, de `DB_PASSWORD`.

Desde `frontend/`: `npm run lint` (ESLint, `no-explicit-any` en error), `npm test` (Vitest) y `npm run build` (`tsc --noEmit` + Vite).

## Formato de error de la API

Todo error bajo `/api/**` es JSON con la forma `{ "codigo", "mensaje", "detalles"? }` (`detalles` solo aparece si aporta información). No se usa `application/problem+json`. Además de los códigos de reglas de negocio `RN-XX-NN` (ver `knowledge-base/05_reglas_de_negocio.md`), hay códigos técnicos:

| Código | HTTP | Cuándo |
|---|---|---|
| `VALIDACION` | 400 | Campos inválidos; `detalles` lista `{ campo, mensaje }` |
| `SOLICITUD_INVALIDA` | 400 / 415 | Cuerpo ilegible, parámetro faltante o de tipo incorrecto, formato no soportado |
| `NO_ENCONTRADO` | 404 | Ruta o recurso inexistente |
| `METODO_NO_PERMITIDO` | 405 | Método HTTP no soportado en la ruta |
| `ERROR_INTERNO` | 500 | Error inesperado; mensaje genérico, el detalle queda solo en el log |
| `ERROR_DESCONOCIDO` | — | Solo en el frontend: cuerpo de error que no cumple el formato |

## Tiempo

El servidor usa un único `Clock` UTC inyectable (`shared.tiempo`); la zona del negocio (`APP_TIMEZONE`, por defecto `America/Argentina/Buenos_Aires`) solo se usa para interpretar y mostrar fechas (`ZonaNegocio`). Un test de ArchUnit (`SinHoraDelSistemaTest`) hace fallar la build si el código de producción llama a `now()` sin reloj, a `System.currentTimeMillis()` o a `new Date()`.
