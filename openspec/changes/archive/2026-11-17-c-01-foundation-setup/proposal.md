# Proposal — C-01 `foundation-setup`

## Why

El repo de OdontoAgenda todavía no tiene código: solo la KB, el roadmap y el design system. Antes de cualquier regla de negocio hace falta un esqueleto que ya cumpla las reglas duras transversales (errores uniformes `{codigo, mensaje, detalles?}`, "ahora" del servidor por `Clock`, secretos solo por entorno, tests de integración contra PostgreSQL real), así C-02 y C-04 arrancan en paralelo sin rediscutir la base. Es el primer eslabón del camino crítico y la demo es el 2026-11-25.

## What Changes

- **Backend nuevo** (`backend/`): proyecto Gradle Kotlin DSL con wrapper versionado, Spring Boot 4.1.x sobre Java 25, paquetes por funcionalidad `ar.turnotarde.{shared,auth,odontologo,agenda,prestacion,paciente,turno,publico,notificacion}` y perfiles `application.yml` / `application-demo.yml`.
- **`shared`**: bean `java.time.Clock` en UTC, utilidad de zona horaria del negocio (`America/Argentina/Buenos_Aires`, configurable por `APP_TIMEZONE`), excepción base para reglas de negocio y un único `@RestControllerAdvice` que responde siempre `{ "codigo", "mensaje", "detalles"? }`.
- **Persistencia lista pero vacía**: datasource PostgreSQL por variables de entorno, Flyway habilitado sin migraciones de tablas todavía, Hibernate con `ddl-auto=validate`.
- **Tests**: separación unitarios (`*Test`, tarea `test`) e integración (`*IT`, tarea `integrationTest`) contra la base local `turnotarde_test` en PostgreSQL 18. Se resuelve PA-04: no hay Docker, así que no se usa Testcontainers (y nunca H2).
- **Frontend nuevo** (`frontend/`): Vite + TypeScript `strict` (sin `any`, controlado por lint), proxy `/api → http://localhost:8080`, Vitest configurado con un primer módulo tipado que interpreta el formato de error de la API.
- **Integración frontend ↔ backend**: tarea Gradle `copyFrontend` que compila Vite y copia el build a los recursos estáticos de Spring; las rutas que no son `/api/**` caen en `index.html` (DD-05).
- **Configuración del repo**: `.env.example` versionado con todas las variables de la KB (más las de la base de test), `.env` y artefactos generados en `.gitignore`, script SQL para crear las bases locales y un README de arranque.

Fuera de alcance: tablas y seed (C-02), Spring Security y login (C-03), shell de UI y router (C-04), mail (C-08) y cualquier endpoint de negocio.

## Capabilities

### New Capabilities
- `errores-api`: contrato uniforme de errores de la API REST (forma del JSON, códigos de regla `RN-XX-NN` y códigos técnicos, estados HTTP, sin filtrar detalles internos).
- `tiempo-servidor`: el "ahora" lo define el reloj del servidor en UTC y las fechas del negocio se interpretan en la zona horaria configurada (RN-GL-02, RN-AG-06).
- `entrega-frontend`: cómo se sirve la SPA desde el backend en un solo origen y cómo se resuelven las rutas que no son de la API.
- `configuracion-entorno`: configuración y secretos solo por variables de entorno, esquema gestionado por migraciones y arranque de la aplicación contra PostgreSQL.

### Modified Capabilities
<!-- Ninguna: no existen specs previas en openspec/specs/. -->

## Impact

- **Código nuevo**: `backend/` (Gradle, Spring Boot, `shared/`, tests), `frontend/` (Vite, TS, Vitest, ESLint), archivos raíz `.gitignore`, `.env.example`, `README.md`.
- **Dependencias**: Spring Boot 4.1.x (Spring Framework 7, Jackson 3, Hibernate 7), Flyway + soporte PostgreSQL, driver PostgreSQL, ArchUnit (test), Gradle 9.x por wrapper; Vite, TypeScript, Vitest, ESLint + typescript-eslint.
- **Entorno local**: requiere las bases `turnotarde` y `turnotarde_test` en el servicio `postgresql-x64-18` y un `.env` local (no versionado).
- **Changes siguientes**: C-02 agrega la migración `V1` sobre este Flyway; C-03 suma Spring Security y debe mantener el formato de error; C-04 parte del scaffold de Vite.
- **KB**: `02_descripcion_general.md` y `AGENTS.md` dicen "Gradle 8.x"; con Java 25 hace falta Gradle 9.x (ver design.md). Se sugiere actualizar la KB fuera de este change.
