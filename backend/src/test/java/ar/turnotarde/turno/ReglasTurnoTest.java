package ar.turnotarde.turno;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnotarde.shared.error.ReglaNegocioException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ReglasTurnoTest {

    private static void verificar(ReglaNegocioException e, String codigo, HttpStatus estado, String mensaje) {
        assertThat(e.codigo()).isEqualTo(codigo);
        assertThat(e.estado()).isEqualTo(estado);
        assertThat(e.getMessage()).isEqualTo(mensaje);
    }

    @Test
    void tablaDeErroresDeD4() {
        verificar(ReglasTurno.horarioTomado(), "RN-TU-01", HttpStatus.CONFLICT,
                "Ese horario acaba de ser tomado, elegí otro");
        verificar(ReglasTurno.limiteDeTurnos(), "RN-PA-03", HttpStatus.CONFLICT,
                "Ya tenés 2 turnos reservados en este consultorio. Cancelá uno desde el link del email para sacar otro.");
        verificar(ReglasTurno.anticipacionInsuficiente(), "RN-TU-03", HttpStatus.UNPROCESSABLE_ENTITY,
                "Los turnos se reservan con al menos 24 horas de anticipación. Elegí otro horario.");
        verificar(ReglasTurno.fueraDeHorizonte(60), "RN-TU-11", HttpStatus.UNPROCESSABLE_ENTITY,
                "Todavía no se puede reservar para esa fecha. Elegí un día dentro de los próximos 60 días.");
        verificar(ReglasTurno.fueraDeAgenda(), "RN-AG-05", HttpStatus.UNPROCESSABLE_ENTITY,
                "Ese horario no está disponible. Elegí uno de la lista.");
    }

    @Test
    void elMensajeDelHorizonteUsaElValorConfigurado() {
        assertThat(ReglasTurno.fueraDeHorizonte(30).getMessage()).contains("próximos 30 días");
    }
}
