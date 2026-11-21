-- C-02 schema-y-seed-base: esquema relacional del negocio (knowledge-base/04_modelo_de_datos.md).
-- Convenciones: PK bigint identity, instantes en timestamptz (UTC), auditoría sin DEFAULT
-- (la completa la aplicación con el Clock inyectado) y FKs sin ON DELETE CASCADE.

CREATE EXTENSION IF NOT EXISTS btree_gist WITH SCHEMA public;

CREATE TABLE odontologo (
    id                    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre                varchar(100) NOT NULL,
    apellido              varchar(100) NOT NULL,
    email                 varchar(255) NOT NULL,
    password_hash         varchar(100) NOT NULL,
    telefono              varchar(30),
    direccion_consultorio varchar(255),
    slug                  varchar(60)  NOT NULL,
    activo                boolean      NOT NULL DEFAULT true,
    creado_en             timestamptz  NOT NULL,
    actualizado_en        timestamptz  NOT NULL,
    CONSTRAINT odontologo_email_uk UNIQUE (email),
    CONSTRAINT odontologo_slug_uk UNIQUE (slug),
    CONSTRAINT odontologo_slug_formato CHECK (slug ~ '^[a-z0-9-]+$')
);

CREATE TABLE horario_atencion (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    odontologo_id  bigint      NOT NULL REFERENCES odontologo (id),
    dia_semana     smallint    NOT NULL,
    hora_inicio    time        NOT NULL,
    hora_fin       time        NOT NULL,
    creado_en      timestamptz NOT NULL,
    actualizado_en timestamptz NOT NULL,
    CONSTRAINT horario_dia_semana CHECK (dia_semana BETWEEN 1 AND 7),
    CONSTRAINT horario_franja CHECK (hora_inicio < hora_fin)
);
CREATE INDEX horario_atencion_odontologo_idx ON horario_atencion (odontologo_id, dia_semana);

CREATE TABLE bloqueo (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    odontologo_id  bigint      NOT NULL REFERENCES odontologo (id),
    inicio         timestamptz NOT NULL,
    fin            timestamptz NOT NULL,
    motivo         varchar(255),
    creado_en      timestamptz NOT NULL,
    actualizado_en timestamptz NOT NULL,
    CONSTRAINT bloqueo_inicio_antes_de_fin CHECK (inicio < fin)
);
CREATE INDEX bloqueo_odontologo_inicio_idx ON bloqueo (odontologo_id, inicio);

CREATE TABLE prestacion (
    id               bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    odontologo_id    bigint       NOT NULL REFERENCES odontologo (id),
    nombre           varchar(100) NOT NULL,
    duracion_minutos smallint     NOT NULL,
    activa           boolean      NOT NULL DEFAULT true,
    creado_en        timestamptz  NOT NULL,
    actualizado_en   timestamptz  NOT NULL,
    CONSTRAINT prestacion_duracion CHECK (duracion_minutos > 0 AND duracion_minutos % 15 = 0),
    CONSTRAINT prestacion_nombre_por_odontologo_uk UNIQUE (odontologo_id, nombre)
);

CREATE TABLE paciente (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    odontologo_id  bigint       NOT NULL REFERENCES odontologo (id),
    dni            varchar(10)  NOT NULL,
    nombre         varchar(100) NOT NULL,
    apellido       varchar(100) NOT NULL,
    telefono       varchar(30)  NOT NULL,
    email          varchar(255) NOT NULL,
    creado_en      timestamptz  NOT NULL,
    actualizado_en timestamptz  NOT NULL,
    CONSTRAINT paciente_dni_por_odontologo_uk UNIQUE (odontologo_id, dni),
    CONSTRAINT paciente_dni_formato CHECK (dni ~ '^[0-9]{7,8}$')
);

