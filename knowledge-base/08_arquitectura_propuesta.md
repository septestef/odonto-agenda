# Arquitectura Propuesta

## Patrones aplicados

| Patrón | Dónde se usa | Por qué |
|---|---|---|
| Monolito modular, organizado por funcionalidad | `backend/.../{auth,agenda,prestacion,turno,publico,notificacion}` | Cada módulo agrupa su controlador, servicio, repositorio y DTOs. Es fácil de navegar y se puede separar más adelante |
| Capas controlador → servicio → repositorio | Todos los módulos | Las reglas de negocio viven en los servicios; los controladores solo traducen HTTP |
| DTOs inmutables (`record`) + Bean Validation | Requests y responses | Las entidades JPA nunca salen por la API |
| Integridad en la base de datos | Exclusion constraint de `turno` | El anti-solapamiento resiste la concurrencia (DD-03) |
| Eventos de dominio después del commit | `TurnoReservado`, `TurnoCancelado`, `TurnoReprogramado` → `NotificacionService` | El email nunca se manda si la transacción hizo rollback, y una falla del email no revierte el turno |
| `Clock` inyectable | Disponibilidad, reglas de 24 h, scheduler | Los tests no dependen de la hora real |
| Manejo de errores centralizado | `@RestControllerAdvice` | Respuesta uniforme `{codigo, mensaje, detalles?}` |
| Frontend por funcionalidades, con router propio | `frontend/src/features/*` | Sin framework, pero con una estructura predecible |

## Estructura de directorios

```
turnotardeutn/
├── backend/
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   ├── gradlew, gradle/
│   └── src/
│       ├── main/
│       │   ├── java/ar/turnotarde/
│       │   │   ├── TurnoTardeApplication.java
│       │   │   ├── shared/          (errores, Clock, config de seguridad, utilidades de tiempo)
│       │   │   ├── auth/
│       │   │   ├── odontologo/
│       │   │   ├── agenda/          (horarios, bloqueos, DisponibilidadService)
│       │   │   ├── prestacion/
│       │   │   ├── paciente/
│       │   │   ├── turno/
│       │   │   ├── publico/         (controladores públicos de reserva y gestión)
│       │   │   └── notificacion/    (emails, plantillas, scheduler, wa.me)
│       │   └── resources/
│       │       ├── application.yml, application-demo.yml
│       │       ├── db/migration/    (Flyway: V1__..., V900__seed_demo.sql)
│       │       ├── templates/mail/  (plantillas HTML de email)
│       │       └── static/          (generado: build de Vite, no se versiona)
│       └── test/java/ar/turnotarde/ (tests unitarios y de integración contra PostgreSQL)
├── frontend/
│   ├── package.json, tsconfig.json (strict), vite.config.ts (proxy /api → :8080)
│   ├── index.html
│   └── src/
│       ├── main.ts, router.ts
│       ├── shared/      (api-client.ts, csrf, fechas, componentes de UI, estilos)
│       └── features/
│           ├── auth/        (login)
│           ├── agenda/      (vistas diaria y semanal, turno manual)
│           ├── config/      (horarios, bloqueos, prestaciones)
│           ├── reservar/    (página pública)
│           └── turno/       (gestión por token)
├── knowledge-base/
├── discovery/
└── openspec/
```

La tarea Gradle `copyFrontend` ejecuta `npm run build` en `frontend/` y copia `dist/` a `backend/src/main/resources/static`. Las rutas de la SPA que no son `/api/**` se redirigen a `index.html`.

## Seguridad

- **Autenticación**: Spring Security con sesión del lado del servidor. La cookie es `HttpOnly` y `SameSite=Lax`, y la contraseña se guarda con BCrypt. No se usa JWT: con frontend y API en el mismo origen, la sesión es más simple y más segura (DD-04).
- **CSRF**: activado con `CookieCsrfTokenRepository`. El `api-client.ts` reenvía el header `X-XSRF-TOKEN`.
- **Autorización**: `/api/public/**` y `/api/auth/login` quedan libres; el resto requiere `ROLE_ODONTOLOGO`. El aislamiento entre odontólogos se aplica en los servicios, filtrando siempre por el `odontologo_id` de la sesión (RN-SE-01).
- **Validación de input**: Bean Validation en los DTOs y reglas de negocio en los servicios. En el frontend, el texto se inserta siempre con `textContent`, nunca con `innerHTML` sobre datos del usuario, para evitar XSS.
- **Tokens de turno**: 32 bytes de `SecureRandom`, en base64url; en la base se guarda solo su SHA-256 (RN-SE-02).
- **Rate limiting**: un contador en memoria para el login (RN-AU-03). Para la reserva pública alcanzan el límite por DNI (RN-PA-03) y la regla de 24 h.
- **Secrets**: solo por variables de entorno. Hay un `.env.example` versionado y el `.env` real está en `.gitignore`. Nunca se commitea la contraseña de aplicación de Gmail.
- **Datos personales**: se guardan los mínimos (DNI, contacto y turnos). Para pasar a producción está pendiente el análisis de las leyes 25.326, 26.529 y 27.706 (ver `10_preguntas_abiertas.md`).

## Variables de entorno

| Variable | Descripción | Ejemplo | Sensible |
|---|---|---|---|
| `DB_URL` | JDBC de PostgreSQL | `jdbc:postgresql://localhost:5432/turnotarde` | N |
| `DB_USER` | Usuario de la base | `turnotarde` | N |
| `DB_PASSWORD` | Contraseña de la base | — | **Y** |
| `MAIL_HOST` | Host SMTP | `smtp.gmail.com` | N |
| `MAIL_PORT` | Puerto SMTP (STARTTLS) | `587` | N |
| `MAIL_USERNAME` | Cuenta de Gmail que envía | `turnos.demo@gmail.com` | N |
| `MAIL_PASSWORD` | Contraseña de aplicación de Gmail | — | **Y** |
| `MAIL_FROM_NAME` | Nombre del remitente | `turnotarde` | N |
| `APP_BASE_URL` | URL base para los links de los emails | `http://localhost:8080` | N |
| `APP_TIMEZONE` | Zona horaria del negocio | `America/Argentina/Buenos_Aires` | N |
| `APP_RESERVA_MAX_DIAS` | Horizonte máximo de reserva | `60` | N |
| `DEMO_PASSWORD` | Contraseña del odontólogo de demo (perfil `demo`) | — | **Y** |
| `SPRING_PROFILES_ACTIVE` | Perfil activo | `demo` | N |

## Testing

- **Unitarios**: `DisponibilidadService` (franjas, bloqueos, 24 h, 60 días, bordes de franja) con un `Clock` fijo; las reglas de reprogramación; el armado de URLs `wa.me`.
- **Integración**: contra una base PostgreSQL local de test (`turnotarde_test`). No se usa H2, porque no soporta la exclusion constraint. Un test obligatorio lanza dos reservas concurrentes del mismo horario y verifica que exactamente una tenga éxito.
- **Frontend**: Vitest para las utilidades de fechas, el cliente de la API y el router.
