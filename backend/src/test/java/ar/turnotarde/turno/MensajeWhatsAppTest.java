package ar.turnotarde.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import ar.turnotarde.shared.error.ReglaNegocioException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class MensajeWhatsAppTest {
    private static final Instant AHORA = Instant.parse("2026-11-27T01:15:00Z");
    private static final Instant INICIO = Instant.parse("2026-11-27T01:30:00Z");
    private static final String TELEFONO = "+5493511234567";
    private static final String DIRECCION = "Av. Colón 123";
    private static final String NO_ELEGIBLE =
            "Solo podés preparar un WhatsApp para un turno reservado que todavía no empezó.";
    private final MensajeWhatsApp mensaje = new MensajeWhatsApp(Clock.fixed(AHORA, ZoneOffset.UTC));

    @Test
    void codificaExactamenteUnaVezYUsaElDiaAnteriorEnBuenosAires() {
        URI url = URI.create(mensaje.preparar(EstadoTurno.RESERVADO, INICIO, TELEFONO,
                "Limpieza & revisión + #50%", "Av. Colón 123 & Perú"));

        assertThat(url.toString()).isEqualTo("https://wa.me/5493511234567?text="
                + "Te%20recordamos%20tu%20turno%20del%2026%2F11%2F2026%20a%20las%2022%3A30"
                + "%20para%20Limpieza%20%26%20revisi%C3%B3n%20%2B%20%2350%25."
                + "%20Direcci%C3%B3n%3A%20Av.%20Col%C3%B3n%20123%20%26%20Per%C3%BA.");
        assertThat(url.getRawQuery().split("&")).hasSize(1);
        assertThat(URLDecoder.decode(url.getRawQuery().substring("text=".length()), StandardCharsets.UTF_8))
                .isEqualTo("Te recordamos tu turno del 26/11/2026 a las 22:30 para Limpieza & revisión + #50%."
                        + " Dirección: Av. Colón 123 & Perú.");
    }

    @Test
    void preservaSecuenciasQueYaParecenCodificadasYNoExpandeLlaves() {
        URI url = URI.create(mensaje.preparar(EstadoTurno.RESERVADO, INICIO, TELEFONO,
                "Consulta {mensaje}", "Piso %2B &text=otro"));

        assertThat(url.getRawQuery()).contains("%252B%20%26text%3Dotro").doesNotContain("&");
        assertThat(url.getRawFragment()).isNull();
        assertThat(URLDecoder.decode(url.getRawQuery(), StandardCharsets.UTF_8))
                .isEqualTo("text=Te recordamos tu turno del 26/11/2026 a las 22:30 para Consulta {mensaje}."
                        + " Dirección: Piso %2B &text=otro.");
    }

    @ParameterizedTest
    @EnumSource(value = EstadoTurno.class, names = {"CANCELADO", "ATENDIDO", "AUSENTE"})
    void rechazaOtrosEstadosAntesDeMirarContacto(EstadoTurno estado) {
        rechazo(estado, INICIO, null, null, HttpStatus.CONFLICT, "RN-NO-06", NO_ELEGIBLE);
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0})
    void rechazaInicioIgualOPasado(long segundos) {
        rechazo(EstadoTurno.RESERVADO, AHORA.plusSeconds(segundos), TELEFONO, DIRECCION,
                HttpStatus.CONFLICT, "RN-NO-06", NO_ELEGIBLE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"+12345678", "+123456789012345"})
    void admiteLosBordesE164YUnTurnoInminente(String telefono) {
        assertThat(URI.create(mensaje.preparar(EstadoTurno.RESERVADO, AHORA.plusNanos(1), telefono,
                "Consulta", DIRECCION)).getRawPath()).isEqualTo("/" + telefono.substring(1));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"3511234567", "+02345678", "+1234567", "+1234567890123456",
            "+54 93511234567", "+１２３４５６７８", " +5493511234567", "+5493511234567\n"})
    void rechazaTelefonoSinCorregirloInclusoSiTambienFaltaDireccion(String telefono) {
        rechazo(EstadoTurno.RESERVADO, INICIO, telefono, null, HttpStatus.UNPROCESSABLE_ENTITY,
                "RN-NO-07", "El paciente no tiene un teléfono internacional válido.");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" \t "})
    void rechazaDireccionAusenteSinInventarUna(String direccion) {
        rechazo(EstadoTurno.RESERVADO, INICIO, TELEFONO, direccion, HttpStatus.UNPROCESSABLE_ENTITY,
                "RN-NO-07", "Falta la dirección del consultorio.");
    }

    private void rechazo(EstadoTurno estado, Instant inicio, String telefono, String direccion,
            HttpStatus http, String codigo, String texto) {
        var error = catchThrowableOfType(ReglaNegocioException.class,
                () -> mensaje.preparar(estado, inicio, telefono, "Consulta", direccion));
        assertThat(error).isNotNull().hasMessage(texto);
        assertThat(error.codigo()).isEqualTo(codigo);
        assertThat(error.estado()).isEqualTo(http);
    }
}
