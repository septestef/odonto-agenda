package ar.turnotarde.soporte;

import java.time.Instant;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Reemplaza el Clock de producción por un {@link RelojDePrueba}. Se activa con {@code @Import}. */
@TestConfiguration(proxyBeanMethods = false)
public class ConfiguracionRelojDePrueba {

    @Bean
    @Primary
    public RelojDePrueba relojDePrueba() {
        return new RelojDePrueba(Instant.parse("2026-11-25T12:00:00Z"));
    }
}
