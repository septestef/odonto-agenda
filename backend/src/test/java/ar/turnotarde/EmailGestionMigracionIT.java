package ar.turnotarde;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

class EmailGestionMigracionIT extends IntegracionTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void v9AgregaUnaColumnaNulableSinDefaultNiReconstruccionDelCorreo() throws SQLException {
        enEsquemaAislado((dataSource, esquema, jdbc) -> {
            migrar(dataSource, esquema, "3");
            var fx = new FixturesJdbc(jdbc);
            long od = fx.odontologo("migracion");
            long paciente = fx.paciente(od, "12345678");
            long prestacion = fx.prestacion(od, "Consulta", 30);
            Instant inicio = Instant.parse("2026-11-27T12:00:00Z");
            long anterior = fx.turno(od, paciente, prestacion, inicio, inicio.plusSeconds(1800), "CANCELADO");
            long vigente = fx.turno(od, paciente, prestacion, inicio, inicio.plusSeconds(1800), "RESERVADO");
            jdbc.update("UPDATE turno SET reprogramado_desde_id = ? WHERE id = ?", anterior, vigente);
            fx.notificacion(anterior, "CONFIRMACION", "ENVIADO");
            var turnos = jdbc.queryForList("SELECT * FROM turno ORDER BY id");

            migrar(dataSource, esquema, "latest");

            assertThat(jdbc.queryForList("SELECT email_gestion FROM turno ORDER BY id", String.class))
                    .containsExactly(null, null);
            var migrados = jdbc.queryForList("SELECT * FROM turno ORDER BY id");
            migrados.forEach(fila -> fila.remove("email_gestion"));
            assertThat(migrados).isEqualTo(turnos);
        });
    }

    @Test
    void flywayCreaElEsquemaVacioConLaColumnaNulableSinDefault() throws SQLException {
        enEsquemaAislado((dataSource, esquema, jdbc) -> {
            migrar(dataSource, esquema, "latest");

            assertThat(jdbc.queryForMap("""
                    SELECT data_type, character_maximum_length, is_nullable, column_default
                    FROM information_schema.columns
                    WHERE table_schema = ? AND table_name = 'turno' AND column_name = 'email_gestion'
                    """, esquema))
                    .containsEntry("data_type", "character varying")
                    .containsEntry("character_maximum_length", 255)
                    .containsEntry("is_nullable", "YES")
                    .containsEntry("column_default", null);
            assertThat(jdbc.queryForList("SELECT * FROM turno")).isEmpty();
        });
    }

    private void enEsquemaAislado(EscenarioMigracion escenario) throws SQLException {
        String esquema = "prueba_v9_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate administracion = new JdbcTemplate(dataSource);
        administracion.execute("CREATE SCHEMA " + esquema);
        try (Connection conexion = dataSource.getConnection()) {
            var aislada = new SingleConnectionDataSource(conexion, true);
            var jdbc = new JdbcTemplate(aislada);
            // La extensión btree_gist ya está instalada en public por V2.
            jdbc.execute("SET search_path TO " + esquema + ", public");
            try {
                escenario.verificar(aislada, esquema, jdbc);
            } finally {
                jdbc.execute("RESET search_path");
            }
        } finally {
            administracion.execute("DROP SCHEMA " + esquema + " CASCADE");
        }
    }

    @FunctionalInterface
    private interface EscenarioMigracion {
        void verificar(DataSource dataSource, String esquema, JdbcTemplate jdbc);
    }

    private static void migrar(DataSource aislada, String esquema, String target) {
        Flyway.configure()
                .dataSource(aislada)
                .schemas(esquema)
                .defaultSchema(esquema)
                .target(target)
                .load()
                .migrate();
    }
}
