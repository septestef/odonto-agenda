package ar.turnotarde.shared.seguridad;

import ar.turnotarde.shared.error.CodigosError;
import ar.turnotarde.shared.error.ReglaNegocioException;
import java.util.Optional;
import org.springframework.http.HttpStatus;

/**
 * Contrato de aislamiento para C-05, C-12 y C-17 (D9): la consulta ya viene filtrada por
 * {@code OdontologoActual.id()}; acá solo se decide si el recurso existe para ese dueño.
 */
public final class AislamientoOdontologo {

    private static final String MENSAJE = "No encontramos lo que buscabas.";

    private AislamientoOdontologo() {
    }

    /** Devuelve el recurso o lanza el mismo 404 que un id inexistente: nunca revela un recurso ajeno. */
    public static <T> T exigir(Optional<T> recurso) {
        return recurso.orElseThrow(() -> new ReglaNegocioException(
                CodigosError.NO_ENCONTRADO, MENSAJE, HttpStatus.NOT_FOUND));
    }
}
