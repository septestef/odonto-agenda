package ar.turnotarde.turno.dto;

import ar.turnotarde.publico.SolicitudReserva;
import ar.turnotarde.shared.validacion.TelefonoInternacional;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * Cuerpo de {@code POST /api/turnos} (RN-PA-04). Reutiliza las mismas restricciones de la reserva pública para
 * que los errores de campo y sus mensajes sean idénticos. El odontólogo no se recibe: sale de la sesión (RN-SE-01).
 */
public record SolicitudTurnoManual(
        @NotNull(message = "Elegí una prestación") @Positive(message = "Elegí una prestación") Long prestacionId,
        @NotNull(message = "Elegí un horario") OffsetDateTime inicio,
        @NotNull(message = "Ingresá el DNI del paciente sin puntos, 7 u 8 dígitos")
        @Pattern(regexp = "^[0-9]{7,8}$", message = "Ingresá el DNI del paciente sin puntos, 7 u 8 dígitos") String dni,
        @NotBlank(message = "Ingresá el nombre del paciente") @Size(max = 100, message = "El nombre es demasiado largo")
        String nombre,
        @NotBlank(message = "Ingresá el apellido del paciente") @Size(max = 100, message = "El apellido es demasiado largo")
        String apellido,
        @NotNull(message = "Ingresá el teléfono del paciente con código de país, por ejemplo +5493511234567")
        @Pattern(regexp = TelefonoInternacional.E164,
                message = "Ingresá el teléfono del paciente con código de país, por ejemplo +5493511234567") String telefono,
        @NotNull(message = "Ingresá un email válido del paciente")
        @Pattern(regexp = SolicitudReserva.EMAIL, message = "Ingresá un email válido del paciente") String email) {
}
