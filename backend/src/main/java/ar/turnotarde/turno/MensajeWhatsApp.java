package ar.turnotarde.turno;

import ar.turnotarde.shared.error.ReglaNegocioException;
import ar.turnotarde.shared.validacion.TelefonoInternacional;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
final class MensajeWhatsApp {
    private static final ZoneId BUENOS_AIRES = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final DateTimeFormatter FECHA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/uuuu 'a las' HH:mm", Locale.ROOT).withZone(BUENOS_AIRES);
    private final Clock reloj;

    MensajeWhatsApp(Clock reloj) {
        this.reloj = reloj;
    }

    String preparar(EstadoTurno estado, Instant inicio, String telefono, String prestacion, String direccion) {
        validar(estado, inicio, telefono, direccion);
        String texto = "Te recordamos tu turno del %s para %s. Dirección: %s."
                .formatted(FECHA_HORA.format(inicio), prestacion, direccion);
        // Las variables se expanden después de encode() para tratar el texto como dato opaco, incluso el '+'.
        return UriComponentsBuilder.fromUriString("https://wa.me/{telefono}")
                .queryParam("text", "{mensaje}")
                .encode(StandardCharsets.UTF_8)
                .buildAndExpand(telefono.substring(1), texto)
                .toUriString();
    }

    private void validar(EstadoTurno estado, Instant inicio, String telefono, String direccion) {
        if (estado != EstadoTurno.RESERVADO || !inicio.isAfter(reloj.instant())) {
            throw new ReglaNegocioException("RN-NO-06",
                    "Solo podés preparar un WhatsApp para un turno reservado que todavía no empezó.",
                    HttpStatus.CONFLICT);
        }
        if (!TelefonoInternacional.esValido(telefono)) {
            throw new ReglaNegocioException("RN-NO-07", "El paciente no tiene un teléfono internacional válido.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (direccion == null || direccion.isBlank()) {
            throw new ReglaNegocioException("RN-NO-07", "Falta la dirección del consultorio.",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
    }
}
