# CHANGES — Secuencia de Implementación

> Índice canónico de todos los changes del proyecto OdontoAgenda (agenda de turnos para odontólogos independientes en Argentina).
> Cada change es atómico: un agente puede implementarlo en una sesión (~4-6 horas).
> **Leer este archivo antes de ejecutar cualquier `/opsx:propose`.**

**Fechas duras**: hoy es 2026-11-17; **demo funcional en localhost el miércoles 2026-11-25**. Los changes C-01 a C-16 son **pre-demo**; C-17 a C-20 son **post-demo** (no se tocan antes del 25/11).

**Reglas del proyecto que rigen todos los changes**:
- La mantenibilidad manda sobre el alcance (DD-08): si falta tiempo se **recortan historias S/C, nunca los tests**.
- Stack: TypeScript vanilla + Vite (frontend), Spring Boot REST + Gradle + Hibernate + PostgreSQL + Flyway (backend). Presupuesto cero.
- **Política visual `system-and-mockups`, design system aprobado en la revisión 2** (`design-system/README.md`). Todo change que toca UI está marcado **[UI]** y necesita un mockup HTML aprobado en `openspec/changes/<name>/mockups/` **antes del apply**. Para esos changes leer también `design-system/tokens.css` y `design-system/components.html`, y registrar `## UI Mockups` en el `design.md` del change.
- Sugerencia de calendario: los mockups de C-04, C-06, C-10, C-11 y C-13 son independientes del backend; conviene diseñarlos y aprobarlos en lote durante los pasos 1-2 para que el gate visual no frene el apply.

---

## Cómo usar este documento

1. Identificá el siguiente change con `[ ]` cuyas dependencias estén todas en `[x]` (usá el árbol y los GATES).
2. Leé los archivos de "Leer antes" de la KB (`knowledge-base/`) y, si el change es **[UI]**, el design system y el mockup aprobado.
3. Ejecutá `/opsx:propose C-NN-nombre` y seguí el flujo OPSX; consultá `openspec status` para el estado real.
4. Al terminar (tests en verde incluidos), ejecutá `/opsx:archive`.
5. Marcá el checkbox del change (`[ ]` → `[x]`) en este archivo.

Governance: **CRITICO** = análisis y aprobación humana explícita antes de escribir código; **ALTO** = proponer y esperar revisión antes de escribir; **MEDIO** = implementar con checkpoints; **BAJO** = autonomía total si pasan los tests.

---

## Árbol de dependencias

```
PRE-DEMO (hasta 2026-11-25)
C-01 foundation-setup
 ├── C-02 schema-y-seed-base
 │    ├── C-03 auth-sesion-odontologo
 │    │    └── C-05 configuracion-agenda-api  (también necesita C-02)
 │    │         ├── C-06 [UI] configuracion-agenda-ui   (+ C-04)
 │    │         └── C-13 [UI] agenda-odontologo-ui      (+ C-12, C-04)
 │    └── C-07 disponibilidad-y-reserva-api
 │         ├── C-08 notificaciones-email
 │         │    ├── C-09 gestion-turno-token-api
 │         │    │    └── C-11 [UI] gestion-turno-ui     (+ C-10)
 │         │    └── C-12 agenda-odontologo-api
 │         │         └── C-13 [UI] agenda-odontologo-ui
 │         │              ├── C-14 [UI] recordatorios-y-whatsapp   (S, + C-08)
 │         │              └── C-15 [UI] turno-manual-odontologo    (S, + C-10)
 │         └── C-10 [UI] reserva-publica-ui               (+ C-04)
 └── C-04 [UI] shell-frontend-y-login                    (login E2E se verifica con C-03)

C-16 cierre-demo-y-ensayo   ← C-06, C-11, C-13 (y C-14, C-15 si entraron)

POST-DEMO (después del 2026-11-25)
C-16
 └── C-17 [UI] historia-clinica-basica        ← PRIMER change post-demo
      ├── C-18 [UI] odontograma
      └── C-20 preparacion-produccion         (+ C-19)
C-16
 └── C-19 [UI] marcar-atendido-ausente
```

### Paralelismo por fase

```
GATE 0: C-01 ✓ (cumplido 2026-11-17)
  → C-02 schema-y-seed-base             [Agente A]
  → C-04 shell-frontend-y-login         [Agente C]

GATE 1: C-02 ✓ (cumplido 2026-11-21)    ← FORK
  → C-03 auth-sesion-odontologo         [Agente A]
  → C-07 disponibilidad-y-reserva-api   [Agente B]
  → C-04 (continúa)                     [Agente C]

GATE 2: C-03 ✓ y C-07 ✓ (C-07 cumplido 2026-11-21) ← FORK
  → C-05 configuracion-agenda-api       [Agente A]
  → C-08 notificaciones-email           [Agente B]
  → C-10 reserva-publica-ui             [Agente C — si C-04 ✓]

GATE 3: C-05 ✓ y C-08 ✓                 ← FORK
  → C-12 agenda-odontologo-api          [Agente A]
  → C-09 gestion-turno-token-api        [Agente B]
  → C-06 configuracion-agenda-ui        [Agente C — si C-04 ✓ y C-05 ✓]

GATE 4: C-12 ✓, C-09 ✓, C-10 ✓
  → C-13 agenda-odontologo-ui           [Agente A — si C-12 ✓, C-05 ✓, C-04 ✓]
  → C-11 gestion-turno-ui               [Agente C — si C-09 ✓ y C-10 ✓]
  → Agente B queda libre: preparar C-16 (guion de demo, README de arranque)

GATE 5: C-13 ✓
  → C-14 recordatorios-y-whatsapp       [Agente A — S, recortable]
  → C-15 turno-manual-odontologo        [Agente B — S, recortable]

GATE 6: C-06 ✓, C-11 ✓, C-13 ✓ (+ C-14, C-15 si entraron)
  → C-16 cierre-demo-y-ensayo           [Agentes A, B, C]

GATE 7 (post-demo): C-16 ✓ y demo realizada
  → C-17 historia-clinica-basica        [Agente A — CRITICO, requiere aprobación humana]
  → C-19 marcar-atendido-ausente        [Agente B]

GATE 8: C-17 ✓
  → C-18 odontograma                    [Agente C]
  → C-20 preparacion-produccion         [Agente A — si C-19 ✓]
```

