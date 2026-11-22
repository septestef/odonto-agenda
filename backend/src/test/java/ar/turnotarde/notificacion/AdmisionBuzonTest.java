package ar.turnotarde.notificacion;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnotarde.publico.SolicitudReserva;
import ar.turnotarde.shared.correo.BuzonCorreo;
import jakarta.validation.Validation;
import java.time.OffsetDateTime;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AdmisionBuzonTest {
    @ParameterizedTest
    @MethodSource("buzones")
    void conservaLasFronterasDistintasDeReservaYCorreo(String email, boolean reserva, boolean aviso) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var solicitud = new SolicitudReserva(1L, OffsetDateTime.parse("2026-11-27T09:00:00-03:00"),
                    "12345678", "Ana", "Pérez", "+5493511234567", email);
            assertThat(factory.getValidator().validateProperty(solicitud, "email").isEmpty()).isEqualTo(reserva);
        }
        assertThat(BuzonCorreo.admisible(email)).isEqualTo(aviso);
    }

    private static Stream<Arguments> buzones() {
        String base = "a".repeat(64) + "@" + "b".repeat(63) + "." + "c".repeat(63) + ".";
        String hasta254 = base + "d".repeat(61);
        String hasta255 = base + "d".repeat(62);
        return Stream.of(
                Arguments.of(null, false, false),
                Arguments.of("Ana.Perez@example.com", true, true),
                Arguments.of(hasta254, true, true),
                Arguments.of(hasta255, true, false),
                Arguments.of("ana@ejemplo_com.test", true, false),
                Arguments.of("ana@example.com ", false, false));
    }
}
