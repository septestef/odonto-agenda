package ar.turnotarde.shared.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnotarde.shared.error.CodigosError;
import ar.turnotarde.shared.error.ReglaNegocioException;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** RN-SE-01: un recurso ajeno se responde como si no existiera. */
class AislamientoOdontologoTest {

    @Test
    void elRecursoPresenteSeDevuelveTalCual() {
        assertThat(AislamientoOdontologo.exigir(Optional.of("propio"))).isEqualTo("propio");
    }

    @Test
    void unRecursoAusenteLanza404NoEncontrado() {
        assertThatThrownBy(() -> AislamientoOdontologo.exigir(Optional.empty()))
                .isInstanceOf(ReglaNegocioException.class)
                .satisfies(error -> {
                    ReglaNegocioException regla = (ReglaNegocioException) error;
                    assertThat(regla.codigo()).isEqualTo(CodigosError.NO_ENCONTRADO);
                    assertThat(regla.estado()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(regla.getMessage()).isEqualTo("No encontramos lo que buscabas.");
                });
    }
}
