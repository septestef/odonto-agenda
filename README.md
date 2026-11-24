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

## Correr la demo en la notebook

Pasos para dejar la demo lista desde cero en Windows, en PowerShell. Requisitos: los de la tabla de arriba, las bases creadas (paso 1 de "Puesta en marcha") y un `.env` en la raíz con `DB_PASSWORD`, `DEMO_PASSWORD` y las variables `MAIL_*` de la cuenta Gmail de prueba (contraseña de aplicación). Los valores se cargan a mano: nunca se versionan ni se pegan en chats o tickets.

1. **Cerrá la app** si estaba corriendo (Ctrl+C en su terminal).

2. **Reiniciá la base de demo** (borra todo el esquema `public` de `turnotarde`; los tests usan otra base). Desde la raíz del repo:

   ```powershell
   & "C:\Program Files\PostgreSQL\18\bin\psql.exe" -h localhost -U turnotarde -d turnotarde -f backend/scripts/reiniciar-base-demo.sql
   ```

   Pide la contraseña del rol `turnotarde`, muestra base, usuario y servidor, y se niega a seguir si no es la base local `turnotarde`. Para borrar hay que escribir `BORRAR`.

3. **Arrancá con el perfil demo** (compila el frontend, lo copia a `static` y Flyway reconstruye el esquema con los datos de ejemplo):

   ```powershell
   cd backend
   $env:SPRING_PROFILES_ACTIVE = 'demo'; .\gradlew.bat bootRun
   ```

   Está lista cuando el log dice `Started TurnoTardeApplication` y `Contraseña de demo aplicada`. Flyway tiene que llegar a la versión `901`.

4. **Abrí el navegador** en la notebook:
   - Odontólogo: `http://localhost:8080/login` con `demo@example.com` y tu `DEMO_PASSWORD`.
   - Paciente: `http://localhost:8080/reservar/demo`.

**Datos de ejemplo**: el miércoles 25/11/2026 tiene 6 turnos activos (09:00, 09:30, 10:30, 12:00, 16:30 y 18:00) y uno cancelado (11:00); el jueves 26 tiene uno a las 17:00 y el viernes 27 está libre. La reserva online exige 24 h de anticipación: en vivo se reserva el 26 o el 27; el turno manual del odontólogo sí admite horarios futuros del mismo día. Los links de los turnos del seed no sirven: para mostrar la gestión, hacé una reserva nueva con un buzón propio.

**Checklist del día**

- [ ] PostgreSQL 18 corriendo (servicio `postgresql-x64-18`).
- [ ] `.env` completo en la raíz (base, `DEMO_PASSWORD` y `MAIL_*`).
- [ ] Base reiniciada y app arrancada con el perfil `demo` (versión `901` en el log).
- [ ] Login del odontólogo y página de reserva abiertos en el navegador.
- [ ] Internet disponible y buzón de prueba abierto (revisá también spam).
- [ ] `localhost` solo funciona en la notebook: los links del correo se abren ahí, no en un celular.

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

### Correo de confirmación y avisos

Para correo real en `demo`, completá `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` y `MAIL_FROM_NAME`
en el entorno o `.env` ignorado. Para Gmail usá `smtp.gmail.com:587` y una contraseña de aplicación. El transporte
exige STARTTLS, verifica el servidor y limita a 5 segundos cada espera de conexión, lectura y escritura.
El arranque de `demo` valida la configuración local sin abrir una conexión SMTP; no comprueba las credenciales.
En `dev` y `test`, la configuración incompleta no impide reservar: el aviso admitido con destinatario válido
queda como `ERROR`/`CONFIG_SMTP`. Los tests usan un doble sin red.

Verificá el correo de demo por separado, solo con autorización: reservá con datos sintéticos y un buzón controlado,
confirmá la aceptación de SMTP, revisá también spam y probá el acceso a `APP_BASE_URL` desde el dispositivo receptor.
`localhost` solo sirve en la máquina anfitriona; para otro dispositivo usá una base HTTPS alcanzable desde ese equipo.
No copies credenciales, links ni tokens a logs o tickets. Gmail tiene una cuota orientativa de 500 envíos diarios,
según la cuenta y sujeta a cambios; aceptación no significa llegada a la bandeja.

El turno se confirma antes del correo. El despacho usa dos workers y una cola en memoria de 32: si no hay cupo,
no se envía ni se registra el aviso. Una caída después del commit puede perder el aviso; no hay outbox, reintentos
durables ni reenvío. Un timeout puede dejar `ERROR`/`SMTP_FALLO` aunque SMTP haya aceptado; `enviado_en` representa
el inicio del intento. Si SMTP acepta pero la auditoría falla, el turno sigue confirmado y solo queda un diagnóstico
sanitizado. No habilites el logging de parámetros JDBC ni la depuración del protocolo SMTP.

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

## Configuración de la agenda

El odontólogo configura su agenda desde el frontend con estas rutas (todas exigen sesión; las escrituras, además, la cabecera `X-XSRF-TOKEN`). El dueño de los datos sale siempre de la sesión: un `odontologoId` que venga en el cuerpo, la query o una cabecera se ignora, y un recurso ajeno responde 404 como si no existiera.

| Ruta | Uso |
|---|---|
| `GET /api/horarios`, `PUT /api/horarios` | Consultar y reemplazar la semana completa con `{ "franjas": [{ "diaSemana", "inicio", "fin" }] }` (día 1–7, horas `HH:mm`). El `PUT` valida antes de borrar: un rango invertido o dos franjas solapadas dan 422 `RN-AG-01` y dejan el horario anterior intacto. |
| `GET /api/prestaciones` | Listar `{ "prestaciones": [...] }`, incluidas las inactivas para que puedas mostrar su estado. |
| `POST /api/prestaciones`, `PUT /api/prestaciones/{id}`, `DELETE /api/prestaciones/{id}` | Crear, editar y dar de baja con `{ "nombre", "duracionMinutos" }`. La duración tiene que ser múltiplo de 15 (si no, 422 `RN-AG-03`); un nombre repetido para el mismo odontólogo da 400 `VALIDACION` con detalle en `nombre`. La baja es lógica: responde 204, conserva la fila con `activa: false` y la prestación deja de ofrecerse en el perfil público. |
| `GET /api/bloqueos?desde=&hasta=`, `POST /api/bloqueos`, `DELETE /api/bloqueos/{id}` | Listar, crear y borrar bloqueos con `{ "inicio", "fin", "motivo" }` (instante con offset, `motivo` opcional). Cada bloqueo trae `turnosAfectados`; un bloqueo no cancela turnos: solo deja de ofrecer ese rango en la disponibilidad pública. Un rango invertido da 400 `VALIDACION` con detalle en `fin`. |

## Tiempo

El servidor usa un único `Clock` UTC inyectable (`shared.tiempo`); la zona del negocio (`APP_TIMEZONE`, por defecto `America/Argentina/Buenos_Aires`) solo se usa para interpretar y mostrar fechas (`ZonaNegocio`). Un test de ArchUnit (`SinHoraDelSistemaTest`) hace fallar la build si el código de producción llama a `now()` sin reloj, a `System.currentTimeMillis()` o a `new Date()`.
