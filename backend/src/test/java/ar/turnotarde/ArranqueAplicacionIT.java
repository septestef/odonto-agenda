package ar.turnotarde;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnotarde.soporte.IntegracionTest;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ArranqueAplicacionIT extends IntegracionTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Clock reloj;

    @Test
    void elContextoLevantaYFlywayCreaSuTablaDeHistorial() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer historial = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' "
                        + "and table_name = 'flyway_schema_history'", Integer.class);

        assertThat(historial).isEqualTo(1);
    }

    @Test
    void existenLas7TablasDeNegocio() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        List<String> tablas = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public' "
                        + "and table_name <> 'flyway_schema_history'", String.class);

        assertThat(tablas).containsExactlyInAnyOrder("odontologo", "horario_atencion", "bloqueo",
                "prestacion", "paciente", "turno", "notificacion");
    }

    @Test
    void sinPerfilDemoNoSeAplicoElSeedV900() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        List<String> versiones = jdbc.queryForList(
                "select version from flyway_schema_history where version is not null", String.class);

        assertThat(versiones).contains("2").doesNotContain("900");
    }

    @Test
    void elRelojPorDefectoEstaEnUtc() {
        assertThat(reloj.getZone()).isEqualTo(ZoneOffset.UTC);
    }
}
