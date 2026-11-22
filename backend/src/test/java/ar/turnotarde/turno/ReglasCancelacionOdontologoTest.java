package ar.turnotarde.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import ar.turnotarde.shared.error.ReglaNegocioException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ReglasCancelacionOdontologoTest {
    private static final Instant INICIO = Instant.parse("2026-11-27T12:00:00Z");

    @Test
    void reservadoFuturoCercanoSeCancela() {
        assertThat(ReglasCancelacionOdontologo.debeCancelar(EstadoTurno.RESERVADO, INICIO,
                INICIO.minusSeconds(1))).isTrue();
    }

    @Test
    void canceladoPrecedeAlTiempoYEsIdempotente() {
        assertThat(ReglasCancelacionOdontologo.debeCancelar(EstadoTurno.CANCELADO, INICIO,
                INICIO.plusSeconds(1))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = EstadoTurno.class, names = {"RESERVADO", "ATENDIDO", "AUSENTE"})
    void tiempoPrecedeALaInconsistencia(EstadoTurno estado) {
        for (Instant ahora : new Instant[] {INICIO, INICIO.plusSeconds(1)}) {
            assertThatThrownBy(() -> ReglasCancelacionOdontologo.debeCancelar(estado, INICIO, ahora))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
                        assertThat(e.codigo()).isEqualTo("RN-TU-07");
                        assertThat(e.estado().value()).isEqualTo(409);
                        assertThat(e.getMessage()).isEqualTo("No podés cancelar un turno que ya empezó.");
                    });
        }
    }

    @ParameterizedTest
    @EnumSource(value = EstadoTurno.class, names = {"ATENDIDO", "AUSENTE"})
    void asistenciaFuturaEsInconsistenciaConExcepcionFijaSinCausa(EstadoTurno estado) {
        assertThatThrownBy(() -> ReglasCancelacionOdontologo.debeCancelar(estado, INICIO, INICIO.minusSeconds(1)))
                .isExactlyInstanceOf(IllegalStateException.class)
                .hasMessage("Estado de turno inconsistente para cancelación.").hasNoCause();
    }
}
