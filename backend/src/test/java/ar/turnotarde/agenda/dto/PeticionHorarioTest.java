package ar.turnotarde.agenda.dto;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

/** Validación estructural del cuerpo de {@code PUT /api/horarios}. */
class PeticionHorarioTest {

    private static final ValidatorFactory FABRICA = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDADOR = FABRICA.getValidator();

    @AfterAll
    static void cerrar() {
        FABRICA.close();
    }

    private static List<String> caminosInvalidos(PeticionHorario peticion) {
        Set<ConstraintViolation<PeticionHorario>> violaciones = VALIDADOR.validate(peticion);
        return violaciones.stream().map(v -> v.getPropertyPath().toString()).sorted().toList();
    }

    @Test
    void unaPeticionConFranjasValidasNoTieneViolaciones() {
        PeticionHorario peticion = new PeticionHorario(List.of(
                new FranjaDto((short) 1, "09:00", "13:00"),
                new FranjaDto((short) 7, "10:00", "12:00")));

        assertThat(caminosInvalidos(peticion)).isEmpty();
    }

    @Test
    void unaListaVaciaEsValidaParaQuitarTodasLasFranjas() {
        assertThat(caminosInvalidos(new PeticionHorario(List.of()))).isEmpty();
    }

    @Test
    void unaListaAusenteSeRechaza() {
        assertThat(caminosInvalidos(new PeticionHorario(null))).containsExactly("franjas");
    }

    @Test
    void diasFueraDelRangoSeRechazan() {
        assertThat(caminosInvalidos(new PeticionHorario(List.of(new FranjaDto((short) 0, "09:00", "10:00")))))
                .anySatisfy(camino -> assertThat(camino).contains("diaSemana"));
        assertThat(caminosInvalidos(new PeticionHorario(List.of(new FranjaDto((short) 8, "09:00", "10:00")))))
                .anySatisfy(camino -> assertThat(camino).contains("diaSemana"));
    }

    @Test
    void horasConFormatoInvalidoSeRechazan() {
        assertThat(caminosInvalidos(new PeticionHorario(List.of(new FranjaDto((short) 1, "9:00", "10:00")))))
                .anySatisfy(camino -> assertThat(camino).contains("inicio"));
        assertThat(caminosInvalidos(new PeticionHorario(List.of(new FranjaDto((short) 1, "09:00", "25:00")))))
                .anySatisfy(camino -> assertThat(camino).contains("fin"));
    }

    @Test
    void unaFranjaInvertidaNoEsErrorDeFormatoSinoDeReglaDeNegocio() {
        // La inversión (13:00 - 09:00) la rechaza ValidadorFranjas con RN-AG-01, no Bean Validation.
        assertThat(caminosInvalidos(new PeticionHorario(List.of(new FranjaDto((short) 1, "13:00", "09:00")))))
                .isEmpty();
    }
}
