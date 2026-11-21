package ar.turnotarde.soporte;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * Inserciones SQL directas para los tests de esquema. Las columnas de auditoría no tienen
 * {@code DEFAULT} (las completa la aplicación con el Clock), así que los fixtures las pasan
 * con un instante fijo.
 */
public final class FixturesJdbc {

    public static final Instant INSTANTE_FIJO = Instant.parse("2026-11-21T00:00:00Z");

    private final JdbcTemplate jdbc;

    public FixturesJdbc(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc);
    }

    private long insertar(String sql, Object... parametros) {
        KeyHolder claves = new GeneratedKeyHolder();
        jdbc.update(conexion -> {
            var ps = conexion.prepareStatement(sql, new String[] {"id"});
            for (int i = 0; i < parametros.length; i++) {
                Object p = parametros[i];
                if (p instanceof Instant instante) {
                    ps.setTimestamp(i + 1, Timestamp.from(instante));
                } else {
                    ps.setObject(i + 1, p);
                }
            }
            return ps;
        }, claves);
        return ((Number) Objects.requireNonNull(claves.getKeys()).get("id")).longValue();
    }

    private static Timestamp ts(Instant instante) {
        return Timestamp.from(instante);
    }

    public long odontologo(String slug, String email) {
        return insertar("""
                INSERT INTO odontologo (nombre, apellido, email, password_hash, slug, creado_en, actualizado_en)
                VALUES ('Nombre', 'Apellido', ?, '!sin-hash', ?, ?, ?)""",
                email, slug, ts(INSTANTE_FIJO), ts(INSTANTE_FIJO));
    }

    public long odontologo(String slug) {
        return odontologo(slug, slug + "@example.com");
    }

    public long prestacion(long odontologoId, String nombre, int duracionMinutos) {
        return insertar("""
                INSERT INTO prestacion (odontologo_id, nombre, duracion_minutos, creado_en, actualizado_en)
                VALUES (?, ?, ?, ?, ?)""",
                odontologoId, nombre, duracionMinutos, ts(INSTANTE_FIJO), ts(INSTANTE_FIJO));
    }

    public long paciente(long odontologoId, String dni) {
        return insertar("""
                INSERT INTO paciente (odontologo_id, dni, nombre, apellido, telefono, email, creado_en, actualizado_en)
                VALUES (?, ?, 'Paciente', 'Ficticio', '+5493510000000', ?, ?, ?)""",
                odontologoId, dni, "p" + dni + "@example.com", ts(INSTANTE_FIJO), ts(INSTANTE_FIJO));
    }

    public long horario(long odontologoId, int diaSemana, String horaInicio, String horaFin) {
        return insertar("""
                INSERT INTO horario_atencion (odontologo_id, dia_semana, hora_inicio, hora_fin, creado_en, actualizado_en)
                VALUES (?, ?, CAST(? AS time), CAST(? AS time), ?, ?)""",
                odontologoId, diaSemana, horaInicio, horaFin, ts(INSTANTE_FIJO), ts(INSTANTE_FIJO));
    }

    public long bloqueo(long odontologoId, Instant inicio, Instant fin) {
        return insertar("""
                INSERT INTO bloqueo (odontologo_id, inicio, fin, creado_en, actualizado_en)
                VALUES (?, ?, ?, ?, ?)""",
                odontologoId, inicio, fin, ts(INSTANTE_FIJO), ts(INSTANTE_FIJO));
    }

    public long turno(long odontologoId, long pacienteId, long prestacionId, Instant inicio, Instant fin,
            String estado) {
        return turno(odontologoId, pacienteId, prestacionId, inicio, fin, estado, tokenHashAleatorio());
    }

    public long turno(long odontologoId, long pacienteId, long prestacionId, Instant inicio, Instant fin,
            String estado, String tokenHash) {
        return insertar("""
                INSERT INTO turno (odontologo_id, paciente_id, prestacion_id, inicio, fin, estado, origen,
                                   token_hash, creado_en, actualizado_en)
                VALUES (?, ?, ?, ?, ?, ?, 'ONLINE', ?, ?, ?)""",
                odontologoId, pacienteId, prestacionId, inicio, fin, estado, tokenHash,
                ts(INSTANTE_FIJO), ts(INSTANTE_FIJO));
    }

    public long notificacion(long turnoId, String tipo, String estado) {
        return insertar("""
                INSERT INTO notificacion (turno_id, tipo, destinatario, enviado_en, estado, creado_en, actualizado_en)
                VALUES (?, ?, 'p@example.com', ?, ?, ?, ?)""",
                turnoId, tipo, ts(INSTANTE_FIJO), estado, ts(INSTANTE_FIJO), ts(INSTANTE_FIJO));
    }

    /** 64 caracteres hexadecimales aleatorios (formato de SHA-256). */
    public static String tokenHashAleatorio() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 64);
    }
}
