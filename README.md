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

## Migraciones

El esquema se gestiona con Flyway (`backend/src/main/resources/db/migration`). Con seis personas trabajando en paralelo rige esta convención:

- **Un número por change**: cada change usa el suyo como versión (`V2__esquema_base.sql` para C-02, `V7__...` para C-07). Si necesita más de una migración, `V<NN>_1__`, `V<NN>_2__` (Flyway lee `V7_1` como 7.1). Flyway admite huecos: no existe `V1`.
- **900+ solo para datos de demo**: viven en `db/demo` y solo se aplican con el perfil `demo` (`V900__seed_demo.sql`).
- **`out-of-order` activado** (`spring.flyway.out-of-order: true` en `application.yml`): si tu base local ya aplicó `V7` y se integra un `V5`, Flyway lo aplica igual en vez de rechazarlo. Un futuro perfil de producción debe ponerlo en `false` y consolidar el esquema con un baseline.
- **Nunca edites una migración ya mergeada**: se corrige con otra migración (Flyway rechaza el cambio de checksum).
- **Columnas `NOT NULL` nuevas** en tablas que tienen datos de demo tienen que traer `DEFAULT` (o completar los datos), porque el seed `V900` puede haberse aplicado antes.
- **Reiniciar una base local** (no hay `undo` en Flyway Community; no hay datos reales que preservar antes de la demo): como dueño de la base, `DROP SCHEMA public CASCADE; CREATE SCHEMA public;` y volver a arrancar.

### Demo con datos de ejemplo

Definí `DEMO_PASSWORD` en el `.env` (la contraseña del odontólogo `demo`; sin ella la app no arranca con ese perfil) y levantá con el perfil `demo`:

```
cd backend
SPRING_PROFILES_ACTIVE=demo ./gradlew bootRun -PskipFrontend
```

Flyway aplica `V2` y `V900` (odontólogo `demo`, horario, prestaciones y turnos del 23 al 27/11/2026); al arrancar, la app guarda el hash BCrypt de `DEMO_PASSWORD`. La contraseña nunca se versiona.

## Formato de error de la API

Todo error bajo `/api/**` es JSON con la forma `{ "codigo", "mensaje", "detalles"? }` (`detalles` solo aparece si aporta información). No se usa `application/problem+json`. Además de los códigos de reglas de negocio `RN-XX-NN` (ver `knowledge-base/05_reglas_de_negocio.md`), hay códigos técnicos:

| Código | HTTP | Cuándo |
|---|---|---|
| `VALIDACION` | 400 | Campos inválidos; `detalles` lista `{ campo, mensaje }` |
| `SOLICITUD_INVALIDA` | 400 / 415 | Cuerpo ilegible, parámetro faltante o de tipo incorrecto, formato no soportado |
| `NO_AUTENTICADO` | 401 | Sin sesión de odontólogo (o sesión expirada); también en `/api/**` inexistente para un anónimo |
| `ACCESO_DENEGADO` | 403 | Falta el token CSRF en una escritura protegida, o el rol no alcanza |
| `NO_ENCONTRADO` | 404 | Ruta o recurso inexistente |
| `METODO_NO_PERMITIDO` | 405 | Método HTTP no soportado en la ruta |
| `ERROR_INTERNO` | 500 | Error inesperado; mensaje genérico, el detalle queda solo en el log |
| `ERROR_DESCONOCIDO` | — | Solo en el frontend: cuerpo de error que no cumple el formato |

Los rechazos 401/403 los escribe la cadena de filtros de Spring Security con este mismo formato (no pasan por el `@RestControllerAdvice`). No hay redirects ni formularios de login.

## Sesión y autenticación

El odontólogo inicia sesión con `POST /api/auth/login` (`{ "email", "password" }`); la respuesta es el DTO del odontólogo (`id`, `nombre`, `apellido`, `email`, `slug`) y deja una cookie de sesión `JSESSIONID`:

| Propiedad | Valor |
|---|---|
| Tipo | Sesión HTTP de servidor (sin JWT) |
| Caducidad | 8 horas de **inactividad** (se renueva en cada pedido) |
| Cookie | `JSESSIONID`, `HttpOnly`, `SameSite=Lax`, solo por cookie (nunca en la URL) |
| Rotación | El id de sesión cambia al autenticar (protección contra fijación) |

Los tres endpoints de sesión son `POST /api/auth/login` (público, sin CSRF), `POST /api/auth/logout` (204, exige sesión y CSRF) y `GET /api/auth/me` (devuelve el DTO, exige sesión). Todo `/api/**` salvo `/api/public/**` y el login exige la sesión; las rutas públicas de reserva no la necesitan.

**CSRF**: la respuesta de un `GET` protegido incluye la cookie `XSRF-TOKEN` (legible por JavaScript, sin `HttpOnly`). El frontend reenvía ese valor en la cabecera `X-XSRF-TOKEN` en cada `POST`/`PUT`/`PATCH`/`DELETE` protegido. La cookie de sesión, que sí es la credencial, sigue siendo `HttpOnly`.

El email se compara sin distinguir mayúsculas y se ignoran los espacios sobrantes. La contraseña se valida hasta **72 bytes UTF-8** (límite real de BCrypt): 72 caracteres con una `ñ` son 73 bytes y dan 400 `VALIDACION` con `campo = "password"`; esa validación no consume intentos del contador.

El login se bloquea transitoriamente tras 5 intentos fallidos por email (429 `RN-AU-03`, con `reintentarEnSegundos` y `bloqueadoHasta` en `detalles`). Un login correcto reinicia el contador. El contador vive **en memoria, por nodo, y se pierde al reiniciar**: alcanza para la demo (una sola instancia), pero no es un rate limiting distribuido; C-20 lo resuelve.

> **Riesgo R12 (aceptado para la demo)**: como el contador es por email y no distingue quién intenta entrar, 5 intentos fallidos contra `demo@example.com` dejan afuera 15 minutos también al odontólogo legítimo. En la demo basta esperar o reiniciar la app; no se mitigó porque no hay segundo factor ni rate limiting distribuido en el alcance actual.

## Tiempo

El servidor usa un único `Clock` UTC inyectable (`shared.tiempo`); la zona del negocio (`APP_TIMEZONE`, por defecto `America/Argentina/Buenos_Aires`) solo se usa para interpretar y mostrar fechas (`ZonaNegocio`). Un test de ArchUnit (`SinHoraDelSistemaTest`) hace fallar la build si el código de producción llama a `now()` sin reloj, a `System.currentTimeMillis()` o a `new Date()`.
