package ar.turnotarde.shared.error;

import java.util.List;
import org.springframework.http.HttpStatus;

/** Rechazos 400 `VALIDACION` generados en la capa de servicio (no por Bean Validation). */
public final class ErroresValidacion {

    public static final String MENSAJE = "Hay datos inválidos. Revisalos y probá de nuevo.";

    private ErroresValidacion() {
    }

    public static ReglaNegocioException campo(String campo, String mensaje) {
        return new ReglaNegocioException(CodigosError.VALIDACION, MENSAJE, HttpStatus.BAD_REQUEST,
                List.of(new DetalleCampo(campo, mensaje)));
    }
}