### Camino crítico (7 changes pre-demo — mínimo irreducible)

`C-01 → C-02 → C-07 → C-08 → C-09 → C-11 → C-16`

*C-11 también espera a C-10 (rama de UI pública), por lo que C-10* es co-crítico. C-03/C-05/C-06 (auth y configuración) y C-12/C-13 (agenda del odontólogo) son obligatorios para la demo (historias M) pero corren en paralelo y no alargan el camino mínimo si se respeta el plan.

Camino crítico post-demo: `C-16 → C-17 → C-18` (C-20 cierra el camino hacia producción).

### Plan óptimo con 3 agentes

1 paso ≈ 1 día calendario (paso 1 = 17/11 … paso 8 = 24/11; el 25/11 es la demo). Si un paso se atrasa, se recorta C-15 y luego C-14 (historias S), nunca los tests.

| Paso | Agente A (Backend Core) | Agente B (Backend Aux) | Agente C (Frontend) |
|------|-------------------------|------------------------|---------------------|
| 1 | C-01 foundation-setup | — (mockups en lote) | — (mockups en lote) |
| 2 | C-02 schema-y-seed-base | — | C-04 shell-frontend-y-login |
| 3 | C-03 auth-sesion-odontologo | C-07 disponibilidad-y-reserva-api | C-04 (cierre) |
| 4 | C-05 configuracion-agenda-api | C-08 notificaciones-email | C-10 reserva-publica-ui |
| 5 | C-12 agenda-odontologo-api | C-09 gestion-turno-token-api | C-06 configuracion-agenda-ui |
| 6 | C-13 agenda-odontologo-ui (API ya lista) | — (prepara guion de demo) | C-11 gestion-turno-ui |
| 7 | C-14 recordatorios-y-whatsapp (S) | C-15 turno-manual-odontologo (S) | refinamiento de UI / apoyo a C-14, C-15 |
| 8 | C-16 cierre-demo-y-ensayo | C-16 (smoke y datos de demo) | C-16 (ensayo móvil 360 px) |
| post | C-17 historia-clinica-basica | C-19 marcar-atendido-ausente | C-18 odontograma |

---

## FASE 0 — Fundación

> C-01 no depende de nada y bloquea todo. C-02 y C-04 corren en paralelo apenas termina C-01.

### [C-01] `foundation-setup`
- **Estado**: `[x]` completado (archivado 2026-11-17 como `2026-11-17-c-01-foundation-setup`; Spring Boot 4.1.2, Gradle 9.9.1, Java 25)
- **Scope**:
  - Backend: proyecto Gradle (Kotlin DSL) con Spring Boot (Java 25), paquetes `ar.turnotarde.{shared,auth,odontologo,agenda,prestacion,paciente,turno,publico,notificacion}`, perfiles `application.yml` / `application-demo.yml`.
  - `shared`: `Clock` inyectable (RN-GL-02), `@RestControllerAdvice` con respuesta `{codigo, mensaje, detalles?}`, utilidades de zona horaria `America/Argentina/Buenos_Aires` (RN-AG-06).
  - Frontend: proyecto Vite + TypeScript `strict`, proxy `/api → :8080`, Vitest configurado.
  - Tarea Gradle `copyFrontend` (build de Vite a `backend/src/main/resources/static`) y fallback de rutas SPA a `index.html` (DD-05).
  - `.env.example` versionado con todas las variables de `08_arquitectura_propuesta.md` §Variables de entorno; `.env` en `.gitignore`.
  - Base de tests de integración contra PostgreSQL local `turnotarde_test` (sin H2); resolver PA-04 (Docker/Testcontainers).
  - Flyway habilitado (sin tablas todavía) y un test de arranque del contexto.
  - Tests: contexto Spring levanta, `Clock` fijo inyectable, formato del error handler.
- **Dependencias**: ninguna
- **Governance**: BAJO
- **Leer antes**:
  - `knowledge-base/08_arquitectura_propuesta.md` §Patrones, §Estructura de directorios, §Variables de entorno, §Testing
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-05, §DD-07, §DD-08
  - `knowledge-base/05_reglas_de_negocio.md` §Excepciones globales

### [C-02] `schema-y-seed-base`
- **Estado**: `[x]` completado (archivado 2026-11-21 como `2026-11-21-c-02-schema-y-seed-base`; 27 tests unitarios y 96 de integración)
- **Scope**:
  - Migración `V2__esquema_base.sql` (convención de una versión por change, ver abajo): extensión `btree_gist` y tablas `odontologo`, `horario_atencion`, `bloqueo`, `prestacion`, `paciente`, `turno`, `notificacion` con CHECKs, UNIQUEs e índices del modelo (convención: PK `bigint identity`, `timestamptz` en UTC, `creado_en`/`actualizado_en`).
  - Constraint `turno_sin_solapamiento` (EXCLUDE USING gist, `tstzrange(inicio, fin, '[)')`, filtrando `RESERVADO|ATENDIDO|AUSENTE`) — pieza central (DD-03, RN-TU-01).
  - Entidades JPA/Hibernate y repositorios Spring Data de todas las tablas; mixin de auditoría `creado_en`/`actualizado_en`. Las entidades nunca salen por la API.
  - Migración `V900__seed_demo.sql` (perfil `demo`): odontólogo `demo` (slug `demo`, contraseña desde `DEMO_PASSWORD` con hash BCrypt generado al arranque, nunca hardcodeada), horario lun-vie 9-13 y 16-20, 4 prestaciones (30/45/60/45 min) y turnos de ejemplo la semana de la demo.
  - Tests de integración (PostgreSQL real): la constraint rechaza solapamiento, permite turnos cancelados superpuestos, CHECKs de `duracion_minutos` y de horarios, UNIQUE de DNI por odontólogo.
