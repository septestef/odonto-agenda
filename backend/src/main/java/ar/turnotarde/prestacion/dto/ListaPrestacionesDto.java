package ar.turnotarde.prestacion.dto;

import java.util.List;

/** Envelope de {@code GET /api/prestaciones} (D2). */
public record ListaPrestacionesDto(List<PrestacionDto> prestaciones) {
}
