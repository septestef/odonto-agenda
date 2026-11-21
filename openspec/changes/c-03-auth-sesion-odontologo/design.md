# Design — C-03 `auth-sesion-odontologo`

## Context

Motivación y alcance en `proposal.md`; requisitos en `specs/` (`autenticacion-odontologo`, `sesion-odontologo`, `aislamiento-odontologo` y el delta de `errores-api`).

Estado que deja C-02 (archivado el 2026-11-21, ver su `design.md`):

| Pieza | Estado actual |
|---|---|
| Stack | Spring Boot 4.1.2, Java 25, Gradle 9.9.1. Hibernate 7 (`ddl-auto=validate`), Flyway 12, **Jackson 3** (`tools.jackson.databind.ObjectMapper`; las anotaciones siguen en `com.fasterxml.jackson.annotation`). |
| Seguridad | Solo `org.springframework.security:spring-security-crypto` (7.1.2): hay `BCryptPasswordEncoder` pero **no** hay `SecurityFilterChain`, ni starter, ni ningún filtro. Todo `/api/**` es hoy público. |
| `odontologo` | `UNIQUE(email)` **sensible a mayúsculas**, `password_hash varchar(100) NOT NULL` con BCrypt "pelado" sin prefijo `{bcrypt}` (contrato D6 de C-02), `activo boolean NOT NULL DEFAULT true`, `slug UNIQUE`. |
| Repositorio | `OdontologoRepository` solo tiene `findBySlug`. |
| Errores | `shared/error/ManejadorErrores` (`@RestControllerAdvice`) traduce excepciones de MVC a `ErrorApi {codigo, mensaje, detalles?}`; `CodigosError` tiene los códigos técnicos. Los filtros de seguridad **no** pasan por el advice. |
| Tiempo | Bean `Clock` UTC en `shared/tiempo`; `SinHoraDelSistemaTest` (ArchUnit) rompe la build si el código llama a `now()` sin reloj. |
| Tests | `*Test` unitarios sin base; `*IT` contra `turnotarde_test` (nunca H2) con `soporte.IntegracionTest`, `LimpiezaBase`, `FixturesJdbc` y `RelojDePrueba`. `EntregaSpaIT` usa `@AutoConfigureMockMvc` con filtros activos. |
| Demo | `ContrasenaDemoInicializador` (`@Profile("demo")`) guarda el hash de `DEMO_PASSWORD` con una instancia propia de `BCryptPasswordEncoder`. |

Governance **CRITICO**: este documento se revisa y se aprueba antes del apply. Alcance acotado a CHANGES §C-03.

## Goals / Non-Goals

**Goals:**
- Login, logout y consulta de sesión funcionando con sesión de servidor, sin JWT (DD-04).
- RN-AU-01..04 y RN-SE-01 implementadas y cubiertas por tests, con el `Clock` inyectado.
- Un contrato de aislamiento reutilizable que C-05, C-12 y C-17 puedan consumir sin reimplementar nada.
- Que el 401/403/429 salga siempre en el formato `{codigo, mensaje, detalles?}`, venga del advice o de la cadena de filtros.

**Non-Goals:**
- Pantalla de login, router, `api-client.ts`, guard de sesión y manejo de `X-XSRF-TOKEN` en el frontend: C-04.
- Alta de odontólogos, cambio de contraseña, "recordarme", recuperación por email (SU-05, fuera de la v0.1).
- `maximumSessions(1)`, Spring Session JDBC o rate limiting distribuido (DD-04 lo acepta como deuda; C-20).
- Rate limiting de la reserva pública (C-20) y cualquier endpoint de negocio (C-05, C-07, C-12).
- Auditoría de accesos a datos de salud (C-17).

## Decisions

### D1 — `spring-boot-starter-security` y una única `SecurityFilterChain`

