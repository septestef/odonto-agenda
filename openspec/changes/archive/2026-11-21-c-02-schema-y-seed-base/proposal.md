# Proposal — C-02 `schema-y-seed-base`

## Why

Todo el camino crítico de la demo (C-07 → C-08 → C-09 → C-11 → C-16) y el login de C-03 necesitan tablas, entidades y repositorios que hoy no existen: C-01 dejó Flyway corriendo sin migraciones y `ddl-auto=validate` sin entidades. Además, la regla central del producto (RN-TU-01: nunca dos turnos superpuestos del mismo odontólogo) tiene que quedar garantizada por la base desde el primer día (DD-03), antes de que cualquier change escriba turnos.

## What Changes

- Migración `V2__esquema_base.sql`: extensión `btree_gist` y las 7 tablas del modelo (`odontologo`, `horario_atencion`, `bloqueo`, `prestacion`, `paciente`, `turno`, `notificacion`) con PK `bigint` identity, `timestamptz` en UTC, `creado_en`/`actualizado_en`, CHECKs, UNIQUEs e índices de `04_modelo_de_datos.md`.
- Exclusion constraint `turno_sin_solapamiento` (GiST, `tstzrange(inicio, fin, '[)')`, solo estados `RESERVADO|ATENDIDO|AUSENTE`): la pieza central.
- Entidades JPA y repositorios Spring Data de las 7 tablas, con una superclase de auditoría que completa `creado_en`/`actualizado_en` desde el `Clock` inyectado. Las entidades no salen por la API (C-02 no agrega controladores).
- Convención de numeración de migraciones para trabajo en paralelo: cada change usa su número (`V2__` para C-02, `V7__` para C-07…), con `spring.flyway.out-of-order=true`. **Desvío de CHANGES.md**, que decía `V1`.
- Seed de demo `V900__seed_demo.sql` en una ubicación de Flyway que solo se activa con el perfil `demo`: odontólogo `demo`, horario lun-vie 9-13 y 16-20, 4 prestaciones, pacientes ficticios y turnos de ejemplo en la semana de la demo (23 al 27/11/2026).
- Contraseña del odontólogo de demo: la migración deja un hash inutilizable y un inicializador del perfil `demo` guarda el hash BCrypt de `DEMO_PASSWORD` al arrancar. Si `DEMO_PASSWORD` falta, la aplicación no arranca. Se agrega solo `spring-security-crypto` (no el starter de seguridad, que es de C-03).
- Tests de integración contra PostgreSQL real (`turnotarde_test`): constraint anti-solapamiento (superposición rechazada, turnos contiguos permitidos, cancelados superpuestos permitidos, odontólogos distintos permitidos, **dos inserciones concurrentes: gana exactamente una**), CHECKs, UNIQUEs, auditoría con `Clock` fijo, seed de demo y el escenario pendiente de C-01 "Mapeo que no coincide".

## Capabilities

### New Capabilities
- `modelo-datos`: esquema relacional del negocio, sus restricciones de integridad (CHECKs, UNIQUEs, claves foráneas), su mapeo a entidades y repositorios, la auditoría de fechas con el reloj del servidor y la convención de versionado de migraciones.
- `turnos-sin-solapamiento`: garantía en la base de que un odontólogo no tiene dos turnos activos superpuestos, incluso con reservas concurrentes (RN-TU-01, DD-03).
- `datos-demo`: datos iniciales del perfil `demo` (odontólogo, horario, prestaciones, turnos de ejemplo) y el manejo seguro de su contraseña.

### Modified Capabilities
- `configuracion-entorno`: el requisito "Esquema gestionado por migraciones" cambia: con C-02 una base vacía queda con las tablas de negocio después de migrar (el escenario de C-01 decía que no se creaban), y las migraciones de distintos changes se aplican aunque lleguen fuera de orden.

## Impact

- **Código nuevo** (backend): `db/migration/V2__esquema_base.sql`, `db/demo/V900__seed_demo.sql`, entidades y repositorios en los paquetes `odontologo`, `agenda`, `prestacion`, `paciente`, `turno`, `notificacion`; `shared/persistencia` (auditoría); inicializador de la contraseña de demo.
- **Archivos compartidos que toca**: `backend/build.gradle.kts` (C-02 es dueño; agrega `spring-security-crypto`), `application.yml` (`out-of-order`), `application-demo.yml` (ubicación del seed y `DEMO_PASSWORD`), `ArranqueAplicacionIT` (el test "no hay tablas de negocio" deja de valer), `README.md` (sección de migraciones).
- **Dependencias**: `org.springframework.security:spring-security-crypto` (versión del BOM de Boot 4.1.2).
- **Changes que dependen de esto**: C-03 (login sobre `odontologo.password_hash`, BCrypt sin prefijo `{bcrypt}`), C-05, C-07 (traduce la violación `turno_sin_solapamiento` a 409 `RN-TU-01`), C-08 (`notificacion`), C-14 (`recordatorio_enviado_en`).
- **Sin cambios** de API REST, frontend ni UI.
