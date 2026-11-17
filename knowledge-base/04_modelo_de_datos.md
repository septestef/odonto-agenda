# Modelo de Datos

## Dominios

- **Identidad:** el odontólogo y sus credenciales.
- **Agenda:** horario de atención semanal, bloqueos y prestaciones.
- **Turnos:** pacientes, turnos y los tokens de gestión.
- **Notificaciones:** registro de los emails enviados (para no duplicar recordatorios).

## ERD

```
odontologo 1───* horario_atencion
odontologo 1───* bloqueo
odontologo 1───* prestacion
odontologo 1───* paciente
odontologo 1───* turno *───1 paciente
                 turno *───1 prestacion
                 turno 1───* notificacion
                 turno 0..1──1 turno   (reprogramado_desde)
```

## Entidades

Convenciones: claves `bigint` identity; fechas y horas en `timestamptz`, guardadas en UTC y mostradas en `America/Argentina/Buenos_Aires`; columnas `creado_en` y `actualizado_en` en todas las tablas.

### odontologo
- `id` bigint PK
- `nombre`, `apellido` varchar(100) NOT NULL
- `email` varchar(255) NOT NULL UNIQUE (es el login)
- `password_hash` varchar(100) NOT NULL (BCrypt)
- `telefono` varchar(30), `direccion_consultorio` varchar(255)
- `slug` varchar(60) NOT NULL UNIQUE, formato `^[a-z0-9-]+$`, usado en la URL pública
- `activo` boolean NOT NULL DEFAULT true

### horario_atencion
- `id`, `odontologo_id` FK
- `dia_semana` smallint 1–7 (ISO: 1 = lunes)
- `hora_inicio`, `hora_fin` time NOT NULL, con CHECK `hora_inicio < hora_fin`
- Permite varias franjas por día (ej. 9–13 y 16–20). Las franjas del mismo día no se solapan; lo valida el servicio.

### bloqueo
- `id`, `odontologo_id` FK
- `inicio`, `fin` timestamptz NOT NULL, con CHECK `inicio < fin`
- `motivo` varchar(255)
- Índice (`odontologo_id`, `inicio`)

### prestacion
- `id`, `odontologo_id` FK
- `nombre` varchar(100) NOT NULL
- `duracion_minutos` smallint NOT NULL, con CHECK `> 0 AND duracion_minutos % 15 = 0`
- `activa` boolean NOT NULL DEFAULT true (baja lógica: los turnos existentes la siguen referenciando)
- UNIQUE (`odontologo_id`, `nombre`)

### paciente
- `id`, `odontologo_id` FK (los pacientes son de cada odontólogo, no globales)
- `dni` varchar(10) NOT NULL (solo dígitos, 7 u 8)
- `nombre`, `apellido` varchar(100) NOT NULL
- `telefono` varchar(30) NOT NULL (formato E.164, ej. `+5493511234567`, para armar el link `wa.me`)
- `email` varchar(255) NOT NULL
- UNIQUE (`odontologo_id`, `dni`). Al reservar con un DNI ya existente se actualizan los datos de contacto (ver RN-PA-02).

### turno
- `id`, `odontologo_id` FK, `paciente_id` FK, `prestacion_id` FK
- `inicio`, `fin` timestamptz NOT NULL (`fin = inicio + duración de la prestación` al momento de reservar)
- `estado` varchar(20) NOT NULL: `RESERVADO | CANCELADO | ATENDIDO | AUSENTE`
- `origen` varchar(20) NOT NULL: `ONLINE | MANUAL`
- `cancelado_por` varchar(20) NULL: `PACIENTE | ODONTOLOGO`; `cancelado_en` timestamptz NULL
- `reprogramado_desde_id` FK NULL a turno
- `token_hash` char(64) NOT NULL UNIQUE (SHA-256 del token de gestión; el token en claro nunca se guarda)
- `recordatorio_enviado_en` timestamptz NULL
- **Constraint anti-solapamiento**, la pieza central del modelo:
  ```sql
  CREATE EXTENSION IF NOT EXISTS btree_gist;
  ALTER TABLE turno ADD CONSTRAINT turno_sin_solapamiento
    EXCLUDE USING gist (
      odontologo_id WITH =,
      tstzrange(inicio, fin, '[)') WITH &&
    ) WHERE (estado IN ('RESERVADO','ATENDIDO','AUSENTE'));
  ```
  Los turnos cancelados no ocupan lugar. La base rechaza una superposición aunque lleguen dos requests a la vez (ver DD-03).
- Índices: (`odontologo_id`, `inicio`), y (`estado`, `inicio`) para el scheduler.

### notificacion
- `id`, `turno_id` FK
- `tipo` varchar(30): `CONFIRMACION | CANCELACION | REPROGRAMACION | RECORDATORIO`
- `destinatario` varchar(255), `enviado_en` timestamptz, `estado` varchar(20): `ENVIADO | ERROR`, `error` text NULL

## Seed data inicial

Migración `V900__seed_demo.sql`, activa solo con el perfil `demo`:
- 1 odontólogo de demo con slug `demo` y contraseña tomada de la variable `DEMO_PASSWORD`, nunca hardcodeada.
- Horario: lunes a viernes de 9 a 13 y de 16 a 20.
- Prestaciones: Consulta (30 min), Limpieza (45 min), Extracción (60 min), Arreglo de caries (45 min).
- Algunos turnos de ejemplo la semana de la demo, para que la agenda no arranque vacía.
