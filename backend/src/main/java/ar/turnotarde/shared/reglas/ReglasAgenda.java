package ar.turnotarde.shared.reglas;

import ar.turnotarde.shared.error.DetalleCampo;
import ar.turnotarde.shared.error.ReglaNegocioException;
import java.util.List;
import org.springframework.http.HttpStatus;

/** Fábrica de los rechazos de reglas de configuración de agenda (RN-AG-01 y RN-AG-03). */
public final class ReglasAgenda {

    public static final String FRANJA = "RN-AG-01";
    public static final String DURACION = "RN-AG-03";

    private static final String MENSAJE_FRANJA = "Revisá el horario de atención: hay franjas inválidas.";
    private static final String MENSAJE_DURACION = "La duración tiene que ser un múltiplo de 15 minutos.";

    private ReglasAgenda() {
    }

    /** Una franja concreta, identificada por su índice en la solicitud. */
    public static ReglaNegocioException franjaInvalida(int indice, String mensaje) {
        return new ReglaNegocioException(FRANJA, MENSAJE_FRANJA, HttpStatus.UNPROCESSABLE_ENTITY,
                List.of(new DetalleCampo("franjas[" + indice + "]", mensaje)));
    }

    /** Dos franjas del mismo día que se pisan; se señala la segunda para que la UI pueda resaltarla. */
    public static ReglaNegocioException franjasSolapadas(int primera, int segunda) {
        String mensaje = "Las franjas " + (primera + 1) + " y " + (segunda + 1)
                + " se superponen. Elegí horarios que no se pisen.";
        return new ReglaNegocioException(FRANJA, mensaje, HttpStatus.UNPROCESSABLE_ENTITY,
                List.of(new DetalleCampo("franjas[" + segunda + "]", mensaje)));
    }

    public static ReglaNegocioException duracionInvalida() {
        return new ReglaNegocioException(DURACION, MENSAJE_DURACION, HttpStatus.UNPROCESSABLE_ENTITY,
                List.of(new DetalleCampo("duracionMinutos", MENSAJE_DURACION)));
    }
}
