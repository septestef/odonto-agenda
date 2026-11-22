package ar.turnotarde.agenda.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/** Cuerpo de {@code POST /api/bloqueos}. Los instantes viajan con offset obligatorio. */
public record PeticionBloqueo(
        @NotNull(message = "Elegí el inicio del bloqueo") OffsetDateTime inicio,
        @NotNull(message = "Elegí el fin del bloqueo") OffsetDateTime fin,
        @Size(max = 255, message = "El motivo es demasiado largo") String motivo) {
}
