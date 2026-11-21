-- Datos de demostración (C-02). Solo se aplica con el perfil `demo` (carpeta db/demo, versiones 900+).
-- Todo es ficticio: emails @example.com, DNIs inventados. Sin secretos:
--   * password_hash queda en un valor que NO es BCrypt, así que nadie puede iniciar sesión hasta que
--     el inicializador del perfil demo guarde el hash de DEMO_PASSWORD al arrancar (design D7).
--   * token_hash son hashes aleatorios cuyo token en claro nadie conoce: esos links no se pueden usar.
-- Las fechas son fijas (semana de la demo, lunes 23 a viernes 27/11/2026, hora de Buenos Aires).
-- Los ids no se fijan: las referencias se resuelven por slug, DNI y nombre de prestación.

INSERT INTO odontologo (nombre, apellido, email, password_hash, telefono, direccion_consultorio, slug, activo,
                        creado_en, actualizado_en)
VALUES ('Demo', 'Odontóloga', 'demo@example.com', '!PENDIENTE-DEMO_PASSWORD', '+5493510000000',
        'Calle Ficticia 123, Córdoba', 'demo', true,
        timestamptz '2026-11-21 00:00:00+00', timestamptz '2026-11-21 00:00:00+00');

-- Lunes a viernes, 9-13 y 16-20.
INSERT INTO horario_atencion (odontologo_id, dia_semana, hora_inicio, hora_fin, creado_en, actualizado_en)
SELECT o.id, d.dia, f.hora_inicio, f.hora_fin,
       timestamptz '2026-11-21 00:00:00+00', timestamptz '2026-11-21 00:00:00+00'
FROM odontologo o
CROSS JOIN generate_series(1, 5) AS d(dia)
CROSS JOIN (VALUES (time '09:00', time '13:00'), (time '16:00', time '20:00')) AS f(hora_inicio, hora_fin)
WHERE o.slug = 'demo';

INSERT INTO prestacion (odontologo_id, nombre, duracion_minutos, activa, creado_en, actualizado_en)
SELECT o.id, p.nombre, p.duracion, true,
       timestamptz '2026-11-21 00:00:00+00', timestamptz '2026-11-21 00:00:00+00'
FROM odontologo o
CROSS JOIN (VALUES ('Consulta', 30), ('Limpieza', 45), ('Extracción', 60), ('Arreglo de caries', 45))
    AS p(nombre, duracion)
WHERE o.slug = 'demo';

INSERT INTO paciente (odontologo_id, dni, nombre, apellido, telefono, email, creado_en, actualizado_en)
SELECT o.id, p.dni, p.nombre, p.apellido, p.telefono, p.email,
       timestamptz '2026-11-21 00:00:00+00', timestamptz '2026-11-21 00:00:00+00'
FROM odontologo o
CROSS JOIN (VALUES
    ('40111222', 'Lucía',  'Fernández', '+5493515550101', 'lucia.fernandez@example.com'),
    ('38222333', 'Martín', 'Rojas',     '+5493515550102', 'martin.rojas@example.com'),
    ('35333444', 'Sofía',  'Benítez',   '+5493515550103', 'sofia.benitez@example.com'),
    ('42444555', 'Tomás',  'Aguirre',   '+5493515550104', 'tomas.aguirre@example.com'))
    AS p(dni, nombre, apellido, telefono, email)
WHERE o.slug = 'demo';

-- Turnos de ejemplo. Ningún paciente tiene más de 2 turnos RESERVADOS (RN-PA-03); el miércoles hay dos
-- turnos contiguos y uno cancelado; el viernes queda libre y casi todo el jueves también.
INSERT INTO turno (odontologo_id, paciente_id, prestacion_id, inicio, fin, estado, origen, token_hash,
                   creado_en, actualizado_en)
SELECT o.id, pa.id, pr.id,
       v.inicio::timestamp AT TIME ZONE 'America/Argentina/Buenos_Aires',
       v.fin::timestamp AT TIME ZONE 'America/Argentina/Buenos_Aires',
       v.estado, v.origen,
       encode(sha256(uuid_send(gen_random_uuid())), 'hex'),
       timestamptz '2026-11-21 00:00:00+00', timestamptz '2026-11-21 00:00:00+00'
FROM (VALUES
    ('40111222', 'Consulta',          '2026-11-23 09:00', '2026-11-23 09:30', 'RESERVADO', 'ONLINE'),
    ('38222333', 'Limpieza',          '2026-11-23 16:00', '2026-11-23 16:45', 'RESERVADO', 'MANUAL'),
    ('35333444', 'Extracción',        '2026-11-24 10:00', '2026-11-24 11:00', 'RESERVADO', 'ONLINE'),
    ('42444555', 'Consulta',          '2026-11-25 09:00', '2026-11-25 09:30', 'RESERVADO', 'ONLINE'),
    ('40111222', 'Arreglo de caries', '2026-11-25 09:30', '2026-11-25 10:15', 'RESERVADO', 'ONLINE'),
    ('35333444', 'Limpieza',          '2026-11-25 11:00', '2026-11-25 11:45', 'CANCELADO', 'ONLINE'),
    ('38222333', 'Consulta',          '2026-11-26 17:00', '2026-11-26 17:30', 'RESERVADO', 'MANUAL'))
    AS v(dni, prestacion, inicio, fin, estado, origen)
JOIN odontologo o ON o.slug = 'demo'
JOIN paciente pa ON pa.odontologo_id = o.id AND pa.dni = v.dni
JOIN prestacion pr ON pr.odontologo_id = o.id AND pr.nombre = v.prestacion;

UPDATE turno
SET cancelado_por = 'PACIENTE', cancelado_en = timestamptz '2026-11-21 00:00:00+00'
WHERE estado = 'CANCELADO'
  AND odontologo_id = (SELECT id FROM odontologo WHERE slug = 'demo');