- **Dependencias**: `C-01`
- **Governance**: ALTO (modelo core que todo referencia; la constraint garantiza RN-TU-01 — proponer y esperar revisión antes de escribir)
- **Leer antes**:
  - `knowledge-base/04_modelo_de_datos.md` (completo: ERD, entidades, constraint, seed)
  - `knowledge-base/05_reglas_de_negocio.md` §Turnos (RN-TU-01, RN-TU-02), §Pacientes
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-01, §DD-03, §DD-07, §SU-05

---

> **Convención de migraciones (vigente desde C-02, obligatoria para todo el equipo):** cada change usa su propio número de versión de Flyway: C-03 → `V3__`, C-05 → `V5__`, C-07 → `V7__`, etc. Si un change necesita varias, `V<NN>_<k>__` (ej. `V7_1__`, `V7_2__`). `spring.flyway.out-of-order: true` está activo, así que el orden de merge no importa. Las versiones 900+ son solo datos de demo (`classpath:db/demo`, perfil `demo`). Nunca se edita una migración ya mergeada: se agrega una nueva. Detalle en `README.md` §Migraciones.

## FASE 1 — Acceso del odontólogo y shell del frontend

> C-03 (backend) y C-04 (frontend) son independientes; el login E2E se verifica cuando ambos están.

### [C-03] `auth-sesion-odontologo`
- **Estado**: `[x]` completado (archivado 2026-11-22 como `2026-11-22-c-03-auth-sesion-odontologo`)
- **Scope**:
  - US-001 backend: `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me` con Spring Security, sesión HTTP de servidor (`JSESSIONID` HttpOnly, SameSite=Lax), expiración a 8 h de inactividad (RN-AU-04), sin JWT (DD-04).
  - BCrypt (RN-AU-02); mensaje genérico 401 que no revela si el email existe; bloqueo de 15 min tras 5 intentos fallidos por email con contador en memoria y 429 con tiempo restante (RN-AU-03).
  - CSRF con `CookieCsrfTokenRepository` (header `X-XSRF-TOKEN`); `/api/public/**` y `/api/auth/login` libres, el resto exige `ROLE_ODONTOLOGO`.
  - Utilidad de aislamiento: el `odontologo_id` sale siempre de la sesión; recurso ajeno devuelve 404 (RN-SE-01).
  - Tests: login válido/ inválido, bloqueo a los 5 intentos con `Clock` fijo, logout invalida la sesión, endpoint protegido sin sesión → 401, CSRF requerido, 404 en recurso de otro odontólogo.
- **Dependencias**: `C-02`
- **Governance**: CRITICO (autenticación: análisis y aprobación humana explícita antes de escribir código)
- **Leer antes**:
  - `knowledge-base/03_actores_y_roles.md` §RBAC
  - `knowledge-base/05_reglas_de_negocio.md` §Autenticación (RN-AU-01..04), §Seguridad (RN-SE-01)
  - `knowledge-base/07_flujos_principales.md` §Flujo 1
  - `knowledge-base/08_arquitectura_propuesta.md` §Seguridad
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-04

### [C-04] `shell-frontend-y-login` **[UI]**
- **Estado**: `[x]` completado (archivado 2026-11-22 como `2026-11-22-c-04-shell-frontend-y-login`; mockups en `openspec/changes/archive/2026-11-22-c-04-shell-frontend-y-login/mockups/`)
- **Scope**:
  - `frontend/src/main.ts` y `router.ts` (router propio, rutas `/login`, `/agenda`, `/config`, `/reservar/{slug}`, `/turno/{token}`) con guard de sesión vía `GET /api/auth/me`.
  - `shared/api-client.ts` (fetch + reenvío de `X-XSRF-TOKEN`, manejo de errores `{codigo, mensaje}`), `shared/fechas.ts` (zona Buenos Aires), componentes de UI base construidos desde `design-system/tokens.css` y `components.html` (botones, inputs, modal de confirmación, toasts, layout).
  - `features/auth`: página de login (US-001) con mensaje genérico de error y estado de bloqueo; cierre de sesión.
  - Regla de seguridad frontend: texto de usuario siempre con `textContent`, nunca `innerHTML`.
  - Tests Vitest: router, api-client (CSRF y errores), utilidades de fechas.
  - **Mockup aprobado requerido antes de apply** (login + layout base), revisión 2 del design system.
- **Dependencias**: `C-01` (el smoke de login contra el backend real requiere C-03 ✓)
- **Governance**: MEDIO
- **Leer antes**:
  - `design-system/README.md`, `design-system/tokens.css`, `design-system/components.html` (revisión 2)
  - `knowledge-base/08_arquitectura_propuesta.md` §Estructura de directorios, §Seguridad
  - `knowledge-base/06_funcionalidades.md` §US-001
  - `knowledge-base/07_flujos_principales.md` §Flujo 1

---

## FASE 2 — Configuración de la agenda

### [C-05] `configuracion-agenda-api`
- **Estado**: `[x]` completado (archivado 2026-11-22 como `2026-11-22-c-05-configuracion-agenda-api`). Con C-03, C-04, C-05, C-07 y C-08 integrados en `main`: 252 tests unitarios y 251 de integración en el backend. Cumple, junto con C-08, el GATE 3.
- **Scope**:
  - US-002: `GET/PUT /api/horarios` (reemplazo semanal, varias franjas por día, rechazo de franjas solapadas o con inicio >= fin; RN-AG-01).
  - US-003: `GET/POST/PUT /api/prestaciones` y baja lógica `DELETE` (duración múltiplo de 15, RN-AG-03/04); la prestación dada de baja deja de ofrecerse pero los turnos la conservan.
  - US-004: `GET/POST/DELETE /api/bloqueos`; al crear un bloqueo con turnos en el rango, la respuesta los lista sin cancelarlos (RN-AG-02); el odontólogo los cancela por el change C-12.
  - Todas las consultas filtran por el `odontologo_id` de la sesión (RN-SE-01).
  - Tests: franjas solapadas, duración inválida, baja lógica, bloqueo con turnos existentes, aislamiento entre odontólogos.