- **Qué**: agregar `implementation("org.springframework.boot:spring-boot-starter-security")` (Spring Security 7.1.2, versión del BOM) y quitar la línea explícita de `spring-security-crypto`, que pasa a llegar transitiva. Una sola clase `auth/ConfiguracionSeguridad` con:
  - `@Bean PasswordEncoder` → `new BCryptPasswordEncoder()` (fuerza 10 por defecto), **sin** `DelegatingPasswordEncoder` y **sin** prefijo `{bcrypt}`: respeta el hash que guarda C-02.
  - `@Bean AuthenticationManager` desde `AuthenticationConfiguration`, con un `DaoAuthenticationProvider(odontologoServicioDetalles)` y `setPasswordEncoder(passwordEncoder)`.
  - `@Bean SecurityFilterChain` con `csrf`, `sessionManagement`, `authorizeHttpRequests`, `exceptionHandling` (D4–D6) y `formLogin`, `httpBasic`, `logout` y `requestCache` **deshabilitados** (el login y el logout son nuestros endpoints JSON; el `logout` por defecto de Spring redirige y no sirve acá).
- **Por qué**: es el único camino para tener sesión, CSRF y autorización sin escribir filtros propios; el starter activa la autoconfiguración, que esta `SecurityFilterChain` reemplaza por completo.
- **Alternativas**: `DelegatingPasswordEncoder` (descartado: no reconoce el hash sin prefijo); seguir con `spring-security-crypto` y validar a mano (descartado: habría que reimplementar sesión, CSRF y autorización, justo lo que DD-04 evita).

### D2 — Contrato HTTP de los tres endpoints

| Método y ruta | Sesión | CSRF | Cuerpo | Respuestas |
|---|---|---|---|---|
| `POST /api/auth/login` | no | exento | `{email, password}` | 200 `OdontologoDto`; 400 `VALIDACION`; 401 `RN-AU-01`; 429 `RN-AU-03` |
| `POST /api/auth/logout` | sí | sí | — | 204 sin cuerpo; 401 `NO_AUTENTICADO`; 403 `ACCESO_DENEGADO` |
| `GET /api/auth/me` | sí | no aplica | — | 200 `OdontologoDto`; 401 `NO_AUTENTICADO` |

- **DTOs** (`record` + Bean Validation, en `auth/dto`):
  - `PeticionLogin(@NotBlank @Email @Size(max = 255) String email, @NotBlank @Size(min = 1, max = 72) String password)`. El máximo de 72 es el límite de BCrypt: más allá de eso la contraseña se trunca o se rechaza, así que el contrato lo hace explícito.
  - `OdontologoDto(Long id, String nombre, String apellido, String email, String slug)`. Sin `passwordHash`, `telefono` ni `direccionConsultorio`: lo mínimo que necesita C-04 para el guard y el encabezado. Es aditivo si un change posterior necesita más.
  - `DetalleBloqueo(long reintentarEnSegundos, Instant bloqueadoHasta)` para `detalles` del 429.
- **Login responde el mismo DTO que `/me`**: evita un round trip y le da al frontend el nombre para pintar el encabezado de inmediato. `/me` sigue siendo la fuente de verdad al recargar (Flujo 1), así que no hay dos contratos.
- **Validación antes de credenciales**: un 400 por email mal formado no consume intentos del bloqueo (spec `autenticacion-odontologo`).
- **Alternativa**: login 204 y obligar a `/me` (más puro, pero agrega un round trip en la demo y un estado intermedio en el frontend).

### D3 — Sesión de servidor: cookie, expiración y persistencia del contexto

- **Configuración** en `application.yml`:
  ```yaml
  server:
    servlet:
      session:
        timeout: 8h            # RN-AU-04: 8 horas de inactividad
        tracking-modes: cookie # el id nunca viaja por URL
        cookie:
          name: JSESSIONID
          http-only: true
          same-site: lax
  ```
  El contenedor renueva el vencimiento en cada pedido: es expiración por inactividad, no absoluta. `Secure` queda en `false` para la demo en `http://localhost`; C-20 lo activa en producción (deuda anotada).
