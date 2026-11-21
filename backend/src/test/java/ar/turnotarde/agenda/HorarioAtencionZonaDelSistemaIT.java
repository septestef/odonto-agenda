package ar.turnotarde.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnotarde.shared.tiempo.ZonaDelProceso;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Regresión del smoke de C-07: las horas locales de las franjas ({@code time}) no pueden depender de la zona
 * horaria del proceso (con {@code hibernate.jdbc.time_zone=UTC} y el proceso en otra zona, 09:00 se leía 06:00).
 * Los tests corren con {@code user.timezone=UTC}, que esconde el problema; acá se simula un servidor en la zona del
 * negocio que arranca con {@link ZonaDelProceso#fijarUtc()}, como hace {@code ZonaUtcInicializador} (spring.factories).
 */
class HorarioAtencionZonaDelSistemaIT extends IntegracionTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private HorarioAtencionRepository horarios;
    @Autowired
    private BloqueoRepository bloqueos;

    private TimeZone zonaOriginal;

    @BeforeEach
    void preparar() {
        zonaOriginal = TimeZone.getDefault();
        LimpiezaBase.limpiar(jdbc);
    }

    @AfterEach
    void restaurar() {
        TimeZone.setDefault(zonaOriginal);
        LimpiezaBase.limpiar(jdbc);
    }

    @Test
    void lasFranjasSeLeenConLaHoraEscritaEnLaBaseAunqueElProcesoNoEsteEnUtc() {
        FixturesJdbc fixtures = new FixturesJdbc(jdbc);
        long odontologo = fixtures.odontologo("zona");
        fixtures.horario(odontologo, 5, "09:00", "13:00");
        TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));
        ZonaDelProceso.fijarUtc();

        List<HorarioAtencion> franjas = horarios.findByOdontologoIdAndDiaSemanaOrderByHoraInicio(odontologo,
                (short) 5);

        assertThat(franjas).hasSize(1);
        assertThat(franjas.get(0).getHoraInicio()).isEqualTo(LocalTime.of(9, 0));
        assertThat(franjas.get(0).getHoraFin()).isEqualTo(LocalTime.of(13, 0));
    }

    @Test
    void losInstantesDeBloqueosSeLeenIgualAunqueElProcesoNoEsteEnUtc() {
        FixturesJdbc fixtures = new FixturesJdbc(jdbc);
        long odontologo = fixtures.odontologo("zona");
        fixtures.bloqueo(odontologo, Instant.parse("2026-11-27T13:00:00Z"), Instant.parse("2026-11-27T14:00:00Z"));
        TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));
        ZonaDelProceso.fijarUtc();

        List<Intervalo> encontrados = bloqueos.intervalosQueCruzan(odontologo,
                Instant.parse("2026-11-27T03:00:00Z"), Instant.parse("2026-11-28T03:00:00Z"));

        assertThat(encontrados).containsExactly(
                new Intervalo(Instant.parse("2026-11-27T13:00:00Z"), Instant.parse("2026-11-27T14:00:00Z")));
    }
}
