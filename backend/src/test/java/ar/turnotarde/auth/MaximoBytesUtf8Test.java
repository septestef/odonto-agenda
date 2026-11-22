package ar.turnotarde.auth;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnotarde.auth.dto.MaximoBytesUtf8;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

/** D2: el límite de BCrypt es de bytes UTF-8, no de caracteres. */
class MaximoBytesUtf8Test {

    private final Validator validador = Validation.buildDefaultValidatorFactory().getValidator();

    private record Peticion(@MaximoBytesUtf8(72) String password) {
    }

    @Test
    void setentaYDosBytesAsciiPasan() {
        assertThat(validador.validate(new Peticion("a".repeat(72)))).isEmpty();
    }

    @Test
    void setentaYDosCaracteresConEnyeSeRechazan() {
        String password = "a".repeat(71) + "ñ"; // 72 caracteres, 73 bytes

        assertThat(password.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(73);
        assertThat(validador.validate(new Peticion(password))).hasSize(1);
    }

    @Test
    void setentaYTresBytesAsciiSeRechazan() {
        assertThat(validador.validate(new Peticion("a".repeat(73)))).hasSize(1);
    }

    @Test
    void nuloYVacioPasanPorqueLosCubreNotBlank() {
        assertThat(validador.validate(new Peticion(null))).isEmpty();
        assertThat(validador.validate(new Peticion(""))).isEmpty();
    }
}
