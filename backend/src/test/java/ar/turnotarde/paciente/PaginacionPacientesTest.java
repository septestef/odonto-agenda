package ar.turnotarde.paciente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnotarde.shared.error.DetalleCampo;
import ar.turnotarde.shared.error.ReglaNegocioException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;

class PaginacionPacientesTest {
    @Test
    void primeraPaginaTieneOffsetCero() {
        var paginacion = new PaginacionPacientes(0, 20);
        assertThat(paginacion.page()).isZero();
        assertThat(paginacion.size()).isEqualTo(20);
        assertThat(paginacion.offset()).isZero();
    }

    @Test
    void incluyeAmbosDetallesSiPaginaYTamanioSonInvalidos() {
        assertThatThrownBy(() -> new PaginacionPacientes(-1, 0))
                .isInstanceOfSatisfying(ReglaNegocioException.class, e -> {
                    assertThat(e.estado()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.codigo()).isEqualTo("VALIDACION");
                    assertThat(e.getMessage()).isEqualTo("Hay datos inválidos. Revisalos y probá de nuevo.");
                    assertThat(e.detalles()).isEqualTo(List.of(
                            new DetalleCampo("page", "Ingresá una página mayor o igual a 0."),
                            new DetalleCampo("size", "Elegí un tamaño entre 1 y 100.")));
                });
    }

    @ParameterizedTest
    @CsvSource({"-1,20,page,Ingresá una página mayor o igual a 0.",
            "0,0,size,Elegí un tamaño entre 1 y 100.", "0,101,size,Elegí un tamaño entre 1 y 100."})
    void rechazaCadaBordeConSuDetalle(int page, int size, String campo, String mensaje) {
        assertThatThrownBy(() -> new PaginacionPacientes(page, size))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        e -> assertThat(e.detalles()).isEqualTo(List.of(new DetalleCampo(campo, mensaje))));
    }

    @ParameterizedTest
    @CsvSource({"1,1,1", "1,100,100", "2147483647,100,214748364700"})
    void limitesValidosCalculanOffsetSinOverflow(int page, int size, long offset) {
        assertThat(new PaginacionPacientes(page, size).offset()).isEqualTo(offset);
    }
}
