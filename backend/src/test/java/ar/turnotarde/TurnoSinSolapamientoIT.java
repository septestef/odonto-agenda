package ar.turnotarde;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import java.sql.SQLException;
import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** Escenarios de la capability turnos-sin-solapamiento (RN-TU-01, DD-03). */
class TurnoSinSolapamientoIT extends IntegracionTest {

    private static final String DIA = "2026-11-25T";

    @Autowired
    private DataSource dataSource;

    private JdbcTemplate jdbc;
    private FixturesJdbc fx;
    private long odontologoA;
    private long odontologoB;
    private long pacienteA;
    private long prestacionA;
    private long pacienteB;
    private long prestacionB;

    @BeforeEach
    void preparar() {
        jdbc = new JdbcTemplate(dataSource);
        fx = new FixturesJdbc(jdbc);
        LimpiezaBase.limpiar(jdbc);
        odontologoA = fx.odontologo("odontologo-a");
        odontologoB = fx.odontologo("odontologo-b");
        pacienteA = fx.paciente(odontologoA, "11111111");
        prestacionA = fx.prestacion(odontologoA, "Consulta", 30);
        pacienteB = fx.paciente(odontologoB, "22222222");
        prestacionB = fx.prestacion(odontologoB, "Consulta", 30);
    }

    private static Instant a(String hora) {
        return Instant.parse(DIA + hora + ":00Z");
    }

    private long turnoDeA(String desde, String hasta, String estado) {
        return fx.turno(odontologoA, pacienteA, prestacionA, a(desde), a(hasta), estado);
    }

    private static void assertViolaSolapamiento(Runnable operacion) {
        assertThatThrownBy(operacion::run)
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("turno_sin_solapamiento")
                .satisfies(e -> {
                    Throwable causa = ((DataIntegrityViolationException) e).getRootCause();
                    assertThat(causa).isInstanceOf(SQLException.class);
                    assertThat(((SQLException) causa).getSQLState()).isEqualTo("23P01");
                });
    }

    @Test
    void rechazaUnTurnoSuperpuesto() {
        turnoDeA("10:00", "10:45", "RESERVADO");

        assertViolaSolapamiento(() -> turnoDeA("10:30", "11:00", "RESERVADO"));
        assertViolaSolapamiento(() -> turnoDeA("09:30", "10:15", "RESERVADO"));
        assertViolaSolapamiento(() -> turnoDeA("10:00", "10:45", "RESERVADO"));
    }

    @Test
    void rechazaUnTurnoContenidoEnOtroAtendido() {
        turnoDeA("09:00", "10:00", "ATENDIDO");

        assertViolaSolapamiento(() -> turnoDeA("09:15", "09:45", "RESERVADO"));
    }

    @Test
    void losTurnosAusentesTambienOcupanLugar() {
        turnoDeA("09:00", "10:00", "AUSENTE");

        assertViolaSolapamiento(() -> turnoDeA("09:30", "10:30", "RESERVADO"));
    }

    @Test
    void aceptaTurnosContiguos() {
        turnoDeA("10:00", "10:30", "RESERVADO");

        assertThatCode(() -> turnoDeA("10:30", "11:00", "RESERVADO")).doesNotThrowAnyException();
        assertThatCode(() -> turnoDeA("09:30", "10:00", "RESERVADO")).doesNotThrowAnyException();
    }

    @Test
    void aceptaElMismoHorarioParaOtroOdontologo() {
        turnoDeA("10:00", "10:30", "RESERVADO");

        assertThatCode(() -> fx.turno(odontologoB, pacienteB, prestacionB, a("10:00"), a("10:30"), "RESERVADO"))
                .doesNotThrowAnyException();
    }

    @Test
    void aceptaUnTurnoActivoSobreUnoCancelado() {
        turnoDeA("10:00", "10:30", "CANCELADO");

        assertThatCode(() -> turnoDeA("10:00", "10:30", "RESERVADO")).doesNotThrowAnyException();
    }

    @Test
    void aceptaVariosTurnosCanceladosSuperpuestos() {
        turnoDeA("10:00", "10:30", "CANCELADO");

        assertThatCode(() -> turnoDeA("10:00", "10:30", "CANCELADO")).doesNotThrowAnyException();
    }

    @Test
    void cancelarLiberaElHorario() {
        long reservado = turnoDeA("10:00", "10:30", "RESERVADO");

        jdbc.update("update turno set estado = 'CANCELADO' where id = ?", reservado);

        assertThatCode(() -> turnoDeA("10:00", "10:30", "RESERVADO")).doesNotThrowAnyException();
    }

    @Test
    void noSePuedeReactivarUnTurnoSobreUnHorarioOcupado() {
        long cancelado = turnoDeA("10:00", "10:30", "CANCELADO");
        turnoDeA("10:00", "10:30", "RESERVADO");

        assertViolaSolapamiento(
                () -> jdbc.update("update turno set estado = 'RESERVADO' where id = ?", cancelado));
    }

    @Test
    void sePuedeReactivarUnTurnoSiSuHorarioEstaLibre() {
        long cancelado = turnoDeA("10:00", "10:30", "CANCELADO");

        assertThatCode(() -> jdbc.update("update turno set estado = 'RESERVADO' where id = ?", cancelado))
                .doesNotThrowAnyException();
    }
}
