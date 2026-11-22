package ar.turnotarde.agenda.dto;

import java.time.OffsetDateTime;
import java.util.List;

/** Bloqueo con los turnos que caen en su rango, sin cancelarlos (RN-AG-02). */
public record BloqueoDto(
        Long id,
        OffsetDateTime inicio,
        OffsetDateTime fin,
        String motivo,
        List<TurnoAfectadoDto> turnosAfectados) {
}
