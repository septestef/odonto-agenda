package ar.turnotarde.prestacion.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/** Validación estructural de alta y edición de prestaciones. */
class PeticionPrestacionTest {

    private static final ValidatorFactory FABRICA = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDADOR = FABRICA.getValidator();

    @AfterAll
    static void cerrar() {
        FABRICA.close();
    }

    private static Set<String> camposInvalidos(PeticionPrestacion peticion) {
        return VALIDADOR.validate(peticion).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void nombreYDuracionValidosNoTienenViolaciones() {
        assertThat(camposInvalidos(new PeticionPrestacion("Limpieza", 45))).isEmpty();
    }

    @Test
    void nombreVacioYOseRechazan() {
        assertThat(camposInvalidos(new PeticionPrestacion("  ", 30))).containsExactly("nombre");
        assertThat(camposInvalidos(new PeticionPrestacion(null, 30))).containsExactly("nombre");
    }

    @Test
    void nombreLargoSeRechaza() {
        assertThat(camposInvalidos(new PeticionPrestacion("a".repeat(101), 30))).containsExactly("nombre");
    }

    @Test
    void duracionNoPositivaOAusenteSeRechaza() {
        assertThat(camposInvalidos(new PeticionPrestacion("Consulta", null))).containsExactly("duracionMinutos");
        assertThat(camposInvalidos(new PeticionPrestacion("Consulta", 0))).containsExactly("duracionMinutos");
        assertThat(camposInvalidos(new PeticionPrestacion("Consulta", -15))).containsExactly("duracionMinutos");
    }

    @Test
    void duracionNoMultipleDe15NoEsErrorEstructuralSinoDeRegla() {
        // RN-AG-03 se valida en ServicioPrestaciones, no acá.
        assertThat(camposInvalidos(new PeticionPrestacion("Consulta", 20))).isEmpty();
    }
}
