package ar.turnotarde.shared.tiempo;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnotarde.soporte.IntegracionTest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Import(RelojInyectadoIT.RelojFijo.class)
class RelojInyectadoIT extends IntegracionTest {

    @TestConfiguration
    static class RelojFijo {
        @Bean
        @Primary
        Clock relojFijo() {
            return Clock.fixed(Instant.parse("2026-11-25T12:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Autowired
    private ZonaNegocio zonaNegocio;

    @Test
    void laZonaDelNegocioUsaElRelojReemplazado() {
        assertThat(zonaNegocio.ahora()).isEqualTo(Instant.parse("2026-11-25T12:00:00Z"));
        assertThat(zonaNegocio.hoy()).isEqualTo(LocalDate.of(2026, 11, 25));
    }
}
