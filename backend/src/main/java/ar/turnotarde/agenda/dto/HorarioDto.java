package ar.turnotarde.agenda.dto;

import java.util.List;

/** Horario semanal devuelto por la API, ordenado por día y hora de inicio. */
public record HorarioDto(List<FranjaDto> franjas) {
}