- **Dependencias**: `C-02`, `C-03`
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` §Épica 2 (US-002, US-003, US-004)
  - `knowledge-base/05_reglas_de_negocio.md` §Agenda (RN-AG-01..06)
  - `knowledge-base/04_modelo_de_datos.md` §horario_atencion, §bloqueo, §prestacion
  - `knowledge-base/03_actores_y_roles.md` §RBAC

### [C-06] `configuracion-agenda-ui` **[UI]**
- **Estado**: `[x]` completado (archivado 2026-11-22 como `2026-11-22-c-06-configuracion-agenda-ui`; UI de horarios, prestaciones y bloqueos; 263 tests frontend en verde)
- **Scope**:
  - `features/config`: pantallas de horario semanal (franjas múltiples por día), prestaciones (alta, edición, baja) y bloqueos (rango fecha-hora; si hay turnos afectados se listan con acción de cancelar).
  - Validación en cliente (duración múltiplo de 15, inicio < fin) y mensajes de error del backend.
  - Tests Vitest de validadores y del mapeo de franjas.
  - **Mockup aprobado requerido antes de apply** (tres pantallas de configuración, móvil y escritorio).
- **Dependencias**: `C-04`, `C-05`
- **Governance**: BAJO
- **Leer antes**:
  - `design-system/tokens.css`, `design-system/components.html` (revisión 2)
  - `knowledge-base/06_funcionalidades.md` §Épica 2
  - `knowledge-base/08_arquitectura_propuesta.md` §Estructura de directorios (frontend)

---

## FASE 3 — Reserva pública y gestión del turno por el paciente

> C-07 abre el fork principal. C-08 y C-10 corren en paralelo, luego C-09/C-12 y C-06 en paralelo.

### [C-07] `disponibilidad-y-reserva-api`
- **Estado**: `[x]` completado (archivado 2026-11-21 como `2026-11-21-c-07-disponibilidad-y-reserva-api`; 131 tests unitarios y 166 de integración). Contratos JSON de los 3 endpoints, payload de eventos y tabla de errores en `openspec/changes/archive/2026-11-21-c-07-disponibilidad-y-reserva-api/design.md` (D3, D4, D9).
- **Scope**:
  - `DisponibilidadService` como función pura con `Clock`: inicios cada 15 min por franja, turno completo dentro de la franja, `t >= ahora + 24h`, `t <= hoy + APP_RESERVA_MAX_DIAS` (60), sin cruce con bloqueos ni turnos (RN-AG-05, RN-TU-03, RN-TU-11).
  - `GET /api/public/{slug}` (perfil y prestaciones activas) y `GET /api/public/{slug}/disponibilidad?prestacionId=&fecha=`; las respuestas no exponen datos de otros pacientes (RN-SE-03).
  - `POST /api/public/{slug}/turnos`: validación Bean Validation (RN-PA-04), upsert de paciente por (`odontologo_id`, `dni`) (RN-PA-02), límite de 2 turnos futuros (RN-PA-03), token de 32 bytes `SecureRandom` base64url con guardado solo del SHA-256 (RN-SE-02), `INSERT turno` `RESERVADO/ONLINE`; violación de la constraint → 409 con código `RN-TU-01` (RN-GL-01). Contrato heredado de C-02: la violación llega como SQLState `23P01` con nombre `turno_sin_solapamiento`; además, mapear defensivamente `40P01` / `CannotAcquireLockException` también a 409 `RN-TU-01`. Migraciones propias de C-07: `V7__`. El token en claro no viaja en la respuesta (201 sin token).
  - Publicación de eventos de dominio `TurnoReservado`, `TurnoCancelado`, `TurnoReprogramado` (sin consumidores todavía).
  - Tests: unitarios de disponibilidad con bordes de franja, 24 h y 60 días con `Clock` fijo; **test de integración obligatorio con dos reservas concurrentes del mismo horario: exactamente una tiene éxito**; límite de 2 turnos; DNI repetido actualiza contacto; validaciones de formato.
- **Dependencias**: `C-02`
- **Governance**: ALTO (reglas centrales de negocio y concurrencia — proponer y esperar revisión antes de escribir)
- **Leer antes**:
  - `knowledge-base/07_flujos_principales.md` §Flujo 2, §Flujo 3
  - `knowledge-base/05_reglas_de_negocio.md` §Turnos, §Pacientes, §Seguridad, §Excepciones globales
  - `knowledge-base/06_funcionalidades.md` §Épica 3 (US-005, US-006)
  - `knowledge-base/04_modelo_de_datos.md` §turno, §paciente
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-02, §DD-03, §SU-02

### [C-08] `notificaciones-email`
- **Estado**: `[x]` completado (archivado 2026-11-22 como `2026-11-22-c-08-notificaciones-email`). Con C-03, C-04, C-07 y C-08 integrados en `main`: 204 tests unitarios y 217 de integración en el backend, 184 tests de Vitest en el frontend
- **Scope**:
  - Contrato heredado de C-07: `TurnoReservado(ResumenTurno, origen, token)` se publica dentro de la transacción; el token en claro viaja **solo en memoria** dentro del evento (su `toString` lo oculta). Consumir con `@TransactionalEventListener(AFTER_COMMIT)` y nunca loguear el token ni el link. `ReservaEventosIT` tiene un listener de ejemplo. Si el proceso cae entre el commit y el envío, el paciente no recibe el link (aceptado para la demo).
  - `NotificacionService` con listeners `@TransactionalEventListener(AFTER_COMMIT)` sobre `TurnoReservado`, `TurnoCancelado`, `TurnoReprogramado` (DD-09); emails vía Gmail SMTP (`MAIL_*`, DD-06) con `JavaMailSender`.
  - Plantillas HTML en `resources/templates/mail/`: confirmación (fecha, hora, prestación, dirección del consultorio, link `{APP_BASE_URL}/turno/{token}`), cancelación y reprogramación (RN-NO-01, RN-NO-02). El token en claro llega solo por email.
  - Registro en `notificacion` con estado `ENVIADO|ERROR` y mensaje de error; una falla del SMTP no revierte el turno (RN-NO-04).
  - Envío desacoplado (`@Async` o equivalente) y un `MailSender` falso en perfil de test; documentar el límite del SMTP gratuito.
  - US-007. Tests: se envía solo después del commit, no se envía en rollback, falla del SMTP deja turno `RESERVADO` y notificación `ERROR`, contenido del email.
- **Dependencias**: `C-07`
- **Governance**: ALTO (notificaciones con datos personales y credenciales SMTP por variables de entorno)
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` §US-007
  - `knowledge-base/05_reglas_de_negocio.md` §Notificaciones (RN-NO-01..04)
  - `knowledge-base/07_flujos_principales.md` §Flujo 3 (paso 5), §Flujo 4
  - `knowledge-base/08_arquitectura_propuesta.md` §Patrones (eventos), §Variables de entorno
  - `knowledge-base/09_decisiones_y_supuestos.md` §DD-06, §DD-09

