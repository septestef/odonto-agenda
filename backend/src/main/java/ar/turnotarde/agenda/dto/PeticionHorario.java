package ar.turnotarde.agenda.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Cuerpo de {@code PUT /api/horarios}: el horario semanal completo. */
public record PeticionHorario(
        @NotNull(message = "Enviá la lista de franjas, aunque sea vacía") List<@Valid FranjaDto> franjas) {
}