- **Creación de la sesión en el controlador**: como el login no pasa por un filtro de autenticación de Spring, el servicio hace explícito lo que haría el filtro:
  1. `sessionAuthenticationStrategy.onAuthentication(auth, request, response)` con un bean `SessionAuthenticationStrategy` = `ChangeSessionIdAuthenticationStrategy` (protección contra fijación de sesión).
  2. `SecurityContextHolder.getContext().setAuthentication(auth)` y `securityContextRepository.saveContext(context, request, response)` con un `HttpSessionSecurityContextRepository`.
  Sin el paso 2 el pedido siguiente no ve la sesión, porque `SecurityContextHolderFilter` no guarda el contexto automáticamente.
- **Por qué no un `AbstractAuthenticationProcessingFilter` propio**: habría que resolver Bean Validation, el 429 con tiempo restante y el contador con `Clock` dentro de un filtro, donde no hay advice ni binding de DTOs; y el filtro tampoco puede responder 429 sin abandonar el flujo de autenticación. Se reutiliza lo valioso del framework (`AuthenticationManager`, `SessionAuthenticationStrategy`, `SecurityContextRepository`) y se deja la traducción HTTP en el controlador, como pide la arquitectura del proyecto.
- **Non-goal explícito**: `maximumSessions`, `rememberMe` y sesión distribuida.

### D4 — CSRF con `csrf.spa()`

- **Qué**: `.csrf(csrf -> csrf.spa().ignoringRequestMatchers("/api/public/**", "/api/auth/login"))`. `spa()` (disponible desde Spring Security 7.0) configura `CookieCsrfTokenRepository` con `XSRF-TOKEN` legible por JavaScript, un `CsrfTokenRequestHandler` que resuelve el token **plano** del header `X-XSRF-TOKEN` (necesario porque el valor por defecto está enmascarado con XOR para BREACH) y el refresco del token después de login y logout.
- **Cookie `XSRF-TOKEN` sin `HttpOnly`, a propósito**: el frontend tiene que leerla para reenviar el header; no es una credencial por sí sola (double-submit: el servidor compara header contra cookie). La cookie de sesión, que sí es la credencial, sigue `HttpOnly`.
- **Por qué el login queda exento**: RN-AU-01/Flujo 1 lo definen como público y un cliente sin sesión no tiene nada que proteger de CSRF; el riesgo real (crear recursos a nombre del usuario) empieza con la sesión.
- **Detalle**: como el login es un controlador y no un filtro, `CsrfAuthenticationStrategy` no se ejecuta y el token previo al login sigue siendo válido. No hace falta refrescarlo, pero el test de CSRF lo cubre igual.
- **Alternativa / plan B**: si `spa()` no se comporta como dice la documentación en 7.1.2, se configura a mano `CookieCsrfTokenRepository.withHttpOnlyFalse()` + un `CsrfTokenRequestHandler` que delegue en `XorCsrfTokenRequestAttributeHandler` para atributos y en `CsrfTokenRequestAttributeHandler` para el header. Se verifica en la primera tarea del apply; la spec no cambia.

### D5 — Orden de autorización y el 401 en rutas de API inexistentes

- **Reglas**, en este orden:
  1. `POST /api/auth/login` → `permitAll`.
  2. `/api/public/**` → `permitAll`.
  3. `/api/**` → `hasRole("ODONTOLOGO")` (cubre `/api/auth/me` y `/api/auth/logout`).
  4. `anyRequest()` → `permitAll` (recursos estáticos, `/index.html`, `/assets/**` y las rutas de la SPA que resuelve `ConfiguracionSpa`).
- **Consecuencia aceptada y registrada como delta de `errores-api`**: una ruta `/api/**` inexistente ya no responde 404 a un cliente anónimo, responde 401 `NO_AUTENTICADO`; con sesión de odontólogo sigue respondiendo 404 `NO_ENCONTRADO`. La autorización corre antes del ruteo, así que para devolver 404 primero habría que enumerar los prefijos existentes (y acordarse de actualizarlos en cada change). Como efecto secundario, un anónimo tampoco puede enumerar rutas.
- **Alternativa descartada**: `permitAll` para `/api/**` desconocido y autorización por método en cada controlador: insegura por defecto (un controlador nuevo sin anotación queda abierto).

