# Tasks — C-03 `auth-sesion-odontologo`

> Governance **CRITICO**: aprobado por Estefi el 2026-11-21; se ejecuta sobre la rama ya rebaseada sobre `origin/main` (C-07 mergeado). Cada tarea es de 2–4 h como máximo. TDD: el test se escribe primero y se lo ve en rojo.
> `./gradlew test -PskipFrontend` corre los unitarios; `./gradlew integrationTest -PskipFrontend` corre los `*IT` contra PostgreSQL `turnotarde_test` (nunca H2). Decisiones en `design.md` (D1–D13). Respetar lo que C-07 agregó en `OdontologoRepository` y `ManejadorErroresTest`, y mantener `/api/public/**` sin sesión ni CSRF.

## 1. Base de seguridad: dependencia, sesión, 401/403 y autorización

- [x] 1.1 Escribir primero, y verlos en rojo: `SesionPropiedadesTest` (8 h, `tracking-modes: cookie`, `HttpOnly`, `SameSite=Lax`), `EscritorErrorJsonTest` (401/403 en JSON, sin HTML, sin `detalles` vacío), `AutorizacionIT` y `CsrfIT` con los controladores de prueba en `ar.turnotarde.soporte.prueba` (`/api/prueba/protegido` y `/api/public/prueba`) según D12. Verificar: `./gradlew compileTestJava -PskipFrontend` compila y los tests fallan por la razón esperada (no existe la cadena de seguridad).
- [x] 1.2 Agregar `spring-boot-starter-security` y quitar `spring-security-crypto` explícito en `backend/build.gradle.kts`; agregar el bloque `server.servlet.session` en `application.yml` y `NO_AUTENTICADO`/`ACCESO_DENEGADO` en `CodigosError` (D1, D3, D6); implementar `EscritorErrorJson`, `ErrorApiAuthenticationEntryPoint` y `ErrorApiAccessDeniedHandler` (D6). Verificar: `EscritorErrorJsonTest` y `SesionPropiedadesTest` en verde.
- [x] 1.3 Implementar `auth/ConfiguracionSeguridad` con la cadena única (D1, D4, D5): `csrf.spa()` con `/api/public/**` y `POST /api/auth/login` exentos, matchers en el orden de D5, `sessionManagement` con `IF_REQUIRED` y `changeSessionId`, `exceptionHandling` con los handlers de 1.2, y `formLogin`/`httpBasic`/`logout`/`requestCache` deshabilitados. Ajustar `ManejadorErroresTest` (`addFilters = false`, conservando los casos de C-07) y `EntregaSpaIT` (`/api/no-existe` sin sesión → 401, más el caso con sesión → 404). Verificar: `AutorizacionIT`, `CsrfIT`, `EntregaSpaIT` y `ManejadorErroresTest` en verde, y `./gradlew integrationTest -PskipFrontend` completo sin regresiones, incluidos los IT de C-07 sobre `/api/public/**` sin sesión ni CSRF (`ReservaOnlineIT`, `DisponibilidadPublicaIT`, `PerfilPublicoIT`, `ReservasConcurrentesApiIT`).
- [x] 1.4 Documentar en `README.md` la sección de sesión (cookie `JSESSIONID`, 8 h de inactividad, `X-XSRF-TOKEN`) y las filas `NO_AUTENTICADO` (401) y `ACCESO_DENEGADO` (403) de la tabla de códigos. Verificar: los códigos y valores del README coinciden con `CodigosError` y `application.yml`.

## 2. Contrato de aislamiento (RN-SE-01)

- [x] 2.1 Escribir `OdontologoActualTest` (id del principal; sin principal → 401 `NO_AUTENTICADO`, nunca 500) y `AislamientoOdontologoTest` (`Optional.empty()` → 404 `NO_ENCONTRADO`; presente → el recurso) y verlos en rojo.
- [x] 2.2 Implementar `shared/seguridad/OdontologoAutenticado` (UserDetails con `id`, `activo` y `ROLE_ODONTOLOGO`), `OdontologoActual` y `AislamientoOdontologo.exigir(...)` según D9. Verificar: los dos tests en verde y `SinHoraDelSistemaTest` sigue pasando.

## 3. Contador de intentos fallidos con `Clock`

- [x] 3.1 Escribir `ControlIntentosLoginTest` con todos los escenarios de D7 y D12 (1–4 fallos no bloquean, el 5.º bloquea 900 s, vencimiento exacto con `RelojDePrueba`, éxito que reinicia, emails independientes, normalización de mayúsculas y espacios, email inexistente también bloqueado, purga de entradas vencidas) y verlo en rojo.
- [x] 3.2 Implementar `auth/ControlIntentosLogin` (mapa concurrente, operaciones con `compute`, `Instant.now(reloj)`, constantes 5/15 min, purga) según D7. Verificar: `ControlIntentosLoginTest` en verde.

## 4. Login, logout y sesión

