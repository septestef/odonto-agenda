package ar.turnotarde;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/** D10: la identidad de login no depende de las mayúsculas, así que la base también lo garantiza. */
class EmailUnicoIT extends IntegracionTest {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void preparar() {
        LimpiezaBase.limpiar(jdbc);
    }

    @AfterEach
    void dejarLaBaseVacia() {
        LimpiezaBase.limpiar(jdbc);
    }

    @Test
    void unEmailConMayusculasNoPuedeDuplicarAExistente() {
        FixturesJdbc fixtures = new FixturesJdbc(jdbc);
        fixtures.odontologo("demo", "demo@example.com");

        assertThatThrownBy(() -> fixtures.odontologo("otro", "Demo@Example.COM"))
                .isInstanceOf(DuplicateKeyException.class)
                .hasMessageContaining("odontologo_email_sin_mayusculas_uq");
    }
}
