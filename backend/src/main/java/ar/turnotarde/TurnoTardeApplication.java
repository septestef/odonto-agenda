package ar.turnotarde;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * La zona UTC del proceso la fija {@code shared.tiempo.ZonaUtcInicializador}, registrado en
 * {@code META-INF/spring.factories}, para que aplique también a los contextos que no pasan por {@code main}.
 */
@SpringBootApplication
public class TurnoTardeApplication {

    public static void main(String[] args) {
        SpringApplication.run(TurnoTardeApplication.class, args);
    }
}
