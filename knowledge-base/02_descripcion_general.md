# Descripción General

## Stack tecnológico

| Capa | Tecnologías | Versión mínima |
|---|---|---|
| Frontend | TypeScript sin framework (vanilla), Vite como compilador y servidor de desarrollo | TypeScript 5.x, Vite 5.x |
| Backend | Java, Spring Boot (Web, Security, Data JPA, Validation, Mail), Hibernate | Java 25 (LTS, el JDK instalado), Spring Boot compatible con Java 25 (fijar la versión al crear el proyecto) |
| Build | Gradle (Kotlin DSL), con wrapper incluido en el repo | Gradle 9.x (requerido por JDK 25) |
| Base de datos | PostgreSQL, con la extensión `btree_gist` | PostgreSQL 15+ |
| Migraciones | Flyway | La versión que gestione Spring Boot |
| Email | SMTP de Gmail con contraseña de aplicación | — |
| Tests | JUnit 5 + Spring Boot Test (backend), Vitest (frontend) | — |

Restricciones duras: el stack fue impuesto por el equipo y el presupuesto es cero, así que no se usa ningún servicio pago.

## Arquitectura general

Es un monolito con una API REST y un frontend SPA servido desde el mismo proceso.

```
 Navegador (odontólogo / paciente)
        │  HTTP (mismo origen, localhost:8080)
        ▼
 ┌─────────────────────────────── Spring Boot ───────────────────────────────┐
 │  /            → archivos estáticos (build de Vite en resources/static)    │
 │  /api/**      → controladores REST                                        │
 │                 ├─ auth          (sesión del odontólogo)                  │
 │                 ├─ agenda        (horarios, bloqueos, disponibilidad)     │
 │                 ├─ prestaciones                                           │
 │                 ├─ turnos        (reservar, cancelar, reprogramar)        │
 │                 └─ public        (reserva y gestión del paciente)         │
 │  Scheduler    → recordatorios por email                                   │
 └───────────────────────┬───────────────────────────────┬───────────────────┘
                         │ JDBC / Hibernate              │ SMTP
                         ▼                               ▼
                  PostgreSQL (local)               Gmail SMTP
```

- **Desarrollo:** Vite corre aparte (`localhost:5173`) y redirige `/api` a Spring (`localhost:8080`).
- **Demo:** una tarea de Gradle compila el frontend y copia `frontend/dist` a los recursos estáticos del backend, así que hay un solo proceso y no hace falta configurar CORS.
- **Por qué monolito:** un solo desarrollador, una demo en 8 días y la mantenibilidad como prioridad. Partir en microservicios no aporta nada a esta escala.

## Integraciones externas

| Servicio | Propósito | Tipo |
|---|---|---|
| Gmail SMTP | Emails de confirmación, cancelación y recordatorio | SMTP (Spring Mail) |
| WhatsApp (`wa.me`) | El odontólogo abre WhatsApp con el mensaje de recordatorio ya armado | Deep link en el navegador, sin API |

## API REST

Prefijo `/api`. JSON. Las rutas `public` no requieren sesión.

| Recurso | Endpoints |
|---|---|
| Auth | `POST /api/auth/login`, `POST /api/auth/logout`, `GET /api/auth/me` |
| Horarios | `GET /api/horarios`, `PUT /api/horarios` (reemplaza el horario semanal completo) |
| Bloqueos | `GET /api/bloqueos?desde&hasta`, `POST /api/bloqueos`, `DELETE /api/bloqueos/{id}` |
| Prestaciones | `GET /api/prestaciones`, `POST /api/prestaciones`, `PUT /api/prestaciones/{id}`, `DELETE /api/prestaciones/{id}` (baja lógica) |
| Turnos (odontólogo) | `GET /api/turnos?desde&hasta`, `POST /api/turnos` (carga manual), `POST /api/turnos/{id}/cancelar`, `POST /api/turnos/{id}/estado` (atendido o ausente), `GET /api/turnos/{id}/whatsapp` |
| Público | `GET /api/public/{slug}` (datos del odontólogo y prestaciones), `GET /api/public/{slug}/disponibilidad?prestacionId&fecha`, `POST /api/public/{slug}/turnos` |
| Gestión del paciente | `GET /api/public/turnos/{token}`, `POST /api/public/turnos/{token}/cancelar`, `POST /api/public/turnos/{token}/reprogramar` |

Los errores se devuelven con un formato uniforme `{ "codigo": "RN-TU-01", "mensaje": "..." }` (ver `08_arquitectura_propuesta.md`).
