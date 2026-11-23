package ar.turnotarde.publico;

import ar.turnotarde.shared.validacion.TelefonoInternacional;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * Cuerpo de {@code POST /api/public/{slug}/turnos} (RN-PA-04). {@code inicio} lleva offset: sin offset no se
 * parsea y la API responde 400 en lugar de interpretar la hora del cliente. No se normaliza nada (la UI lo hace).
 * Cada campo tiene una sola regla de formato para informar un único detalle por campo inválido.
 */
public record SolicitudReserva(
        @NotNull(message = "Elegí una prestación") @Positive(message = "Elegí una prestación") Long prestacionId,
        @NotNull(message = "Elegí un horario") OffsetDateTime inicio,
        @NotNull(message = "Ingresá tu DNI sin puntos, 7 u 8 dígitos")
        @Pattern(regexp = "^[0-9]{7,8}$", message = "Ingresá tu DNI sin puntos, 7 u 8 dígitos") String dni,
        @NotBlank(message = "Ingresá tu nombre") @Size(max = 100, message = "El nombre es demasiado largo")
        String nombre,
        @NotBlank(message = "Ingresá tu apellido") @Size(max = 100, message = "El apellido es demasiado largo")
        String apellido,
        @NotNull(message = "Ingresá el teléfono con código de país, por ejemplo +5493511234567")
        @Pattern(regexp = TelefonoInternacional.E164,
                message = "Ingresá el teléfono con código de país, por ejemplo +5493511234567") String telefono,
        @NotNull(message = "Ingresá un email válido")
        @Pattern(regexp = EMAIL, message = "Ingresá un email válido") String email) {

    /**
     * Email de 6 a 255 caracteres en total (el largo de la columna), parte local de 1 a 64 caracteres y dominio sin
     * puntos dobles, que no empieza ni termina con punto y tiene al menos uno. El largo se controla en la misma regex
     * (no con {@code @Size}) para informar un único detalle por campo. El lookahead de largo corta enseguida
     * cualquier entrada enorme y no hay grupos repetidos (que en {@code java.util.regex} recursan y desbordan la
     * pila). La regex anterior ({@code [^@\s]+@[^@\s]+\.[^@\s]+}) retrocedía de forma cuadrática con entradas
     * largas y bloqueaba un hilo del endpoint público (ReDoS).
     */
    static final String EMAIL =
            "^(?=[^\\s]{6,255}$)[^@\\s]{1,64}@(?!.*\\.\\.)[^@\\s.][^@\\s]*\\.[^@\\s.]+$";
}
