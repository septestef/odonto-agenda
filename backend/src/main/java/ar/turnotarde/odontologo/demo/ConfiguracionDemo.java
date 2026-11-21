package ar.turnotarde.odontologo.demo;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Habilita las propiedades del perfil demo (y su validación fail-fast). Solo con el perfil {@code demo}. */
@Configuration
@Profile("demo")
@EnableConfigurationProperties(PropiedadesDemo.class)
public class ConfiguracionDemo {
}