### [C-09] `gestion-turno-token-api`
- **Estado**: `[x]` completado (archivado 2026-11-22 como `2026-11-22-c-09-gestion-turno-token-api`; 277 tests unitarios y 316 de integración en `main`). Contrato del GET, tabla de errores y orden de locks en `openspec/changes/archive/2026-11-22-c-09-gestion-turno-token-api/design.md` (D1–D3). Agrega `turno.email_gestion` (V9) y la regla `RN-SE-04`.
- **Scope**:
  - `GET /api/public/turnos/{token}` (busca por `sha256(token)`; turno pasado o cancelado en solo lectura), `POST /api/public/turnos/{token}/cancelar` y `POST /api/public/turnos/{token}/reprogramar {inicio}` (US-008, US-009).
  - Cancelación en cualquier momento antes del inicio (RN-TU-05, IN-01); no se cancela ni reprograma un turno pasado → 409 `RN-TU-07`; token inexistente → 404.
  - Reprogramar en **una transacción**: cancela el original, inserta el nuevo con `reprogramado_desde_id` y token nuevo, cumple la regla de 24 h para el turno nuevo (RN-TU-06); si el insert viola la constraint, rollback completo y el original sigue `RESERVADO`.
  - Disparo de los eventos `TurnoCancelado` / `TurnoReprogramado` para los emails de C-08.
  - Nota heredada de C-02: el trigger `turno_serializar_por_odontologo` también actúa en UPDATE (reactivar/mover); agregar un test de dos reprogramaciones simultáneas del mismo horario (hoy solo se prueba INSERT concurrente) y mapear `40P01` a 409 `RN-TU-01`.
  - Tests: cancelar libera el horario de inmediato, reprogramación atómica con conflicto concurrente, turno pasado, token inválido, regla de 24 h en el turno nuevo.
- **Dependencias**: `C-07`, `C-08`
- **Governance**: ALTO (el token es la única credencial del paciente)
- **Leer antes**:
  - `knowledge-base/07_flujos_principales.md` §Flujo 4
  - `knowledge-base/06_funcionalidades.md` §Épica 4 (US-008, US-009)
  - `knowledge-base/05_reglas_de_negocio.md` §Turnos (RN-TU-05, RN-TU-06, RN-TU-07), §Seguridad (RN-SE-02)
  - `knowledge-base/10_preguntas_abiertas.md` §IN-01

### [C-10] `reserva-publica-ui` **[UI]**
- **Estado**: `[x]` archivado (2026-11-22)
- **Scope**:
  - `features/reservar`: página `/reservar/{slug}` con selección de prestación, calendario/fecha, lista de horarios libres, formulario (DNI, nombre, apellido, teléfono E.164, email) con validación, pantalla de éxito (US-005, US-006, US-007 lado UI).
  - Manejo del 409 `RN-TU-01`: mensaje "Ese horario acaba de ser tomado, elegí otro" y refresco de disponibilidad; mensaje claro por límite de 2 turnos y por fuera de ventana de 24 h/60 días.
  - Componente reutilizable de selector de horarios (lo reutilizan C-11 y C-15).
  - Usable en celular de 360 px de ancho; tests Vitest de validadores y del estado del flujo.
  - **Mockup aprobado requerido antes de apply** (selección, formulario, éxito y error de conflicto; 390x844 y 1440x900).
- **Dependencias**: `C-04`, `C-07`
- **Governance**: MEDIO
- **Leer antes**:
  - `design-system/tokens.css`, `design-system/components.html` (revisión 2)
  - `knowledge-base/06_funcionalidades.md` §Épica 3
  - `knowledge-base/07_flujos_principales.md` §Flujo 2, §Flujo 3
  - `knowledge-base/05_reglas_de_negocio.md` §Pacientes (RN-PA-03, RN-PA-04)

### [C-11] `gestion-turno-ui` **[UI]**
- **Estado**: `[ ]` pendiente
- **Scope**:
  - `features/turno`: página `/turno/{token}` con detalle del turno, cancelación con confirmación previa, reprogramación con selector de horarios de la misma prestación (reutiliza el componente de C-10) y vista en solo lectura para turnos cancelados o pasados (US-008, US-009).
  - Errores del contrato de C-09 (todos `{codigo, mensaje}`): 404 `NO_ENCONTRADO` (token inválido o desconocido, o consultorio/prestación inactivos al reprogramar); 409 `RN-TU-07` (el turno ya empezó), 409 `RN-SE-02` (turno futuro ya no reservado: solo lectura), 409 `RN-SE-04` (no se puede reprogramar desde el link; cancelar sí) y 409 `RN-TU-01` (horario tomado: refrescar disponibilidad); 422 `RN-TU-03`, `RN-TU-11` y `RN-AG-05` (horario fuera de 24 h, horizonte o agenda); 400 `VALIDACION`/`SOLICITUD_INVALIDA`.
  - Usar `puedeCancelar` y `puedeReprogramar` del GET para mostrar las acciones. Cancelar y reprogramar responden 204 sin cuerpo: después de cada acción, volver a pedir el turno con el mismo token. El link nuevo de la reprogramación llega solo por email; la página no lo recibe ni redirige al turno nuevo.
  - Los horarios para reprogramar salen de `GET /api/public/{odontologo.slug}/disponibilidad?prestacionId=&fecha=` de C-07, con los datos del GET de gestión.
  - Tests Vitest del flujo de estados.
  - **Mockup aprobado requerido antes de apply** (turno activo, confirmación de cancelación, reprogramación, solo lectura).
