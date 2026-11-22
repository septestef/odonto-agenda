package ar.turnotarde.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnotarde.shared.error.DetalleCampo;
import ar.turnotarde.shared.error.ReglaNegocioException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class RangoAgendaTest {
    private static final LocalDate DESDE = LocalDate.of(2026, 11, 25);

    @Test
    void admiteUnDia() {
        var rango = new RangoAgenda(DESDE, DESDE.plusDays(1));
        assertThat(rango.desde()).isEqualTo(DESDE);
        assertThat(rango.hasta()).isEqualTo(LocalDate.of(2026, 11, 26));
    }

    @Test
    void admite31DiasCalendarioInclusoAlCambiarDeMes() {
        assertThat(new RangoAgenda(DESDE, DESDE.plusDays(31)).hasta())
                .isEqualTo(LocalDate.of(2026, 12, 26));
    }

    @ParameterizedTest
    @ValueSource(ints = {32, 365})
    void rechazaMasDe31Dias(int dias) {
        assertThatThrownBy(() -> new RangoAgenda(DESDE, DESDE.plusDays(dias)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
                    assertThat(e.estado()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.codigo()).isEqualTo("VALIDACION");
                    assertThat(e.getMessage()).isEqualTo("Hay datos inválidos. Revisalos y probá de nuevo.");
                    assertThat(e.detalles()).isEqualTo(List.of(
                            new DetalleCampo("hasta", "Elegí un rango de hasta 31 días.")));
                });
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -32})
    void ordenPrecedeALongitud(int dias) {
        assertThatThrownBy(() -> new RangoAgenda(DESDE, DESDE.plusDays(dias)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
                    assertThat(e.estado()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.codigo()).isEqualTo("VALIDACION");
                    assertThat(e.getMessage()).isEqualTo("Hay datos inválidos. Revisalos y probá de nuevo.");
                    assertThat(e.detalles()).isEqualTo(List.of(
                            new DetalleCampo("hasta", "Elegí una fecha posterior a desde.")));
                });
    }
}
