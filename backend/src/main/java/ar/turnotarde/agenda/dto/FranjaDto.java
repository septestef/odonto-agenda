package ar.turnotarde.agenda.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Franja semanal de atención: día ISO (1 = lunes) y horas locales en formato {@code HH:mm}. */
public record FranjaDto(
        @Min(value = 1, message = "Elegí un día entre el lunes (1) y el domingo (7)")
        @Max(value = 7, message = "Elegí un día entre el lunes (1) y el domingo (7)")
        short diaSemana,
        @NotBlank(message = "Escribí la hora de inicio como HH:mm")
        @Pattern(regexp = HORA, message = "Escribí la hora de inicio como HH:mm")
        String inicio,
        @NotBlank(message = "Escribí la hora de fin como HH:mm")
        @Pattern(regexp = HORA, message = "Escribí la hora de fin como HH:mm")
        String fin) {

    private static final String HORA = "^(?:[01][0-9]|2[0-3]):[0-5][0-9]$";
}
