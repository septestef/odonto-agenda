package ar.turnotarde.shared.tiempo;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Único reloj de la aplicación: UTC. Los tests lo reemplazan por uno fijo. */
@Configuration
public class ConfiguracionTiempo {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
