package ar.turnotarde.paciente;

import ar.turnotarde.shared.tiempo.ZonaNegocio;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Alta o actualización atómica del paciente por (odontólogo, DNI) con {@code INSERT ... ON CONFLICT} (D6).
 * Corre en la transacción de JPA de quien llama. El {@code DO UPDATE} deja la fila bloqueada hasta el commit:
 * dos reservas del mismo DNI se serializan, y así el límite de RN-PA-03 vale bajo concurrencia.
 * Las marcas de auditoría salen del {@code Clock} inyectado, nunca del {@code now()} de la base.
 */
@Component
public class RegistroPacientes {

    private static final String UPSERT = """
            INSERT INTO paciente (odontologo_id, dni, nombre, apellido, telefono, email, creado_en, actualizado_en)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT ON CONSTRAINT paciente_dni_por_odontologo_uk
            DO UPDATE SET nombre = EXCLUDED.nombre, apellido = EXCLUDED.apellido, telefono = EXCLUDED.telefono,
                          email = EXCLUDED.email, actualizado_en = EXCLUDED.actualizado_en
            RETURNING id""";

    private final JdbcTemplate jdbc;
    private final ZonaNegocio zonaNegocio;

    public RegistroPacientes(JdbcTemplate jdbc, ZonaNegocio zonaNegocio) {
        this.jdbc = jdbc;
        this.zonaNegocio = zonaNegocio;
    }

    /** Devuelve el id del paciente, creado o actualizado con lo último ingresado (RN-PA-02). */
    public long registrar(long odontologoId, String dni, String nombre, String apellido, String telefono,
            String email) {
        Timestamp ahora = Timestamp.from(zonaNegocio.ahora());
        Long id = jdbc.queryForObject(UPSERT, Long.class, odontologoId, dni, nombre, apellido, telefono, email,
                ahora, ahora);
        return id;
    }
}
