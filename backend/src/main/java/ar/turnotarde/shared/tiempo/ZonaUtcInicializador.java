package ar.turnotarde.shared.tiempo;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Fija la zona del proceso en UTC ({@link ZonaDelProceso#fijarUtc()}) antes de que se cree cualquier bean.
 * Registrado en {@code META-INF/spring.factories}, por lo que aplica a todo contexto de Spring Boot: {@code bootRun},
 * el jar empaquetado y los {@code @SpringBootTest} lanzados desde el IDE (que no pasan por {@code main} ni por la
 * propiedad {@code user.timezone} que fija Gradle). Sin esto, con {@code hibernate.jdbc.time_zone=UTC} y el proceso
 * en la zona de Windows, las columnas {@code time} de las franjas se leían corridas (09:00 como 06:00).
 */
public class ZonaUtcInicializador implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext contexto) {
        ZonaDelProceso.fijarUtc();
    }
}
