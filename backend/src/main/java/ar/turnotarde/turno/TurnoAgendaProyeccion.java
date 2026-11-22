package ar.turnotarde.turno;

import java.time.Instant;

/** Lectura plana: no materializa entidades ni selecciona secretos de gestión. */
public record TurnoAgendaProyeccion(Long id, Instant inicio, Instant fin, EstadoTurno estado, OrigenTurno origen,
        Long pacienteId, String dni, String nombre, String apellido, String telefono, String email,
        Long prestacionId, String prestacionNombre, CanceladoPor canceladoPor, Instant canceladoEn) {
}
