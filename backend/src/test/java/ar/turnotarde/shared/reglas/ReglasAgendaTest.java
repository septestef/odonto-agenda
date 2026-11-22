package ar.turnotarde.shared.reglas;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnotarde.shared.error.DetalleCampo;
import ar.turnotarde.shared.error.ReglaNegocioException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/** Contrato de los rechazos RN-AG-01 y RN-AG-03: código, estado y detalles sin SQL. */
class ReglasAgendaTest {

    @Test
    void franjaInvalidaUsaRnAg01Estado422YDetalleConIndice() {
        ReglaNegocioException error = ReglasAgenda.franjaInvalida(2, "La franja empieza después de terminar");

        assertThat(error.codigo()).isEqualTo("RN-AG-01");
        assertThat(error.estado()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(error.detalles()).isEqualTo(
                List.of(new DetalleCampo("franjas[2]", "La franja empieza después de terminar")));
        assertThat(error.getMessage()).contains("horario");
    }

    @Test
    void franjasSolapadasUsaRnAg01YSeñalaLaSegundaFranja() {
        ReglaNegocioException error = ReglasAgenda.franjasSolapadas(0, 1);

        assertThat(error.codigo()).isEqualTo("RN-AG-01");
        assertThat(error.estado()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(((List<?>) error.detalles())).singleElement().asString().contains("franjas[1]");
        assertThat(error.getMessage()).contains("superponen");
    }

    @Test
    void duracionInvalidaUsaRnAg03Estado422YDetalleDelCampo() {
        ReglaNegocioException error = ReglasAgenda.duracionInvalida();

        assertThat(error.codigo()).isEqualTo("RN-AG-03");
        assertThat(error.estado()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(error.detalles()).isEqualTo(
                List.of(new DetalleCampo("duracionMinutos", "La duración tiene que ser un múltiplo de 15 minutos.")));
    }

    @Test
    void losMensajesNoExponenSqlNiNombresDeConstraints() {
        List<ReglaNegocioException> errores = List.of(
                ReglasAgenda.franjaInvalida(0, "invertida"),
                ReglasAgenda.franjasSolapadas(0, 1),
                ReglasAgenda.duracionInvalida());

        for (ReglaNegocioException error : errores) {
            String texto = error.getMessage() + error.detalles();
            assertThat(texto).doesNotContain("SELECT", "CHECK", "constraint", "prestacion_duracion", "SQLState");
        }
    }
}
