package ar.turnotarde.paciente;

import ar.turnotarde.shared.error.CodigosError;
import ar.turnotarde.shared.error.DetalleCampo;
import ar.turnotarde.shared.error.ErroresValidacion;
import ar.turnotarde.shared.error.ReglaNegocioException;
import java.util.ArrayList;
import org.springframework.http.HttpStatus;

record PaginacionPacientes(int page, int size) {
    PaginacionPacientes {
        var detalles = new ArrayList<DetalleCampo>();
        if (page < 0) {
            detalles.add(new DetalleCampo("page", "Ingresá una página mayor o igual a 0."));
        }
        if (size < 1 || size > 100) {
            detalles.add(new DetalleCampo("size", "Elegí un tamaño entre 1 y 100."));
        }
        if (!detalles.isEmpty()) {
            throw new ReglaNegocioException(CodigosError.VALIDACION, ErroresValidacion.MENSAJE,
                    HttpStatus.BAD_REQUEST, detalles);
        }
    }

    long offset() {
        return (long) page * size;
    }
}
