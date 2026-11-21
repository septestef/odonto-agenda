package ar.turnotarde;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnotarde.soporte.IntegracionTest;
import java.util.List;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Convención D1: una migración que llega con versión menor que la última aplicada igual se aplica. */
class MigracionesFueraDeOrdenIT extends IntegracionTest {

    private static final String ESQUEMA = "prueba_orden";

    @Autowired
    private Flyway flyway;

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void descartarEsquema() {
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("DROP SCHEMA IF EXISTS " + ESQUEMA + " CASCADE");
    }

    private Flyway flywayDePrueba(boolean fueraDeOrden, String... ubicaciones) {
        return Flyway.configure()
                .configuration(flyway.getConfiguration())
                .dataSource(dataSource)
                .schemas(ESQUEMA)
                .defaultSchema(ESQUEMA)
                .createSchemas(true)
                .locations(ubicaciones)
                .outOfOrder(fueraDeOrden)
                .load();
    }

    private List<String> versionesAplicadas() {
        return jdbc.queryForList(
                "select version from " + ESQUEMA + ".flyway_schema_history where version is not null "
                        + "order by installed_rank", String.class);
    }

    @Test
    void laConfiguracionEfectivaPermiteMigracionesFueraDeOrden() {
        assertThat(flyway.getConfiguration().isOutOfOrder()).isTrue();
    }

    @Test
    void aplicaV5aunqueYaEstenAplicadasV2yV7() {
        flywayDePrueba(true, "classpath:db/prueba-orden/inicial").migrate();
        assertThat(versionesAplicadas()).containsExactly("2", "7");

        Flyway conV5 = flywayDePrueba(true,
                "classpath:db/prueba-orden/inicial", "classpath:db/prueba-orden/posterior");
        conV5.migrate();

        assertThat(versionesAplicadas()).containsExactly("2", "7", "5");
        Integer tablas = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = ? and table_name = 'orden_c'",
                Integer.class, ESQUEMA);
        assertThat(tablas).isEqualTo(1);
    }

    @Test
    void sinFueraDeOrdenFlywayRechazaV5() {
        flywayDePrueba(false, "classpath:db/prueba-orden/inicial").migrate();

        Flyway conV5 = flywayDePrueba(false,
                "classpath:db/prueba-orden/inicial", "classpath:db/prueba-orden/posterior");

        assertThatThrownBy(conV5::migrate).isInstanceOf(FlywayException.class);
    }
}
