package ar.turnotarde.agenda.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.OffsetDateTime;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/** Validación estructural del alta de bloqueos; el rango invertido se valida en el servicio. */
class PeticionBloqueoTest {

    private static final ValidatorFactory FABRICA = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDADOR = FABRICA.getValidator();
    private static final OffsetDateTime INICIO = OffsetDateTime.parse("2026-11-27T10:00:00-03:00");
    private static final OffsetDateTime FIN = OffsetDateTime.parse("2026-11-27T12:00:00-03:00");

    @AfterAll
    static void cerrar() {
        FABRICA.close();
    }

    private static Set<String> camposInvalidos(PeticionBloqueo peticion) {
        return VALIDADOR.validate(peticion).stream()
                .map(v -> v.getPropertyPath().toString())
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void bloqueoConInstantesConOffsetYMotivoOpcionalEsValido() {
        assertThat(camposInvalidos(new PeticionBloqueo(INICIO, FIN, "Control particular"))).isEmpty();
        assertThat(camposInvalidos(new PeticionBloqueo(INICIO, FIN, null))).isEmpty();
    }

    @Test
    void instantesAusentesSeRechazan() {
        assertThat(camposInvalidos(new PeticionBloqueo(null, FIN, null))).containsExactly("inicio");
        assertThat(camposInvalidos(new PeticionBloqueo(INICIO, null, null))).containsExactly("fin");
    }

    @Test
    void motivoLargoSeRechaza() {
        assertThat(camposInvalidos(new PeticionBloqueo(INICIO, FIN, "a".repeat(256)))).containsExactly("motivo");
    }

    @Test
    void rangoInvertidoNoEsErrorEstructural() {
        // Lo rechaza ServicioBloqueos con 400 VALIDACION y detalle en "fin".
        assertThat(camposInvalidos(new PeticionBloqueo(FIN, INICIO, null))).isEmpty();
    }
}
