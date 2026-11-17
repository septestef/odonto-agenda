package ar.turnotarde.shared.tiempo;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnotarde.shared.config.AppProperties;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

class ZonaNegocioTest {

    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");

    private static ZonaNegocio zonaConReloj(String instante) {
        Clock reloj = Clock.fixed(Instant.parse(instante), ZoneOffset.UTC);
        return new ZonaNegocio(reloj, new AppProperties(BUENOS_AIRES, URI.create("http://localhost:8080"), 60));
    }

    @Test
    void horaLocalSeConvierteAInstanteUtc() {
        ZonaNegocio zona = zonaConReloj("2026-11-25T12:00:00Z");

        Instant instante = zona.aInstante(LocalDate.of(2026, 11, 25), LocalTime.of(9, 0));

        assertThat(instante).isEqualTo(Instant.parse("2026-11-25T12:00:00Z"));
    }

    @Test
    void instanteUtcSeMuestraEnHoraLocal() {
        ZonaNegocio zona = zonaConReloj("2026-11-25T12:00:00Z");

        LocalDateTime local = zona.aLocal(Instant.parse("2026-11-25T23:30:00Z")).toLocalDateTime();

        assertThat(local).isEqualTo(LocalDateTime.of(2026, 11, 25, 20, 30));
    }

    @Test
    void hoyEsLaFechaDelNegocioNoLaDeUtc() {
        ZonaNegocio zona = zonaConReloj("2026-11-26T01:00:00Z");

        assertThat(zona.hoy()).isEqualTo(LocalDate.of(2026, 11, 25));
    }

    @Test
    void hoyCoincideConUtcCuandoYaEsElMismoDiaEnAmbasZonas() {
        ZonaNegocio zona = zonaConReloj("2026-11-26T15:00:00Z");

        assertThat(zona.hoy()).isEqualTo(LocalDate.of(2026, 11, 26));
    }

    @Test
    void ahoraDevuelveElInstanteDelRelojInyectado() {
        ZonaNegocio zona = zonaConReloj("2026-11-25T12:00:00Z");

        assertThat(zona.ahora()).isEqualTo(Instant.parse("2026-11-25T12:00:00Z"));
        assertThat(zona.zona()).isEqualTo(BUENOS_AIRES);
    }

    @Test
    void elResultadoNoDependeDeLaZonaPorDefectoDeLaJvm() {
        TimeZone original = TimeZone.getDefault();
        try {
            for (String zonaJvm : new String[] {"Asia/Tokyo", "Pacific/Auckland", "UTC"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(zonaJvm));
                ZonaNegocio zona = zonaConReloj("2026-11-26T01:00:00Z");

                assertThat(zona.aInstante(LocalDate.of(2026, 11, 25), LocalTime.of(9, 0)))
                        .isEqualTo(Instant.parse("2026-11-25T12:00:00Z"));
                assertThat(zona.aLocal(Instant.parse("2026-11-25T23:30:00Z")).toLocalTime())
                        .isEqualTo(LocalTime.of(20, 30));
                assertThat(zona.hoy()).isEqualTo(LocalDate.of(2026, 11, 25));
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