### D6 — 401 y 403 en el formato uniforme desde la cadena de filtros

- **Qué**: `shared/seguridad/EscritorErrorJson` (componente con el `ObjectMapper` de Jackson 3 administrado por Boot) que fija el estado, `Content-Type: application/json;charset=UTF-8` y serializa `ErrorApi`. Lo usan:
  - `ErrorApiAuthenticationEntryPoint` → 401 `NO_AUTENTICADO`, mensaje "Tu sesión no está iniciada o expiró. Volvé a ingresar."
  - `ErrorApiAccessDeniedHandler` → 403 `ACCESO_DENEGADO`, mensaje "No pudimos verificar la solicitud. Recargá la página e intentá de nuevo."
- **Códigos nuevos** en `CodigosError`: `NO_AUTENTICADO` y `ACCESO_DENEGADO`. Los rechazos del login no usan estos códigos: van por `ReglaNegocioException` al advice existente, con `RN-AU-01` (401) y `RN-AU-03` (429).
- **Por qué dos caminos**: `ExceptionTranslationFilter` maneja los rechazos antes de llegar a MVC, así que el `@RestControllerAdvice` no los ve. Duplicar el formato en dos clases chiquitas es más simple que mover toda la seguridad a controladores.
- **Sin redirects**: `requestCache` deshabilitado y el entry point nunca responde 302 ni `Location` (spec `sesion-odontologo`).

### D7 — Contador de intentos fallidos en memoria con `Clock`

- **Clase**: `auth/ControlIntentosLogin` (`@Component`, `Clock` inyectado). Estado: `ConcurrentHashMap<String, Registro>` con clave = email normalizado (`strip().toLowerCase(Locale.ROOT)`), valor = `Registro(int fallos, Instant ultimoFallo, Instant bloqueadoHasta)`.
- **Operaciones atómicas** con `compute` (no hay lectura-modificación-escritura suelta): `registrarFallo(email)` devuelve `EstadoBloqueo(boolean bloqueado, Duration restante)`; `registrarExito(email)` borra la entrada; `estado(email)` informa si está bloqueado y cuánto falta.
- **Reglas**: `MAX_INTENTOS = 5`, `BLOQUEO = Duration.ofMinutes(15)` como constantes de la clase (RN-AU-03 es una regla de negocio fija, no configuración de despliegue). El **5.º fallo ya responde 429**: en ese instante el bloqueo está vigente, y es lo que la UI necesita para mostrar el aviso. Al vencer, la entrada se descarta y el contador vuelve a cero.
- **Hora**: siempre `Instant.now(reloj)`; `SinHoraDelSistemaTest` lo verifica.
- **Purga**: las entradas vencidas se descartan al consultarlas y, si el mapa supera 1.000 entradas, se barren las que llevan más de 15 minutos sin fallos. Sin esto, un atacante que rote emails haría crecer el mapa sin límite.
- **Alternativas**: tabla en la base (una escritura por intento fallido y una migración para un dato efímero); Bucket4j/Caffeine (dependencia nueva para algo que son 60 líneas); bloqueo por IP (queda para C-20).
- **Límites aceptados**: el contador es por nodo y se pierde al reiniciar (DD-04 ya acepta sesión en memoria; C-20 resuelve rate limiting real). Se documenta en el README.

### D8 — No revelar qué emails existen (mensaje, estado y tiempo)