- [x] 4.1 Escribir `LoginIT`, `BloqueoLoginIT`, `SesionIT` y `MaximoBytesUtf8Test` (escenarios de `autenticacion-odontologo` y de D12) y el soporte que necesitan: overload de `FixturesJdbc.odontologo(...)` con hash y `activo`, y `soporte/AutenticacionDePrueba` (crea odontólogos con BCrypt de fuerza 4 y devuelve sesión + token CSRF listos para MockMvc). El caso explícito: contraseña de **72 caracteres con una `ñ`** (73 bytes UTF-8) → 400 `VALIDACION` con `campo` `password`; 72 bytes ASCII pasan. Verificar: compilan y fallan en rojo.
- [x] 4.2 Implementar los DTOs (`PeticionLogin`, `OdontologoDto`, `DetalleBloqueo`), la restricción `@MaximoBytesUtf8(72)` con su validador (mide `getBytes(StandardCharsets.UTF_8)`, no caracteres), `OdontologoServicioDetalles` (busca por email insensible a mayúsculas), `ServicioAutenticacion` (bloqueo → autenticación → rotación de sesión → **contexto nuevo con `SecurityContextHolder.createEmptyContext()`** persistido → recarga del odontólogo para `/me`, D3 y D8, incluido el hash ficticio para igualar tiempos) y `ControladorAuth` con los tres endpoints (D2). Completar `ConfiguracionSeguridad` con `PasswordEncoder` BCrypt sin prefijo, `DaoAuthenticationProvider`, `AuthenticationManager`, `SessionAuthenticationStrategy` y `SecurityContextRepository`; hacer que `ContrasenaDemoInicializador` use el bean y actualizar `ContrasenaDemoInicializadorTest`. Verificar: `LoginIT`, `BloqueoLoginIT`, `SesionIT`, `MaximoBytesUtf8Test`, `DatosDemoIT` y `ContrasenaDemoInicializadorTest` en verde, y `CodificacionContrasenaTest` confirmando el prefijo `$2` sin `{bcrypt}`.
- [x] 4.3 Escribir y pasar `SesionCookieIT` con Tomcat real (`webEnvironment = RANDOM_PORT`): `Set-Cookie` del login con `JSESSIONID`, `HttpOnly` y `SameSite=Lax`; `XSRF-TOKEN` presente y sin `HttpOnly`; 8 h de expiración en la configuración efectiva del servidor. Verificar: el IT pasa.
- [x] 4.4 Documentar en `README.md` los tres endpoints de sesión y los límites del contador en memoria (por nodo, se pierde al reiniciar, C-20 resuelve rate limiting real). Verificar: el README describe el contrato que exponen los tests.

## 5. Identidad de login insensible a mayúsculas

- [x] 5.1 Escribir `EmailUnicoIT` (insertar `Demo@Example.com` con `demo@example.com` existente viola `odontologo_email_sin_mayusculas_uq`) y verlo en rojo.
- [x] 5.2 Agregar `OdontologoRepository.findByEmailIgnoreCase` (si no entró en 4.2) y `V3__email_odontologo_unico_sin_mayusculas.sql` con el índice único sobre `lower(email)` (D10); ampliar `ArranqueAplicacionIT` para que la versión `3` figure en el historial de Flyway. Verificar: `EmailUnicoIT` y `ArranqueAplicacionIT` en verde y `LoginIT` sigue pasando con emails en mayúsculas.

## 6. Aislamiento entre odontólogos de punta a punta y verificación final

- [x] 6.1 Escribir y pasar `AislamientoOdontologoIT` con `ControladorAislamientoDePrueba` (recurso de `prestacion` filtrado por el odontólogo de la sesión): A pide un recurso de B → 404 con el mismo cuerpo que un id inexistente; su propio recurso → 200; un `odontologoId` en la query no cambia el dueño. Verificar: el IT pasa.
- [x] 6.2 Correr `./gradlew clean build` desde `backend/` (unitarios, ArchUnit, integración y frontend embebido). Verificar: termina en verde, sin tests salteados.
- [x] 6.3 Revisar que no se versionó ningún secreto ni hash (`git diff --stat`, búsqueda de `DEMO_PASSWORD=` y de `$2a$` en archivos nuevos), que ninguna entidad JPA sale por la API y que el código nuevo no llama a `now()` sin reloj. Verificar: las búsquedas no devuelven resultados y `SinHoraDelSistemaTest` pasa.
- [x] 6.4 Registrar en `design.md` la sección "Desvíos del apply" (si los hubo) y correr `openspec validate c-03-auth-sesion-odontologo --strict`. Verificar: la validación pasa.

## Workflow follow-up

- Aprobación humana explícita del `design.md` (governance CRITICO) antes de empezar el apply.
- Avisar los contratos al equipo: C-04 (login sin CSRF, `/me` como guard, `X-XSRF-TOKEN` desde la cookie) y C-05/C-12/C-17 (`OdontologoActual.id()` + `AislamientoOdontologo.exigir(...)`, ids de odontólogo del request ignorados).
- Confirmar que la demo entra con `demo@example.com` + `DEMO_PASSWORD` y que el 5.º intento fallido muestra el bloqueo (429) en la UI de C-04.
- Archivar con `/opsx:archive c-03-auth-sesion-odontologo` y marcar `[x]` C-03 en `CHANGES.md`.
