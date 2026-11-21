package ar.turnotarde.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnotarde.prestacion.Prestacion;
import ar.turnotarde.prestacion.PrestacionRepository;
import ar.turnotarde.shared.error.ReglaNegocioException;
import ar.turnotarde.soporte.ConfiguracionRelojDePrueba;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** Reloj fijo en 2026-11-25T12:00:00Z (09:00 local). Un caso por motivo de rechazo y la lista de libres. */
@Import(ConfiguracionRelojDePrueba.class)
class DisponibilidadServiceIT extends IntegracionTest {

    private static final LocalDate VIERNES = LocalDate.of(2026, 11, 27);

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PrestacionRepository prestaciones;
    @Autowired
    private DisponibilidadService servicio;

    private FixturesJdbc fixtures;
    private long odontologo;
    private Prestacion consulta;

    @AfterEach
    void dejarLaBaseVacia() {
        LimpiezaBase.limpiar(jdbc);
    }

    @BeforeEach
    void preparar() {
        LimpiezaBase.limpiar(jdbc);
        fixtures = new FixturesJdbc(jdbc);
        odontologo = fixtures.odontologo("demo");
        consulta = prestaciones.findById(fixtures.prestacion(odontologo, "Consulta", 30)).orElseThrow();
        fixtures.horario(odontologo, 5, "09:00", "13:00");
    }

    private static void rechazaCon(Throwable t, String codigo) {
        assertThat(t).isInstanceOf(ReglaNegocioException.class);
        assertThat(((ReglaNegocioException) t).codigo()).isEqualTo(codigo);
    }

    @Test
    void horariosLibresUsanFranjasBloqueosYTurnosDeLaBase() {
        long paciente = fixtures.paciente(odontologo, "12345678");
        fixtures.bloqueo(odontologo, Instant.parse("2026-11-27T13:00:00Z"), Instant.parse("2026-11-27T14:00:00Z"));
        fixtures.turno(odontologo, paciente, consulta.getId(), Instant.parse("2026-11-27T12:00:00Z"),
                Instant.parse("2026-11-27T12:30:00Z"), "RESERVADO");

        List<Instant> libres = servicio.horariosLibres(odontologo, consulta, VIERNES);

        assertThat(libres).containsExactly(Instant.parse("2026-11-27T12:30:00Z"),
                Instant.parse("2026-11-27T14:00:00Z"), Instant.parse("2026-11-27T14:15:00Z"),
                Instant.parse("2026-11-27T14:30:00Z"), Instant.parse("2026-11-27T14:45:00Z"),
                Instant.parse("2026-11-27T15:00:00Z"), Instant.parse("2026-11-27T15:15:00Z"),
                Instant.parse("2026-11-27T15:30:00Z"));
    }

    @Test
    void unDiaSinFranjasOFueraDelHorizonteNoTieneHorarios() {
        assertThat(servicio.horariosLibres(odontologo, consulta, LocalDate.of(2026, 11, 28))).isEmpty();
        fixtures.horario(odontologo, 1, "09:00", "13:00");
        assertThat(servicio.horariosLibres(odontologo, consulta, LocalDate.of(2027, 1, 25))).isEmpty();
    }

    @Test
    void validarInicioAceptaUnHorarioLibre() {
        assertThatCode(() -> servicio.validarInicio(odontologo, consulta, Instant.parse("2026-11-27T12:00:00Z")))
                .doesNotThrowAnyException();
    }

    @Test
    void validarInicioRechazaPorAnticipacion() {
        assertThatThrownBy(() -> servicio.validarInicio(odontologo, consulta, Instant.parse("2026-11-26T11:45:00Z")))
                .satisfies(t -> rechazaCon(t, "RN-TU-03"));
    }

    @Test
    void validarInicioRechazaPorHorizonte() {
        fixtures.horario(odontologo, 1, "09:00", "13:00");
        assertThatThrownBy(() -> servicio.validarInicio(odontologo, consulta, Instant.parse("2027-01-25T12:00:00Z")))
                .satisfies(t -> rechazaCon(t, "RN-TU-11"));
    }

    @Test
    void validarInicioRechazaFueraDeAgendaPorPasoPorCierreYPorBloqueo() {
        fixtures.bloqueo(odontologo, Instant.parse("2026-11-27T14:00:00Z"), Instant.parse("2026-11-27T14:30:00Z"));

        assertThatThrownBy(() -> servicio.validarInicio(odontologo, consulta, Instant.parse("2026-11-27T12:10:00Z")))
                .as("fuera de paso").satisfies(t -> rechazaCon(t, "RN-AG-05"));
        assertThatThrownBy(() -> servicio.validarInicio(odontologo, consulta, Instant.parse("2026-11-27T15:45:00Z")))
                .as("terminaria 13:15 local").satisfies(t -> rechazaCon(t, "RN-AG-05"));
        assertThatThrownBy(() -> servicio.validarInicio(odontologo, consulta, Instant.parse("2026-11-27T14:15:00Z")))
                .as("cruza el bloqueo").satisfies(t -> rechazaCon(t, "RN-AG-05"));
    }

    @Test
    void validarInicioRechazaHorarioOcupadoConRnTu01() {
        long paciente = fixtures.paciente(odontologo, "12345678");
        fixtures.turno(odontologo, paciente, consulta.getId(), Instant.parse("2026-11-27T12:00:00Z"),
                Instant.parse("2026-11-27T12:30:00Z"), "RESERVADO");

        assertThatThrownBy(() -> servicio.validarInicio(odontologo, consulta, Instant.parse("2026-11-27T12:15:00Z")))
                .satisfies(t -> rechazaCon(t, "RN-TU-01"));
        assertThatCode(() -> servicio.validarInicio(odontologo, consulta, Instant.parse("2026-11-27T12:30:00Z")))
                .as("contiguo").doesNotThrowAnyException();
    }
}
