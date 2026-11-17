package ar.turnotarde.shared.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Propiedades de negocio de la aplicación (prefijo {@code app}).
 *
 * @param timezone       zona horaria del negocio, solo para interpretar y mostrar fechas
 * @param baseUrl        URL base para los links de los emails
 * @param reservaMaxDias horizonte máximo de reserva, en días
 */
@Validated
@ConfigurationProperties("app")
public record AppProperties(
        @NotNull @DefaultValue("America/Argentina/Buenos_Aires") ZoneId timezone,
        @NotNull @DefaultValue("http://localhost:8080") URI baseUrl,
        @Min(1) @DefaultValue("60") int reservaMaxDias) {
}
