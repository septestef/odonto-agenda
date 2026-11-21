package ar.turnotarde.soporte;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Deja vacías las tablas de negocio entre tests. No se usa rollback transaccional porque
 * el test de concurrencia necesita commits reales (D9).
 */
public final class LimpiezaBase {

    private static final String TABLAS =
            "notificacion, turno, paciente, prestacion, bloqueo, horario_atencion, odontologo";

    private LimpiezaBase() {
    }

    public static void limpiar(JdbcTemplate jdbc) {
        jdbc.execute("TRUNCATE " + TABLAS + " RESTART IDENTITY CASCADE");
    }
}
