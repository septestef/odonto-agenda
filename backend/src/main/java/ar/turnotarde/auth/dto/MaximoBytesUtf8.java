package ar.turnotarde.auth.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** El valor no puede superar {@link #value()} bytes en UTF-8 (límite real de BCrypt). */
@Documented
@Constraint(validatedBy = MaximoBytesUtf8Validator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT,
        ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaximoBytesUtf8 {

    String message() default "no puede superar los {value} bytes UTF-8";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    int value();
}