- **Dependencias**: `C-09`, `C-10`
- **Governance**: BAJO
- **Leer antes**:
  - `design-system/tokens.css`, `design-system/components.html` (revisión 2)
  - `knowledge-base/06_funcionalidades.md` §Épica 4
  - `knowledge-base/07_flujos_principales.md` §Flujo 4

---

## FASE 4 — Agenda del odontólogo

### [C-12] `agenda-odontologo-api`
- **Estado**: `[x]` completado (archivado 2026-11-22 como `2026-11-22-c-12-agenda-odontologo-api`; 299 tests unitarios y 416 de integración en `main`). Contratos JSON y decisiones P1–P9 en `openspec/changes/archive/2026-11-22-c-12-agenda-odontologo-api/design.md`. Specs vigentes nuevas: `agenda-odontologo`, `pacientes-odontologo` y `cancelacion-odontologo`.
- **Scope**:
  - US-010 backend: `GET /api/turnos?desde=&hasta=` para vista diaria y semanal con paciente, prestación, horario, estado y origen, filtrado por `odontologo_id` de la sesión (RN-SE-01); `GET /api/pacientes` (lectura de sus pacientes).
  - US-012: `POST /api/turnos/{id}/cancelar` (cancela cualquier turno futuro, `cancelado_por = ODONTOLOGO`, evento para el email de aviso; RN-TU-09, RN-NO-02).
  - Tests: aislamiento entre odontólogos (404), cancelación libera el horario y dispara el aviso, no se puede cancelar un turno pasado.
- **Dependencias**: `C-07`, `C-08`
- **Governance**: MEDIO
- **Leer antes**:
  - `knowledge-base/06_funcionalidades.md` §Épica 5 (US-010, US-012)
  - `knowledge-base/05_reglas_de_negocio.md` §Turnos (RN-TU-07, RN-TU-09), §Seguridad (RN-SE-01)
  - `knowledge-base/03_actores_y_roles.md` §RBAC

### [C-13] `agenda-odontologo-ui` **[UI]**
- **Estado**: `[x]` completado (archivado 2026-11-22 como `2026-11-22-c-13-agenda-odontologo-ui`; vista de día y semana, detalle y cancelación; 479 tests frontend en verde)
- **Scope**:
  - `features/agenda`: vistas diaria y semanal con navegación anterior/siguiente y botón "hoy"; cada turno muestra paciente, prestación, horario, estado y origen; se ven los bloqueos y el horario de atención (consume C-05 y C-12).
  - Acción de cancelar turno con confirmación (US-012) y listado de turnos afectados por un bloqueo.
  - Contrato de C-12 para la agenda: `GET /api/turnos?desde=&hasta=` con fechas locales `YYYY-MM-DD` y rango semiabierto `[desde, hasta)` de 1 a 31 días (vista diaria: `hasta = desde + 1`; semanal: 7 días). Devuelve un array ordenado por inicio e id con todos los estados, incluidos los cancelados, con fechas en offset de Buenos Aires. Errores: 400 `SOLICITUD_INVALIDA` (fecha ausente o inválida) y 400 `VALIDACION` (orden o rango de más de 31 días).
  - Contrato de C-12 para cancelar: `POST /api/turnos/{id}/cancelar` sin cuerpo y con CSRF (cookie `XSRF-TOKEN` y header `X-XSRF-TOKEN`). Responde 204 sin cuerpo, también si el turno ya estaba cancelado; después, volver a pedir la agenda. Errores: 404 `NO_ENCONTRADO`, 409 `RN-TU-07` (el turno ya empezó), 409 `RN-TU-01` (conflicto de lock: reintentar) y 500 `ERROR_INTERNO`.
  - Tests Vitest de la grilla horaria y de la navegación de fechas.
  - **Mockup aprobado requerido antes de apply** (vista diaria, semanal, detalle de turno; móvil y escritorio).
- **Dependencias**: `C-04`, `C-05`, `C-12`
- **Governance**: MEDIO
- **Leer antes**:
  - `design-system/tokens.css`, `design-system/components.html` (revisión 2)
  - `knowledge-base/06_funcionalidades.md` §Épica 5
  - `knowledge-base/07_flujos_principales.md` §Flujo 6
  - `knowledge-base/08_arquitectura_propuesta.md` §Estructura de directorios (frontend)

---

## FASE 5 — Opcionales de la demo (historias S, recortables)

> Si el calendario se atrasa, se recortan primero C-15 y después C-14. Los tests de los changes anteriores no se recortan.

### [C-14] `recordatorios-y-whatsapp` **[UI]**
- **Estado**: `[ ]` pendiente
- **Scope**:
  - US-014: job `@Scheduled` cada 15 min que busca turnos `RESERVADO` con inicio entre `ahora + 23h45m` y `ahora + 24h` y `recordatorio_enviado_en IS NULL`, envía el email con el link de gestión, marca `recordatorio_enviado_en` y registra la notificación; idempotente ante reinicios (RN-NO-03, RN-NO-04). Usa el `Clock` inyectado.
  - US-015: `GET /api/turnos/{id}/whatsapp` devuelve `https://wa.me/{telefono}?text={mensaje}` con fecha, hora, prestación y dirección (RN-NO-05); botón "WhatsApp" en la agenda que abre una pestaña nueva.
  - Tests: ventana de recordatorio con `Clock` fijo, no duplica envíos, armado de la URL `wa.me` con codificación correcta.
  - **Mockup aprobado requerido antes de apply** (botón/acción WhatsApp sobre el turno en la agenda).
