package ar.turnotarde.shared.tiempo;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnotarde.TurnoTardeApplication;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.support.SpringFactoriesLoader;

/**
 * La zona UTC del proceso se fija con un {@link ApplicationContextInitializer} registrado en
 * {@code META-INF/spring.factories}: así aplica antes de crear cualquier bean (DataSource, Hibernate) tanto en
 * {@code bootRun} y el jar como en un {@code @SpringBootTest} lanzado desde el IDE, que no pasa por {@code main}
 * ni por la propiedad {@code user.timezone} que fija Gradle.
 */
class ZonaUtcInicializadorTest {

    private TimeZone original;

    @BeforeEach
    void guardar() {
        original = TimeZone.getDefault();
    }

    @AfterEach
    void restaurar() {
        TimeZone.setDefault(original);
    }

    @Test
    void inicializarFijaUtcEnUnProcesoQueArrancoEnLaZonaDelNegocio() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));

        new ZonaUtcInicializador().initialize(new GenericApplicationContext());

        assertThat(TimeZone.getDefault().getID()).isEqualTo("UTC");
    }

    @Test
    void estaRegistradoEnSpringFactoriesParaCualquierContextoDeSpringBoot() {
        var inicializadores = SpringFactoriesLoader.forDefaultResourceLocation(getClass().getClassLoader())
                .load(ApplicationContextInitializer.class);

        assertThat(inicializadores).hasAtLeastOneElementOfType(ZonaUtcInicializador.class);
    }

    @Test
    void laAplicacionLoAplicaSinDependerDeMain() {
        SpringApplication aplicacion = new SpringApplication(TurnoTardeApplication.class);

        assertThat(aplicacion.getInitializers()).hasAtLeastOneElementOfType(ZonaUtcInicializador.class);
    }
}
