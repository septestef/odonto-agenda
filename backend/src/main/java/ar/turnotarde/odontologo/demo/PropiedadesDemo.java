package ar.turnotarde.odontologo.demo;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Propiedades del perfil {@code demo}. La contraseña viene de la variable de entorno
 * {@code DEMO_PASSWORD}; si falta, el arranque falla con un mensaje que la nombra (sin mostrar valores).
 */
@ConfigurationProperties("app.demo")
@Validated
public record PropiedadesDemo(
        @NotBlank(message = "Falta DEMO_PASSWORD: definila en el entorno o en el .env para el perfil demo")
        String password) {

    /** Evita que la contraseña aparezca en un log o en un reporte de error. */
    @Override
    public String toString() {
        return "PropiedadesDemo[password=****]";
    }
}