- **Mismo resultado**: email inexistente, contraseña incorrecta y cuenta inactiva devuelven **el mismo** 401 `RN-AU-01` con el mismo mensaje ("El email o la contraseña no son correctos.").
- **Mismo bloqueo**: el contador también cuenta emails inexistentes, así que a los 5 intentos cualquier email recibe 429 (spec `autenticacion-odontologo`).
- **Mismo tiempo**: `DaoAuthenticationProvider` con `hideUserNotFoundExceptions` (por defecto `true`) convierte "no existe" en `BadCredentialsException`, pero sin un hash real el pedido vuelve mucho más rápido y el tiempo delata la existencia de la cuenta. Cuando el email no existe se ejecuta igual un `passwordEncoder.matches(password, hashFicticio)` contra un hash BCrypt generado una vez al arrancar (nunca versionado, nunca logueado).
- **Cuenta inactiva**: `OdontologoAutenticado.isEnabled()` devuelve `activo`, así que Spring lanza `DisabledException`; el servicio la trata como cualquier `AuthenticationException` y responde el 401 genérico.
- **Riesgo residual**: el tiempo sigue sin ser idéntico (diferencias de red, JIT). Se acepta para la v0.1.

### D9 — Contrato de aislamiento para C-05, C-12 y C-17 (RN-SE-01)

- `shared/seguridad/OdontologoAutenticado` (`implements UserDetails`): `Long id`, `String email`, `String passwordHash`, `boolean activo`, autoridad única `ROLE_ODONTOLOGO`. Es el principal que queda en la sesión.
- `shared/seguridad/OdontologoActual` (`@Component`): lee `SecurityContextHolder` y expone `long id()` y `OdontologoAutenticado autenticado()`. Si no hay principal del tipo esperado lanza `ReglaNegocioException(NO_AUTENTICADO, …, UNAUTHORIZED)` → 401, nunca un 500 (spec `aislamiento-odontologo`).
- `shared/seguridad/AislamientoOdontologo.exigir(Optional<T>)`: devuelve el recurso o lanza 404 `NO_ENCONTRADO` con el mensaje genérico. Regla de uso para los próximos changes: **toda consulta del odontólogo es `findByIdAndOdontologoId(id, OdontologoActual.id())` y pasa por `exigir(...)`**; nunca `findById` + comparación en el servicio, porque es fácil de olvidar.
- **Por qué en `shared` y no en `auth`**: todos los módulos necesitan el dueño de la sesión y ninguno debería depender del paquete de autenticación; `auth` queda como el módulo que crea el principal y `shared/seguridad` como la infraestructura que lo lee.
- **Cómo se prueba en C-03 sin recursos de negocio**: dos controladores de prueba en `src/test` (`ControladorProtegidoDePrueba` en `/api/prueba/protegido` y `ControladorAislamientoDePrueba` en `/api/prueba/recurso/{id}`, que busca en `prestacion` filtrando por odontólogo). Se registran con `@Import` solo en los ITs, así que no existen en producción.

### D10 — Migración `V3` e identidad de login insensible a mayúsculas

- **Qué**: `V3__email_odontologo_unico_sin_mayusculas.sql` con `CREATE UNIQUE INDEX odontologo_email_sin_mayusculas_uq ON odontologo (lower(email));` y `OdontologoRepository.findByEmailIgnoreCase(String)`.
- **Por qué**: `UNIQUE(email)` de C-02 distingue mayúsculas. Sin el índice, dos cuentas podrían diferir solo en mayúsculas y `findByEmailIgnoreCase` lanzaría `IncorrectResultSizeDataAccessException` → 500 en el login. El índice funcional cierra el agujero y además indexa la búsqueda real.
- **Riesgo**: si una base local tuviera duplicados que solo difieren en mayúsculas, la migración falla. Hoy no los hay (el seed inserta `demo@example.com` en minúsculas) y no existe alta de odontólogos (SU-05).
- **Alternativa**: normalizar el email en cada escritura. Sin endpoint de alta no hay dónde hacerlo, y la normalización sola no impide el duplicado a nivel base.

### D11 — Estructura de clases

