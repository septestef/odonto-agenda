package ar.turnotarde.turno.eventos;

import java.time.Instant;

/**
 * Copia inmutable de lo necesario para notificar un turno (sin entidades JPA ni lazy loading).
 * Los instantes son UTC: el consumidor los formatea con {@code ZonaNegocio}.
 */
public record ResumenTurno(
        long turnoId, long odontologoId, Instant inicio, Instant fin,
        long prestacionId, String prestacionNombre, int duracionMinutos,
        String pacienteNombre, String pacienteApellido, String pacienteEmail, String pacienteTelefono,
        String odontologoNombre, String odontologoApellido, String direccionConsultorio) {
}
