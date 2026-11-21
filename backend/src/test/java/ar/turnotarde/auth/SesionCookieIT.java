package ar.turnotarde.auth;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnotarde.soporte.AutenticacionDePrueba;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.Cookie.SameSite;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/** D3 y D5: las cookies reales que emite Tomcat, no las que simula MockMvc. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SesionCookieIT extends IntegracionTest {

    private static final String CLAVE = "ClaveSegura123!";

    @Value("${local.server.port}")
    private int puerto;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ServerProperties propiedadesServidor;

    private String email;

    @BeforeEach
    void preparar() {
        LimpiezaBase.limpiar(jdbc);
        email = "u-" + UUID.randomUUID() + "@example.com";
        new FixturesJdbc(jdbc).odontologo("demo-" + UUID.randomUUID(), email,
                AutenticacionDePrueba.hash(CLAVE), true);
    }

    @AfterEach
    void dejarLaBaseVacia() {
        LimpiezaBase.limpiar(jdbc);
    }

    @Test
    void elLoginEmiteJsessionidHttpOnlyConSameSiteLax() {
        List<String> cookies = login().getHeaders().get(HttpHeaders.SET_COOKIE);

        assertThat(cookies).isNotNull();
        String sesion = cookies.stream().filter(c -> c.startsWith("JSESSIONID=")).findFirst()
                .orElseThrow(() -> new AssertionError("Falta la cookie JSESSIONID: " + cookies));
        assertThat(sesion).contains("HttpOnly").contains("SameSite=Lax");
        assertThat(sesion).doesNotContain("SameSite=None");
    }

    @Test
    void elTokenCsrfVaEnCookieLeyiblePorElFrontend() {
        List<String> cookies = login().getHeaders().get(HttpHeaders.SET_COOKIE);

        assertThat(cookies).isNotNull();
        String csrf = cookies.stream().filter(c -> c.startsWith("XSRF-TOKEN=")).findFirst()
                .orElseThrow(() -> new AssertionError("Falta la cookie XSRF-TOKEN: " + cookies));
        assertThat(csrf).doesNotContain("HttpOnly");
    }

    @Test
    void laConfiguracionEfectivaDaSesionesDeOchoHoras() {
        assertThat(propiedadesServidor.getServlet().getSession().getTimeout())
                .isEqualTo(Duration.ofHours(8));
        assertThat(propiedadesServidor.getServlet().getSession().getCookie().getName())
                .isEqualTo("JSESSIONID");
        assertThat(propiedadesServidor.getServlet().getSession().getCookie().getHttpOnly()).isTrue();
        assertThat(propiedadesServidor.getServlet().getSession().getCookie().getSameSite())
                .isEqualTo(SameSite.LAX);
    }

    private ResponseEntity<String> login() {
        ResponseEntity<String> respuesta = RestClient.builder().baseUrl("http://localhost:" + puerto)
                .build().post().uri("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("email", email, "password", CLAVE))
                .retrieve().toEntity(String.class);
        assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
        return respuesta;
    }
}
