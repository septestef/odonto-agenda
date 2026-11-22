package ar.turnotarde.auth.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

/** Mide bytes y no caracteres: una `ñ` ocupa 2 bytes y puede pasar `@Size(max = 72)` igual. */
public class MaximoBytesUtf8Validator implements ConstraintValidator<MaximoBytesUtf8, String> {

    private int max;

    @Override
    public void initialize(MaximoBytesUtf8 restriccion) {
        this.max = restriccion.value();
    }

    @Override
    public boolean isValid(String valor, ConstraintValidatorContext contexto) {
        return valor == null || valor.getBytes(StandardCharsets.UTF_8).length <= max;
    }
}
