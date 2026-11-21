package ar.turnotarde.shared.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.web.server.Cookie;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.boot.web.server.servlet.Session;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/** La configuración de sesión de {@code application.yml} se lee igual que la usa el servidor. */
class SesionPropiedadesTest {

    private static ServerProperties servidor() throws Exception {
        StandardEnvironment entorno = new StandardEnvironment();
        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                .forEach(fuente -> entorno.getPropertySources().addLast(fuente));
        ConfigurationPropertySources.attach(entorno);
        return Binder.get(entorno).bind("server", ServerProperties.class).get();
    }

    @Test
    void laSesionExpiraALasOchoHoras() throws Exception {
        assertThat(servidor().getServlet().getSession().getTimeout()).isEqualTo(Duration.ofHours(8));
    }

    @Test
    void elIdentificadorSoloSeAceptaPorCookie() throws Exception {
        assertThat(servidor().getServlet().getSession().getTrackingModes())
                .containsExactly(Session.SessionTrackingMode.COOKIE);
    }

    @Test
    void laCookieDeSesionEsHttpOnlyYSameSiteLax() throws Exception {
        Cookie cookie = servidor().getServlet().getSession().getCookie();
        assertThat(cookie.getName()).isEqualTo("JSESSIONID");
        assertThat(cookie.getHttpOnly()).isTrue();
        assertThat(cookie.getSameSite()).isEqualTo(Cookie.SameSite.LAX);
    }
}
