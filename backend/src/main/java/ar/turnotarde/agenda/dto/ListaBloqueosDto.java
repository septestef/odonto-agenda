package ar.turnotarde.agenda.dto;

import java.util.List;

/** Envelope de {@code GET /api/bloqueos} (D2). */
public record ListaBloqueosDto(List<BloqueoDto> bloqueos) {
}
