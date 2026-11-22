package ar.turnotarde.agenda;

import ar.turnotarde.turno.EstadoTurno;
import java.time.Instant;

/**
 * Proyección intermedia de los turnos que caen dentro de un bloqueo. JPQL no puede construir un
 * {@code OffsetDateTime}: el servicio convierte estos instantes con {@code ZonaNegocio} antes de
 * exponer {@code TurnoAfectadoDto}. No incluye DNI, email, teléfono ni token (RN-SE-03).
 */
public record TurnoAfectadoProyeccion(
        Long id,
        Instant inicio,
        Instant fin,
        EstadoTurno estado,
        String pacienteNombre,
        String pacienteApellido,
        String prestacionNombre) {
}