- **Dependencias**: `C-08`, `C-13`
- **Governance**: MEDIO
- **Leer antes**:
  - `design-system/tokens.css`, `design-system/components.html` (revisión 2)
  - `knowledge-base/07_flujos_principales.md` §Flujo 5, §Flujo 6
  - `knowledge-base/05_reglas_de_negocio.md` §Notificaciones (RN-NO-03..05)
  - `knowledge-base/06_funcionalidades.md` §Épica 6

### [C-15] `turno-manual-odontologo` **[UI]**
- **Estado**: `[ ]` pendiente
- **Scope**:
  - US-011: `POST /api/turnos` (origen `MANUAL`) para cualquier horario futuro sin la regla de 24 h pero con anti-solapamiento (RN-TU-01, RN-TU-08); mismo upsert de paciente y envío del email de confirmación con link.
  - Formulario "nuevo turno" en la agenda (reutiliza el formulario de paciente y el selector de C-10); 409 `RN-TU-01` mostrado en el formulario.
  - Tests: sin regla de 24 h, rechazo por solapamiento, email enviado, aislamiento por odontólogo.
  - **Mockup aprobado requerido antes de apply** (formulario de turno manual).
- **Dependencias**: `C-10`, `C-12`, `C-13`
- **Governance**: MEDIO
- **Leer antes**:
  - `design-system/tokens.css`, `design-system/components.html` (revisión 2)
  - `knowledge-base/06_funcionalidades.md` §US-011
  - `knowledge-base/05_reglas_de_negocio.md` §Turnos (RN-TU-01, RN-TU-08)
  - `knowledge-base/10_preguntas_abiertas.md` §PA-02

---

## FASE 6 — Cierre de la demo

### [C-16] `cierre-demo-y-ensayo`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Verificar el arranque completo en localhost: `bootRun` con perfil `demo`, build del frontend copiado a `static`, PostgreSQL local, seed con turnos de ejemplo, variables `.env` completas.
  - README de arranque (pasos exactos para correr la demo en la notebook) y guion de demo que recorre: login, configuración, reserva online, email, cancelación y reprogramación desde el link, agenda diaria/semanal y, si entraron, recordatorio y WhatsApp.
  - Smoke E2E manual (checklist) incluyendo el caso de reserva concurrente y la prueba en celular de 360 px; ensayo con una cuenta Gmail de prueba.
  - Suite completa de tests (backend y frontend) en verde; congelar alcance y listar lo que quedó afuera (historias S/C no implementadas).
  - Verificación visual de cada pantalla contra su mockup aprobado (revisión 2), con reporte de diferencias.
- **Dependencias**: `C-06`, `C-11`, `C-13` (y `C-14`, `C-15` si entraron)
- **Governance**: BAJO
- **Leer antes**:
  - `knowledge-base/07_flujos_principales.md` (todos los flujos)
  - `knowledge-base/08_arquitectura_propuesta.md` §Variables de entorno, §Testing
  - `knowledge-base/06_funcionalidades.md` (checklist de historias M)

---

## FASE 7 — Post-demo (después del 2026-11-25)

> No se empieza ninguno de estos changes antes de la demo. C-17 es el **primer change después de la demo**: la suscripción comercial incluye historia clínica básica y la demo no la muestra (IN-02).

### [C-17] `historia-clinica-basica` **[UI]**
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Migración `V2`: tabla de historia clínica por paciente (entradas con fecha, motivo de consulta, diagnóstico, tratamiento realizado y notas, `turno_id` opcional, `odontologo_id`, autor y marcas de tiempo) y ficha de antecedentes/alergias por paciente. Las entradas no se borran ni se editan en silencio: las correcciones se agregan como entradas nuevas con referencia (integridad de la historia, Ley 26.529).
  - Endpoints `/api/pacientes` (búsqueda por DNI/apellido) y `/api/pacientes/{id}/historia` (listar, agregar entrada), siempre filtrados por `odontologo_id` de la sesión (404 ante recurso ajeno, RN-SE-01).
  - Registro de auditoría de accesos y cambios a datos de salud.
  - Pantallas: listado de pacientes y ficha del paciente con su historia (cronológica) y antecedentes.
  - Resolver antes del apply el análisis legal mínimo de PA-06 (Leyes 25.326, 26.529, 27.706: consentimiento, registro de la base) en lo que afecte al modelo de datos.
  - Tests: aislamiento entre odontólogos, inmutabilidad/correcciones, auditoría de acceso, validaciones.
  - **Mockup aprobado requerido antes de apply** (lista de pacientes, ficha, alta de entrada).
  - **Decisión de alcance**: el odontograma queda en un change separado (C-18). Razones: es un componente gráfico interactivo con modelo de datos propio (diente × cara × estado), lo que duplicaría el tamaño y el riesgo de C-17; la historia clínica por sí sola ya es lo que la suscripción promete como "básica" y puede entregarse y validarse con odontólogos primero.
- **Dependencias**: `C-16` (demo realizada), `C-03`, `C-07`
- **Governance**: CRITICO (datos de salud, auditoría y modelo core: análisis y aprobación humana explícita antes de escribir código)
- **Leer antes**:
  - `design-system/tokens.css`, `design-system/components.html` (revisión 2)
  - `knowledge-base/10_preguntas_abiertas.md` §IN-02, §PA-06
  - `knowledge-base/04_modelo_de_datos.md` §paciente, §turno
  - `knowledge-base/03_actores_y_roles.md` §RBAC
  - `knowledge-base/08_arquitectura_propuesta.md` §Seguridad (datos personales)

