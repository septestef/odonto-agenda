package ar.turnotarde.paciente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnotarde.soporte.ConfiguracionRelojDePrueba;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Upsert atómico del paciente (D6). Reloj fijo en 2026-11-25T12:00:00Z; los fixtures se crearon el 2026-11-21. */
@Import(ConfiguracionRelojDePrueba.class)
class RegistroPacientesIT extends IntegracionTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RegistroPacientes registro;
    @Autowired
    private TransactionTemplate transacciones;

    private FixturesJdbc fixtures;
    private long demo;
    private long otro;

    @AfterEach
    void dejarLaBaseVacia() {
        LimpiezaBase.limpiar(jdbc);
    }

    @BeforeEach
    void preparar() {
        LimpiezaBase.limpiar(jdbc);
        fixtures = new FixturesJdbc(jdbc);
        demo = fixtures.odontologo("demo");
        otro = fixtures.odontologo("otro");
    }

    private Map<String, Object> fila(long id) {
        return jdbc.queryForMap("SELECT * FROM paciente WHERE id = ?", id);
    }

    private int cantidad(String dni) {
        return jdbc.queryForObject("SELECT count(*) FROM paciente WHERE dni = ?", Integer.class, dni);
    }

    @Test
    void dniNuevoCreaElPacienteConLosDatosYLaAuditoriaDelReloj() {
        long id = registro.registrar(demo, "12345678", "Luis", "Gómez", "+5493511234567", "luis@example.com");

        Map<String, Object> paciente = fila(id);
        assertThat(paciente).containsEntry("odontologo_id", demo).containsEntry("dni", "12345678")
                .containsEntry("nombre", "Luis").containsEntry("apellido", "Gómez")
                .containsEntry("telefono", "+5493511234567").containsEntry("email", "luis@example.com");
        assertThat(((Timestamp) paciente.get("creado_en")).toInstant()).isEqualTo(Instant.parse("2026-11-25T12:00:00Z"));
        assertThat(((Timestamp) paciente.get("actualizado_en")).toInstant())
                .isEqualTo(Instant.parse("2026-11-25T12:00:00Z"));
    }

    @Test
    void dniRepetidoActualizaElContactoSinDuplicarNiTocarCreadoEn() {
        long existente = fixtures.paciente(demo, "12345678");

        long id = registro.registrar(demo, "12345678", "Luis", "Gómez", "+5493511234567", "nuevo@example.com");

        assertThat(id).isEqualTo(existente);
        assertThat(cantidad("12345678")).isEqualTo(1);
        Map<String, Object> paciente = fila(id);
        assertThat(paciente).containsEntry("nombre", "Luis").containsEntry("apellido", "Gómez")
                .containsEntry("telefono", "+5493511234567").containsEntry("email", "nuevo@example.com");
        assertThat(((Timestamp) paciente.get("creado_en")).toInstant()).isEqualTo(FixturesJdbc.INSTANTE_FIJO);
        assertThat(((Timestamp) paciente.get("actualizado_en")).toInstant())
                .isEqualTo(Instant.parse("2026-11-25T12:00:00Z"));
    }

    @Test
    void elMismoDniEnOtroOdontologoCreaOtroPacienteYNoTocaElExistente() {
        long deOtro = fixtures.paciente(otro, "12345678");

        long id = registro.registrar(demo, "12345678", "Luis", "Gómez", "+5493511234567", "luis@example.com");

        assertThat(id).isNotEqualTo(deOtro);
        assertThat(cantidad("12345678")).isEqualTo(2);
        assertThat(fila(deOtro)).containsEntry("nombre", "Paciente").containsEntry("email", "p12345678@example.com");
    }

    @Test
    void siLaTransaccionHaceRollbackElAltaYLaActualizacionSeDeshacen() {
        long existente = fixtures.paciente(demo, "11111111");

        assertThatThrownBy(() -> transacciones.executeWithoutResult(estado -> {
            registro.registrar(demo, "22222222", "Nuevo", "Paciente", "+5493510000001", "nuevo@example.com");
            registro.registrar(demo, "11111111", "Cambiado", "Paciente", "+5493510000002", "cambiado@example.com");
            throw new IllegalStateException("rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(cantidad("22222222")).isZero();
        assertThat(fila(existente)).containsEntry("nombre", "Paciente").containsEntry("email",
                "p11111111@example.com");
    }
}