| Paquete | Clase | Rol |
|---|---|---|
| `auth` | `ConfiguracionSeguridad` | `SecurityFilterChain`, `PasswordEncoder`, `AuthenticationManager`, `SessionAuthenticationStrategy`, `SecurityContextRepository`. |
| `auth` | `ControladorAuth` | Los tres endpoints; solo traduce HTTP. |
| `auth` | `ServicioAutenticacion` | Reglas del login/logout/me: bloqueo, autenticación, rotación de sesión, persistencia del contexto, recarga del odontólogo. |
| `auth` | `ControlIntentosLogin` | Contador en memoria con `Clock` (D7). |
| `auth` | `OdontologoServicioDetalles` | `UserDetailsService`: busca por email (insensible a mayúsculas) y arma `OdontologoAutenticado`. |
| `auth/dto` | `PeticionLogin`, `OdontologoDto`, `DetalleBloqueo` | Contrato de la API. |
| `shared/seguridad` | `OdontologoAutenticado`, `OdontologoActual`, `AislamientoOdontologo` | Contrato de aislamiento (D9). |
| `shared/seguridad` | `ErrorApiAuthenticationEntryPoint`, `ErrorApiAccessDeniedHandler`, `EscritorErrorJson` | 401/403 uniformes (D6). |

`OdontologoRepository` suma `findByEmailIgnoreCase`; `CodigosError` suma los dos códigos técnicos; `ContrasenaDemoInicializador` pasa a inyectar el `PasswordEncoder` bean (su test unitario se actualiza).

### D12 — Estrategia de tests

**Unitarios (`./gradlew test -PskipFrontend`, sin base):**

| Clase | Cubre |
|---|---|
| `ControlIntentosLoginTest` | 1–4 fallos no bloquean; el 5.º bloquea 900 s; bloqueado justo antes del vencimiento y libre justo después (con `RelojDePrueba`); un éxito limpia el contador; emails distintos no se mezclan; normalización de mayúsculas/espacios; un email inexistente también se bloquea; purga de entradas vencidas. |
| `OdontologoActualTest` | Devuelve el id del principal; sin principal lanza 401 `NO_AUTENTICADO`. |
| `AislamientoOdontologoTest` | `Optional.empty()` → 404 `NO_ENCONTRADO`; presente → el recurso. |
| `EscritorErrorJsonTest` | 401 y 403 con `Content-Type` JSON, cuerpo `{codigo, mensaje}`, sin `detalles` vacío y sin HTML. |
| `CodificacionContrasenaTest` | El bean es BCrypt con prefijo `$2` y **sin** `{bcrypt}`; un hash generado verifica la contraseña original. |
| `SesionPropiedadesTest` | La configuración efectiva de `application.yml` da 8 h de expiración, `tracking-modes: cookie`, cookie `HttpOnly` y `SameSite=Lax`. |

**Integración (`./gradlew integrationTest -PskipFrontend`, PostgreSQL `turnotarde_test`, MockMvc con filtros activos):**

| Clase | Cubre |
|---|---|
| `LoginIT` | Login válido (200 + cookie + `/me` con la misma sesión); email inexistente, contraseña incorrecta y cuenta inactiva con el **mismo** cuerpo 401; email con mayúsculas y espacios funciona; 400 por email mal formado y por contraseña vacía; la respuesta no trae el hash; un hash `!PENDIENTE…` no autentica. |
| `BloqueoLoginIT` | Los escenarios de `RN-AU-03` con `RelojDePrueba`: 4.º 401, 5.º 429 con `reintentarEnSegundos` ≈ 900, credenciales correctas bloqueadas, fin del bloqueo a los 15 min, éxito que reinicia el contador, emails independientes, email inexistente también bloqueado. |
| `SesionIT` | `/me` sin sesión → 401 `NO_AUTENTICADO`; logout 204 y después `/me` 401; logout sin sesión → 401; el identificador de sesión cambia después del login; `/me` con la cuenta desactivada → 401. |
| `CsrfIT` | Logout sin `X-XSRF-TOKEN` → 403 `ACCESO_DENEGADO` y la sesión sigue viva; con el token de la cookie → 204; login sin token → 200; `POST` a un controlador de prueba bajo `/api/public/**` sin token → no es 403; `GET` sin token → no es 403. |
| `AutorizacionIT` | `/api/public/**` sin sesión pasa; `/api/prueba/protegido` sin sesión → 401; con sesión → 200; `/`, `/index.html` y `/assets/**` sin sesión → 200; `/api/no-existe` sin sesión → 401 y con sesión → 404. |
| `AislamientoOdontologoIT` | El odontólogo A pide un recurso de B → 404 con el mismo cuerpo que un id inexistente; su propio recurso → 200; un `odontologoId` en la query no cambia el dueño. |
| `EmailUnicoIT` | Insertar `Demo@Example.com` cuando existe `demo@example.com` viola `odontologo_email_sin_mayusculas_uq`. |
| `SesionCookieIT` | Con Tomcat real (`webEnvironment = RANDOM_PORT`): `Set-Cookie` del login con `JSESSIONID`, `HttpOnly` y `SameSite=Lax`; `XSRF-TOKEN` presente y **sin** `HttpOnly`; la fábrica de servlet reporta 8 h de expiración. |

