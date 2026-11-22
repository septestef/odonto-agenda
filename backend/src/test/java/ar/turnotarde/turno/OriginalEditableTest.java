package ar.turnotarde.turno;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnotarde.shared.error.ReglaNegocioException;
import java.time.Instant;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OriginalEditableTest {
    private static final Instant INICIO = Instant.parse("2026-11-27T12:00:00Z");

    @ParameterizedTest
    @EnumSource(EstadoTurno.class)
    void elInicioIgualOPasadoTienePrecedencia(EstadoTurno estado) {
        for (var ahora : new Instant[]{INICIO, INICIO.plusSeconds(1)}) {
            assertThatThrownBy(() -> ReglasTurno.exigirOriginalEditable(INICIO, estado, ahora))
                    .isInstanceOfSatisfying(ReglaNegocioException.class,
                            e -> org.assertj.core.api.Assertions.assertThat(e.codigo()).isEqualTo("RN-TU-07"));
        }
    }

    @ParameterizedTest
    @EnumSource(EstadoTurno.class)
    void antesDelInicioSoloEsEditableElReservado(EstadoTurno estado) {
        var ahora = INICIO.minusSeconds(1);
        if (estado == EstadoTurno.RESERVADO) {
            assertThatCode(() -> ReglasTurno.exigirOriginalEditable(INICIO, estado, ahora)).doesNotThrowAnyException();
        } else {
            assertThatThrownBy(() -> ReglasTurno.exigirOriginalEditable(INICIO, estado, ahora))
                    .isInstanceOfSatisfying(ReglaNegocioException.class,
                            e -> org.assertj.core.api.Assertions.assertThat(e.codigo()).isEqualTo("RN-SE-02"));
        }
    }
}
