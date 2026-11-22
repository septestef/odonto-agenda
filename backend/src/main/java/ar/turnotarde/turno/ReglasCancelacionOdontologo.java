package ar.turnotarde.turno;

import ar.turnotarde.shared.error.ReglaNegocioException;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

final class ReglasCancelacionOdontologo {
    private static final Logger LOG = LoggerFactory.getLogger(ReglasCancelacionOdontologo.class);
    private ReglasCancelacionOdontologo() {
    }

    static boolean debeCancelar(EstadoTurno estado, Instant inicio, Instant ahora) {
        if (estado == EstadoTurno.CANCELADO) {
            return false;
        }
        if (!inicio.isAfter(ahora)) {
            throw new ReglaNegocioException("RN-TU-07", "No podés cancelar un turno que ya empezó.", HttpStatus.CONFLICT);
        }
        if (estado == EstadoTurno.ATENDIDO || estado == EstadoTurno.AUSENTE) {
            LOG.error("CANCELACION_ODONTOLOGO_ESTADO_INCONSISTENTE estado={}", estado);
            throw new IllegalStateException("Estado de turno inconsistente para cancelación.");
        }
        return true;
    }
}