**Soporte nuevo en `src/test`**: overload de `FixturesJdbc.odontologo(...)` con `password_hash` y `activo`; `soporte/AutenticacionDePrueba` (crea odontólogos con BCrypt de fuerza 4 y devuelve sesión + token CSRF ya listos para MockMvc, para reutilizar en C-05/C-12); `ConfiguracionRelojDePrueba` se reutiliza para el bloqueo. Los controladores de prueba viven en `ar.turnotarde.soporte.prueba` y se importan con `@Import`, nunca por component scan.

**Ajustes a tests existentes** (impacto real del starter de seguridad):
- `ManejadorErroresTest` (`@WebMvcTest`) suma `@AutoConfigureMockMvc(addFilters = false)`: prueba el advice, no la seguridad, y con el starter pasaría a recibir 401.
- `EntregaSpaIT`: la ruta `/api/no-existe` sin sesión ahora espera 401; se agrega el caso con sesión de odontólogo → 404 (delta de `errores-api`).
- `DatosDemoIT` y `ContrasenaDemoInicializadorTest`: el inicializador recibe el `PasswordEncoder` por constructor.

### D13 — Archivos compartidos y coordinación con otros changes

| Archivo | Cambio | Aviso |
|---|---|---|
| `backend/build.gradle.kts` | `spring-security-crypto` → `spring-boot-starter-security` | C-03 es dueño. |
| `backend/src/main/resources/application.yml` | bloque `server.servlet.session` | — |
| `shared/error/CodigosError.java` | `NO_AUTENTICADO`, `ACCESO_DENEGADO` | — |
| `odontologo/OdontologoRepository.java` | `findByEmailIgnoreCase` | C-05/C-12 agregan las suyas. |
| `odontologo/demo/ContrasenaDemoInicializador.java` | inyecta el bean `PasswordEncoder` | Cierra el pendiente D6 de C-02. |
| `shared/error/ManejadorErroresTest`, `shared/web/EntregaSpaIT` | ver D12 | — |
| `README.md` | códigos `NO_AUTENTICADO`/`ACCESO_DENEGADO`, endpoints de sesión, límites del contador en memoria | — |

**Contratos que quedan para otros changes**: C-04 consume `POST /api/auth/login` sin CSRF, `GET /api/auth/me` como guard y `X-XSRF-TOKEN` desde la cookie; C-05/C-12/C-17 usan `OdontologoActual.id()` + `AislamientoOdontologo.exigir(...)` y no aceptan ids de odontólogo del request.

## UI Mockups

- Design system revision: 2
- Decision: no (no UI) — 2026-11-21. C-03 es solo backend: endpoints, sesión, CSRF y aislamiento. La pantalla de login y su mockup son de C-04.
- Mockups: —
- Approval: no aplica
- Preview: no aplica
- Visual verify: no aplica

## Risks / Trade-offs

