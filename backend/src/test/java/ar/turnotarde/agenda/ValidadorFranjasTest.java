package ar.turnotarde.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnotarde.agenda.dto.FranjaDto;
import ar.turnotarde.shared.error.DetalleCampo;
import ar.turnotarde.shared.error.ReglaNegocioException;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Regla RN-AG-01 pura: rango válido, día ISO y franjas que no se pisan dentro del mismo día. */
class ValidadorFranjasTest {

    private static FranjaDto franja(short dia, String inicio, String fin) {
        return new FranjaDto(dia, inicio, fin);
    }

    private static String campoDelError(List<FranjaDto> franjas) {
        assertThatThrownBy(() -> ValidadorFranjas.validar(franjas))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo("RN-AG-01");
                    assertThat(error.estado().value()).isEqualTo(422);
                });
        try {
            ValidadorFranjas.validar(franjas);
        } catch (ReglaNegocioException error) {
            return ((List<DetalleCampo>) error.detalles()).get(0).campo();
        }
        throw new AssertionError("La validación no rechazó las franjas");
    }

    @Test
    void variasFranjasPorDiaYDiasSinAtencionSonValidos() {
        assertThatCode(() -> ValidadorFranjas.validar(List.of(
                franja((short) 1, "09:00", "13:00"),
                franja((short) 1, "16:00", "20:00"),
                franja((short) 7, "10:00", "12:00"))))
                .doesNotThrowAnyException();
    }

    @Test
    void franjaInvertidaSeRechazaConSuIndice() {
        assertThat(campoDelError(List.of(
                franja((short) 1, "09:00", "10:00"),
                franja((short) 3, "13:00", "09:00")))).isEqualTo("franjas[1]");
    }

    @Test
    void franjaConInicioIgualAlFinSeRechaza() {
        assertThat(campoDelError(List.of(franja((short) 2, "10:00", "10:00")))).isEqualTo("franjas[0]");
    }

    @Test
    void diasFueraDelRangoIsoSeRechazan() {
        assertThat(campoDelError(List.of(franja((short) 0, "09:00", "10:00")))).isEqualTo("franjas[0]");
        assertThat(campoDelError(List.of(franja((short) 8, "09:00", "10:00")))).isEqualTo("franjas[0]");
    }

    @Test
    void franjasSolapadasDelMismoDiaSeRechazan() {
        assertThat(campoDelError(List.of(
                franja((short) 3, "09:00", "11:00"),
                franja((short) 3, "10:00", "12:00")))).isEqualTo("franjas[1]");
    }

    @Test
    void franjasContiguasNoSeConsideranSolapadas() {
        assertThatCode(() -> ValidadorFranjas.validar(List.of(
                franja((short) 3, "09:00", "11:00"),
                franja((short) 3, "11:00", "13:00"))))
                .doesNotThrowAnyException();
    }

    @Test
    void laMismaHoraEnDiasDistintosEsValida() {
        assertThatCode(() -> ValidadorFranjas.validar(List.of(
                franja((short) 1, "09:00", "11:00"),
                franja((short) 2, "09:00", "11:00"))))
                .doesNotThrowAnyException();
    }

    @Test
    void elOrdenDeValidacionEsRangosPrimeroYSolapesDespues() {
        // Hay un solape en el índice 1 y una franja invertida en el 2: se informa primero el rango inválido.
        List<FranjaDto> franjas = List.of(
                franja((short) 1, "09:00", "11:00"),
                franja((short) 1, "10:00", "12:00"),
                franja((short) 2, "13:00", "09:00"));

        assertThat(campoDelError(franjas)).isEqualTo("franjas[2]");
    }

    @Test
    void unaListaVaciaEsValida() {
        assertThatCode(() -> ValidadorFranjas.validar(List.of())).doesNotThrowAnyException();
    }
}
