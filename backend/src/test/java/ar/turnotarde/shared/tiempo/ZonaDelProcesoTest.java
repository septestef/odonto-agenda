package ar.turnotarde.shared.tiempo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ZonaDelProcesoTest {

    private TimeZone original;

    @BeforeEach
    void guardar() {
        original = TimeZone.getDefault();
    }

    @AfterEach
    void restaurar() {
        TimeZone.setDefault(original);
    }

    @Test
    void fijarUtcReemplazaLaZonaDeUnProcesoQueArrancoEnOtraZona() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));

        ZonaDelProceso.fijarUtc();

        assertThat(TimeZone.getDefault().getID()).isEqualTo("UTC");
    }

    @Test
    void fijarUtcEsIdempotente() {
        ZonaDelProceso.fijarUtc();
        ZonaDelProceso.fijarUtc();

        assertThat(TimeZone.getDefault().getID()).isEqualTo("UTC");
    }
}
