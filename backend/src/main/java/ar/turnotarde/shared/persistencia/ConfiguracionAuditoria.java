package ar.turnotarde.shared.persistencia;

import java.time.Clock;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Activa la auditoría JPA con el {@link Clock} de la aplicación. Vive en una configuración propia
 * (y no en la clase principal) para no romper los tests de slice web.
 */
@Configuration
@EnableJpaAuditing(dateTimeProviderRef = "proveedorFechaAuditoria")
public class ConfiguracionAuditoria {

    @Bean
    public DateTimeProvider proveedorFechaAuditoria(Clock reloj) {
        return () -> Optional.of(reloj.instant());
    }
}
