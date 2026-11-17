package ar.turnotarde.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.validation.ValidationBindHandler;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class AppPropertiesTest {

    private static AppProperties enlazar(Map<String, String> valores) {
        LocalValidatorFactoryBean validador = new LocalValidatorFactoryBean();
        validador.afterPropertiesSet();
        Binder binder = new Binder(new MapConfigurationPropertySource(valores));
        return binder.bind("app", org.springframework.boot.context.properties.bind.Bindable.of(AppProperties.class),
                new ValidationBindHandler(validador)).get();
    }

    @Test
    void sinConfiguracionUsaLosValoresPorDefecto() {
        AppProperties props = enlazar(Map.of("app.reserva-max-dias", "60"));

        assertThat(props.timezone()).isEqualTo(ZoneId.of("America/Argentina/Buenos_Aires"));
        assertThat(props.reservaMaxDias()).isEqualTo(60);
        assertThat(props.baseUrl()).isEqualTo(URI.create("http://localhost:8080"));
    }

    @Test
    void respetaLosValoresConfigurados() {
        AppProperties props = enlazar(Map.of(
                "app.timezone", "UTC",
                "app.base-url", "https://turnos.example.org",
                "app.reserva-max-dias", "30"));

        assertThat(props.timezone()).isEqualTo(ZoneId.of("UTC"));
        assertThat(props.baseUrl()).isEqualTo(URI.create("https://turnos.example.org"));
        assertThat(props.reservaMaxDias()).isEqualTo(30);
    }

    @Test
    void unaZonaInvalidaHaceFallarElEnlace() {
        assertThatThrownBy(() -> enlazar(Map.of("app.timezone", "Mars/Olympus_Mons")))
                .isInstanceOf(BindException.class);
    }

    @Test
    void unHorizonteMenorAUnoHaceFallarElEnlace() {
        assertThatThrownBy(() -> enlazar(Map.of("app.reserva-max-dias", "0")))
                .isInstanceOf(BindException.class);
    }
}