CREATE TABLE turno (
    id                      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    odontologo_id           bigint      NOT NULL REFERENCES odontologo (id),
    paciente_id             bigint      NOT NULL REFERENCES paciente (id),
    prestacion_id           bigint      NOT NULL REFERENCES prestacion (id),
    inicio                  timestamptz NOT NULL,
    fin                     timestamptz NOT NULL,
    estado                  varchar(20) NOT NULL,
    origen                  varchar(20) NOT NULL,
    cancelado_por           varchar(20),
    cancelado_en            timestamptz,
    reprogramado_desde_id   bigint REFERENCES turno (id),
    token_hash              char(64)    NOT NULL,
    recordatorio_enviado_en timestamptz,
    creado_en               timestamptz NOT NULL,
    actualizado_en          timestamptz NOT NULL,
    CONSTRAINT turno_inicio_antes_de_fin CHECK (inicio < fin),
    CONSTRAINT turno_estado_valido CHECK (estado IN ('RESERVADO', 'CANCELADO', 'ATENDIDO', 'AUSENTE')),
    CONSTRAINT turno_origen_valido CHECK (origen IN ('ONLINE', 'MANUAL')),
    CONSTRAINT turno_cancelado_por_valido CHECK (cancelado_por IN ('PACIENTE', 'ODONTOLOGO')),
    CONSTRAINT turno_token_hash_uk UNIQUE (token_hash)
);
CREATE INDEX turno_odontologo_inicio_idx ON turno (odontologo_id, inicio);
CREATE INDEX turno_estado_inicio_idx ON turno (estado, inicio);

-- RN-TU-01 (DD-03): un odontólogo no puede tener dos turnos activos superpuestos.
-- El rango [) deja contiguos 10:00-10:30 y 10:30-11:00; los CANCELADOS no ocupan lugar.
-- La violación llega como SQLState 23P01 con este nombre: es contrato con C-07, no se renombra.
ALTER TABLE turno ADD CONSTRAINT turno_sin_solapamiento
    EXCLUDE USING gist (odontologo_id WITH =, tstzrange(inicio, fin, '[)') WITH &&)
    WHERE (estado IN ('RESERVADO', 'ATENDIDO', 'AUSENTE'));

-- Serializa las escrituras de turnos activos de un mismo odontologo. Sin esto, dos transacciones
-- que insertan a la vez un turno superpuesto se esperan mutuamente al verificar la exclusion
-- constraint y PostgreSQL aborta una con 40P01 (deadlock_detected) en lugar de 23P01. Con el lock
-- advisory, la segunda espera a que la primera confirme y recien ahi la constraint la rechaza con
-- 23P01 / turno_sin_solapamiento de forma determinista. La garantia sigue siendo la constraint.
CREATE FUNCTION turno_serializar_por_odontologo() RETURNS trigger
    LANGUAGE plpgsql AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('turno_sin_solapamiento'), hashtext(NEW.odontologo_id::text));
    RETURN NEW;
END;
$$;

CREATE TRIGGER turno_serializar_por_odontologo
    BEFORE INSERT OR UPDATE OF estado, inicio, fin, odontologo_id ON turno
    FOR EACH ROW
    WHEN (NEW.estado IN ('RESERVADO', 'ATENDIDO', 'AUSENTE'))
    EXECUTE FUNCTION turno_serializar_por_odontologo();

CREATE TABLE notificacion (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    turno_id       bigint       NOT NULL REFERENCES turno (id),
    tipo           varchar(30)  NOT NULL,
    destinatario   varchar(255) NOT NULL,
    enviado_en     timestamptz  NOT NULL,
    estado         varchar(20)  NOT NULL,
    error          text,
    creado_en      timestamptz  NOT NULL,
    actualizado_en timestamptz  NOT NULL,
    CONSTRAINT notificacion_tipo_valido
        CHECK (tipo IN ('CONFIRMACION', 'CANCELACION', 'REPROGRAMACION', 'RECORDATORIO')),
    CONSTRAINT notificacion_estado_valido CHECK (estado IN ('ENVIADO', 'ERROR'))
);
CREATE INDEX notificacion_turno_idx ON notificacion (turno_id, tipo);