### [C-18] `odontograma` **[UI]**
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Modelo de datos: estado por diente y cara (por ejemplo, `odontograma_registro` con `paciente_id`, pieza FDI, cara, estado, fecha) con historial de cambios, vinculado a la historia clínica (C-17).
  - Endpoints `GET/PUT /api/pacientes/{id}/odontograma`, aislados por odontólogo, con auditoría.
  - Componente SVG interactivo en TypeScript vanilla accesible (teclado y táctil) dentro de la ficha del paciente.
  - Tests: validación de piezas y caras, historial, aislamiento, tests Vitest del componente.
  - **Mockup aprobado requerido antes de apply**.
- **Dependencias**: `C-17`
- **Governance**: ALTO (extiende datos de salud — proponer y esperar revisión)
- **Leer antes**:
  - `design-system/tokens.css`, `design-system/components.html` (revisión 2)
  - `knowledge-base/04_modelo_de_datos.md` §paciente
  - `knowledge-base/10_preguntas_abiertas.md` §IN-02
  - `discovery/discovery.md` (historia clínica y odontograma como funcionalidad opcional)

### [C-19] `marcar-atendido-ausente` **[UI]**
- **Estado**: `[ ]` pendiente
- **Scope**:
  - US-013 (C): `POST /api/turnos/{id}/atendido` y `/ausente`, solo para turnos `RESERVADO` cuyo inicio ya pasó (RN-TU-10); botones en la agenda.
  - Si C-17 está en `[x]`, ofrecer "agregar entrada de historia clínica" al marcar atendido.
  - Tests: transiciones válidas e inválidas con `Clock` fijo, aislamiento por odontólogo.
  - **Mockup aprobado requerido antes de apply** (estados en la agenda).
- **Dependencias**: `C-13`, `C-16`
- **Governance**: BAJO
- **Leer antes**:
  - `design-system/tokens.css`, `design-system/components.html` (revisión 2)
  - `knowledge-base/06_funcionalidades.md` §US-013
  - `knowledge-base/05_reglas_de_negocio.md` §Turnos (RN-TU-10)

### [C-20] `preparacion-produccion`
- **Estado**: `[ ]` pendiente
- **Scope**:
  - Definir hosting y proveedor de email de producción (PA-08), HTTPS, backups de PostgreSQL y política de secretos; migraciones Flyway verificadas sobre una base vacía.
  - Cumplimiento: consentimiento y política de privacidad, registro de la base ante la AAIP (PA-06), revisión de retención y acceso a datos de salud.
  - Rate limiting para la reserva pública y alta de odontólogos (hoy solo por seed, SU-05), decisión sobre alta self-service y cobro (PA-09).
  - Validación de problema y precio con odontólogos reales (PA-07). Backlog fuera de este change: lista de espera, seña con Mercado Pago, recall, caja, obras sociales, multi-profesional.
- **Dependencias**: `C-17`, `C-19`
- **Governance**: CRITICO (seguridad, datos de salud y cumplimiento legal: análisis y aprobación humana explícita)
- **Leer antes**:
  - `knowledge-base/10_preguntas_abiertas.md` §PA-05..PA-09
  - `knowledge-base/08_arquitectura_propuesta.md` §Seguridad, §Variables de entorno
  - `knowledge-base/09_decisiones_y_supuestos.md` §SU-01, §SU-05
  - `knowledge-base/01_vision_y_objetivos.md`

---

## Resumen

| Change | Nombre | Fase | Momento | UI | Governance | Depende de |
|--------|--------|------|---------|----|-----------|------------|
| C-01 | foundation-setup | 0 | Pre-demo | no | BAJO | — |
| C-02 | schema-y-seed-base | 0 | Pre-demo | no | ALTO | C-01 |
| C-03 | auth-sesion-odontologo | 1 | Pre-demo | no | CRITICO | C-02 |
| C-04 | shell-frontend-y-login | 1 | Pre-demo | sí | MEDIO | C-01 |
| C-05 | configuracion-agenda-api | 2 | Pre-demo | no | MEDIO | C-02, C-03 |
| C-06 | configuracion-agenda-ui | 2 | Pre-demo | sí | BAJO | C-04, C-05 |
| C-07 | disponibilidad-y-reserva-api | 3 | Pre-demo | no | ALTO | C-02 |
| C-08 | notificaciones-email | 3 | Pre-demo | no | ALTO | C-07 |
| C-09 | gestion-turno-token-api | 3 | Pre-demo | no | ALTO | C-07, C-08 |
| C-10 | reserva-publica-ui | 3 | Pre-demo | sí | MEDIO | C-04, C-07 |
| C-11 | gestion-turno-ui | 3 | Pre-demo | sí | BAJO | C-09, C-10 |
| C-12 | agenda-odontologo-api | 4 | Pre-demo | no | MEDIO | C-07, C-08 |
| C-13 | agenda-odontologo-ui | 4 | Pre-demo | sí | MEDIO | C-04, C-05, C-12 |
| C-14 | recordatorios-y-whatsapp (S) | 5 | Pre-demo, recortable | sí | MEDIO | C-08, C-13 |
| C-15 | turno-manual-odontologo (S) | 5 | Pre-demo, recortable | sí | MEDIO | C-10, C-12, C-13 |
| C-16 | cierre-demo-y-ensayo | 6 | Pre-demo | no | BAJO | C-06, C-11, C-13 |
| C-17 | historia-clinica-basica | 7 | **Post-demo (primero)** | sí | CRITICO | C-16, C-03, C-07 |
| C-18 | odontograma | 7 | Post-demo | sí | ALTO | C-17 |
| C-19 | marcar-atendido-ausente (C) | 7 | Post-demo | sí | BAJO | C-13, C-16 |
| C-20 | preparacion-produccion | 7 | Post-demo | no | CRITICO | C-17, C-19 |

**Primer change recomendado**: `C-01` (foundation-setup). Para arrancar: `/opsx:propose C-01-foundation-setup`. En paralelo, diseñar y aprobar en lote los mockups de C-04, C-06, C-10, C-11 y C-13.
