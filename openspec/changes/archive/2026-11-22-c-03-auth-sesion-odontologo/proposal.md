# Proposal — C-03 `auth-sesion-odontologo`

## Why

El backend todavía no tiene autenticación: C-02 dejó la tabla `odontologo` con `password_hash` en BCrypt "pelado", pero nada lo valida, y `spring-boot-starter-security` ni siquiera está en el classpath. Sin login no se puede abrir la demo (US-001, Flujo 1) y ningún endpoint protegido de C-05 y C-12 tiene dueño del recurso. Además RN-AU-01..04 y RN-SE-01 no están implementadas en ninguna capa: hoy cualquiera podría leer la agenda de cualquier odontólogo.

## What Changes

- **Endpoints de sesión** con DTOs `record` y Bean Validation: `POST /api/auth/login` (`{email, password}` → 200 con el odontólogo de la sesión), `POST /api/auth/logout` (204, invalida la sesión) y `GET /api/auth/me` (200 con el odontólogo de la sesión).
- **Sesión HTTP de servidor** (DD-04, sin JWT): cookie `JSESSIONID` `HttpOnly` y `SameSite=Lax`, `tracking-modes: cookie`, expiración a las **8 h de inactividad** (RN-AU-04) y rotación del id de sesión al autenticar (protección contra fijación de sesión).
- **Contraseñas con BCrypt** (RN-AU-02): `BCryptPasswordEncoder` como bean, sin prefijo `{bcrypt}`, respetando el contrato que dejó C-02. El inicializador de la contraseña de demo pasa a usar ese bean.
- **Bloqueo por fuerza bruta** (RN-AU-03): contador en memoria por email con el `Clock` inyectado; al 5.º intento fallido seguido el login responde **429** con el tiempo restante y queda bloqueado 15 minutos. Se cuentan también los emails inexistentes, para no filtrar qué cuentas existen.
- **Mensaje genérico** en el 401 de credenciales: mismo `codigo`, mismo `mensaje` y misma latencia percibida para email inexistente, contraseña incorrecta y cuenta inactiva.
- **CSRF** con `CookieCsrfTokenRepository` (header `X-XSRF-TOKEN`): `/api/public/**` y `/api/auth/login` exentos; todo lo demás bajo `/api/**` exige `ROLE_ODONTOLOGO`. Los recursos estáticos y las rutas de la SPA quedan públicos.
- **Errores 401 y 403 en el formato uniforme** `{codigo, mensaje, detalles?}`: `NO_AUTENTICADO` (sin sesión), `ACCESO_DENEGADO` (CSRF/rol) y `RN-AU-01` / `RN-AU-03` para el login. Nada de redirects a un formulario de login ni de HTML.
- **Contrato de aislamiento para C-05 y C-12** (RN-SE-01): el `odontologo_id` sale siempre de la sesión (`OdontologoActual`), nunca del request, y un helper único traduce "recurso de otro odontólogo" a **404 `NO_ENCONTRADO`**.
- **Migración `V3`**: índice único sobre `lower(email)` en `odontologo`, para que el identificador de login no admita dos cuentas que solo difieren en mayúsculas.
- **Tests**: unitarios del contador de intentos con `Clock` fijo y de los handlers de 401/403; integración contra PostgreSQL real (`turnotarde_test`) de login válido/inválido, bloqueo, logout, CSRF, autorización por rol, cookies y sesión, y aislamiento entre odontólogos.

## Capabilities

### New Capabilities
- `autenticacion-odontologo`: cómo el odontólogo inicia y cierra sesión, cómo se validan sus credenciales, el mensaje genérico del 401 y el bloqueo temporal por intentos fallidos (RN-AU-01..03).
- `sesion-odontologo`: la sesión de servidor (cookie, expiración por inactividad), la protección CSRF y las reglas de autorización de `/api/**` con sus respuestas 401/403 en JSON (RN-AU-04, DD-04).
- `aislamiento-odontologo`: el mecanismo por el que el `odontologo_id` sale de la sesión y un recurso de otro odontólogo se responde 404, reutilizable por C-05, C-12 y C-17 (RN-SE-01).

### Modified Capabilities
- `errores-api`: una ruta bajo `/api/**` que no existe deja de responder siempre 404. Sin sesión responde 401 `NO_AUTENTICADO` (la autorización corre antes del ruteo y no se enumeran rutas para anónimos); con sesión de odontólogo sigue respondiendo 404 `NO_ENCONTRADO`. Se agrega además el requisito explícito de que 401 y 403 respeten el formato uniforme, sin HTML ni redirects.

## Impact

- **Código nuevo** (backend): paquete `auth` (`ControladorAuth`, `ServicioAutenticacion`, `ControlIntentosLogin`, `ConfiguracionSeguridad`, `OdontologoServicioDetalles`, DTOs) y `shared/seguridad` (`OdontologoAutenticado`, `OdontologoActual`, `AislamientoOdontologo`, `ErrorApiAuthenticationEntryPoint`, `ErrorApiAccessDeniedHandler`, `EscritorErrorJson`).
- **Archivos compartidos que toca**: `backend/build.gradle.kts` (cambia `spring-security-crypto` por `spring-boot-starter-security`), `application.yml` (bloque `server.servlet.session`), `shared/error/CodigosError` (`NO_AUTENTICADO`, `ACCESO_DENEGADO`), `OdontologoRepository` (`findByEmailIgnoreCase`), `odontologo/demo/ContrasenaDemoInicializador` (usa el bean `PasswordEncoder`), `README.md` (tabla de códigos de error y sección de sesión), `ManejadorErroresTest` (slice web sin filtros de seguridad) y `EntregaSpaIT` (ruta de API inexistente sin sesión).
- **Migración nueva**: `V3__email_odontologo_unico_sin_mayusculas.sql` (sin tablas nuevas; C-03 no necesita esquema propio).
- **Dependencias**: `org.springframework.boot:spring-boot-starter-security` (Spring Security 7.1.2, versión del BOM de Boot 4.1.2). `spring-security-crypto` deja de declararse explícitamente porque llega transitiva.
- **Changes que dependen de esto**: C-04 (login E2E y guard de sesión), C-05 y C-12 (aislamiento por `odontologo_id` de la sesión), C-17 (mismo contrato).
- **Sin cambios** de UI ni de frontend: C-03 es solo backend; el mockup y la pantalla de login son de C-04.