- **R1** [`csrf.spa()` no se comporta como documenta Spring Security 7.1.2] → es la primera tarea del apply: un IT verifica que el login sin token pasa, que el logout sin token da 403 y que la cookie `XSRF-TOKEN` es legible. Si falla, se aplica el plan B de D4 sin tocar specs.
- **R2** [El login por controlador se olvida de persistir el `SecurityContext` o de rotar la sesión] → `SesionIT` verifica `/me` con la misma sesión y que el identificador cambie; si el enfoque no cierra, se pasa a un `AbstractAuthenticationProcessingFilter` propio.
- **R3** [El starter de seguridad rompe tests existentes] → `ManejadorErroresTest` con `addFilters = false` y `EntregaSpaIT` actualizado (D12); la suite completa corre en la verificación final.
- **R4** [Cambia el contrato de "ruta de API inexistente"] → registrado como delta de `errores-api` y en el README; el frontend de C-04 ya trata 401 como "sin sesión".
- **R5** [El contador en memoria se pierde al reiniciar y no sirve con varios nodos] → aceptado para la v0.1 (una sola instancia en la demo); C-20 define rate limiting real. Se documenta en el README.
- **R6** [Sigue habiendo una diferencia de tiempos entre email existente e inexistente] → hash ficticio (D8) y mensaje/estado idénticos; el residuo se acepta y se anota.
- **R7** [La cookie de sesión viaja sin `Secure` en la demo] → es `http://localhost`; C-20 la activa en producción.
- **R8** [`V3` falla si una base local tiene emails duplicados por mayúsculas] → no existen hoy (seed en minúsculas, sin alta de odontólogos); si pasara, se limpia el dato y se reaplica.
- **R9** [Jackson 3: el `ObjectMapper` de Boot es `tools.jackson.databind.ObjectMapper`] → se inyecta ese tipo en `EscritorErrorJson`; `EscritorErrorJsonTest` lo detecta en la primera corrida.
- **R10** [Eximir `/api/public/**` de CSRF deja la reserva pública sin esa protección] → ya está decidido en la KB (03 §Rutas públicas); no hay sesión que secuestrar y RN-PA-03/RN-TU-03 acotan el abuso.
- **R11** [El 5.º intento devuelve 429 y no 401] → decisión de D7, registrada en la spec; C-04 tiene que renderizar el aviso de bloqueo en ese mismo intento.

## Migration Plan

1. Rama `feat/c-03-auth-sesion-odontologo`.
2. Orden de apply: dependencia y `application.yml` → `CodigosError` + `shared/seguridad` (401/403 y aislamiento) → `auth` (contador, servicio, controlador, configuración) → ajustes de tests existentes → README.
3. Al arrancar, Flyway aplica `V3` sobre `turnotarde` y `turnotarde_test`. No hay migración de datos ni cambio de columnas.
4. **Rollback**: en desarrollo, `DROP SCHEMA public CASCADE; CREATE SCHEMA public;` como dueño de la base y volver a arrancar (no hay `undo` en Flyway Community ni datos reales antes de la demo). No existe perfil de producción en la v0.1.
5. Después del merge: avisar al equipo que todo `/api/**` (salvo público y login) exige sesión, que el login de la demo es `demo@example.com` + `DEMO_PASSWORD`, y que C-05/C-12 deben usar `OdontologoActual` + `AislamientoOdontologo`.

## Open Questions

- ¿`/me` tiene que devolver `telefono` y `direccionConsultorio`? C-14 los necesita para el mensaje de WhatsApp y C-06 para la configuración. Es aditivo y no cambia ninguna spec: se resuelve en el change que los consuma.
- ¿C-04 muestra el tiempo restante del bloqueo con cuenta regresiva o solo el aviso? El contrato ya expone `reintentarEnSegundos` y `bloqueadoHasta`; la decisión es de UI.
- ¿Hace falta `maximumSessions(1)` (una sola sesión por odontólogo)? Queda para después de la demo; no afecta las specs de C-03.
