package ar.turnotarde.turno;

import java.time.Instant;

record TurnoWhatsAppProyeccion(EstadoTurno estado, Instant inicio, String telefono, String prestacion,
        String direccionConsultorio) {
}
