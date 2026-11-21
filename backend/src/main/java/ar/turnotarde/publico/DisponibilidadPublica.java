package ar.turnotarde.publico;

import java.time.LocalDate;
import java.util.List;

/** Horarios libres de un día para una prestación: nada de turnos, pacientes ni motivos de bloqueo. */
public record DisponibilidadPublica(LocalDate fecha, long prestacionId, int duracionMinutos,
        List<HorarioLibre> horarios) {
}
