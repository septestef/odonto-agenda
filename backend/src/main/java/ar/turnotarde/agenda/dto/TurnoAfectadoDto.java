package ar.turnotarde.agenda.dto;

import java.time.OffsetDateTime;

/** Turno que cae dentro de un bloqueo. No expone DNI, email, teléfono ni token (RN-SE-03). */
public record TurnoAfectadoDto(
        Long id,
        OffsetDateTime inicio,
        OffsetDateTime fin,
        String estado,
        String pacienteNombre,
        String pacienteApellido,
        String prestacionNombre) {
}
