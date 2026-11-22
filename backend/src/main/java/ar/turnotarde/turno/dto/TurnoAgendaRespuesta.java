package ar.turnotarde.turno.dto;

import ar.turnotarde.paciente.dto.PacienteRespuesta;
import ar.turnotarde.turno.CanceladoPor;
import ar.turnotarde.turno.EstadoTurno;
import ar.turnotarde.turno.OrigenTurno;
import java.time.OffsetDateTime;

public record TurnoAgendaRespuesta(long id, OffsetDateTime inicio, OffsetDateTime fin, EstadoTurno estado,
        OrigenTurno origen, PacienteRespuesta paciente, PrestacionAgendaRespuesta prestacion,
        CanceladoPor canceladoPor, OffsetDateTime canceladoEn) {

    public record PrestacionAgendaRespuesta(long id, String nombre, long duracionMinutos) {
    }
}
