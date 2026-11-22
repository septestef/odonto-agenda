package ar.turnotarde.prestacion.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Cuerpo de alta y edición de prestaciones; el múltiplo de 15 lo valida la regla RN-AG-03. */
public record PeticionPrestacion(
        @NotBlank(message = "Ingresá el nombre de la prestación")
        @Size(max = 100, message = "El nombre es demasiado largo")
        String nombre,
        @NotNull(message = "Ingresá la duración en minutos")
        @Positive(message = "La duración tiene que ser mayor a cero")
        Integer duracionMinutos) {
}
