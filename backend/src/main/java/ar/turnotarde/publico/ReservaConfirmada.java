package ar.turnotarde.publico;

import java.time.OffsetDateTime;

/**
 * Cuerpo del 201 (D3): sin token y sin id de turno. {@code email} es el que el paciente acaba de ingresar,
 * para mostrar "Te mandamos el link a ...".
 */
public record ReservaConfirmada(OffsetDateTime inicio, OffsetDateTime fin, PrestacionPublica prestacion,
        OdontologoPublico odontologo, String email) {
}
