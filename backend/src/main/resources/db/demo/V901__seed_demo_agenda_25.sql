-- Más turnos de ejemplo para el miércoles 25/11/2026, día de la demo (C-16, design D4).
-- Solo se aplica con el perfil `demo`. No edita V900: suma dos pacientes ficticios y cuatro turnos
-- repartidos entre la mañana y la tarde, con las cuatro prestaciones y dos turnos de origen MANUAL.
-- Ninguno se superpone con otro ni queda pegado a otro turno activo; el 26 y el 27 no se tocan, para
-- reservar en vivo. Ningún paciente queda con más de 2 turnos RESERVADOS (RN-PA-03).

INSERT INTO paciente (odontologo_id, dni, nombre, apellido, telefono, email, creado_en, actualizado_en)
SELECT o.id, p.dni, p.nombre, p.apellido, p.telefono, p.email,
       timestamptz '2026-11-24 00:00:00+00', timestamptz '2026-11-24 00:00:00+00'
FROM odontologo o
CROSS JOIN (VALUES
    ('41555666', 'Valentina', 'Ortiz',   '+5493515550105', 'valentina.ortiz@example.com'),
    ('39666777', 'Joaquín',   'Peralta', '+5493515550106', 'joaquin.peralta@example.com'))
    AS p(dni, nombre, apellido, telefono, email)
WHERE o.slug = 'demo';

INSERT INTO turno (odontologo_id, paciente_id, prestacion_id, inicio, fin, estado, origen, token_hash,
                   creado_en, actualizado_en)
SELECT o.id, pa.id, pr.id,
       v.inicio::timestamp AT TIME ZONE 'America/Argentina/Buenos_Aires',
       v.fin::timestamp AT TIME ZONE 'America/Argentina/Buenos_Aires',
       'RESERVADO', v.origen,
       encode(sha256(uuid_send(gen_random_uuid())), 'hex'),
       timestamptz '2026-11-24 00:00:00+00', timestamptz '2026-11-24 00:00:00+00'
FROM (VALUES
    ('35333444', 'Consulta',          '2026-11-25 10:30', '2026-11-25 11:00', 'ONLINE'),
    ('41555666', 'Limpieza',          '2026-11-25 12:00', '2026-11-25 12:45', 'MANUAL'),
    ('39666777', 'Extracción',        '2026-11-25 16:30', '2026-11-25 17:30', 'ONLINE'),
    ('42444555', 'Arreglo de caries', '2026-11-25 18:00', '2026-11-25 18:45', 'MANUAL'))
    AS v(dni, prestacion, inicio, fin, origen)
JOIN odontologo o ON o.slug = 'demo'
JOIN paciente pa ON pa.odontologo_id = o.id AND pa.dni = v.dni
JOIN prestacion pr ON pr.odontologo_id = o.id AND pr.nombre = v.prestacion;
