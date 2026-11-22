package ar.turnotarde.publico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;

import ar.turnotarde.soporte.ConfiguracionRelojDePrueba;
import ar.turnotarde.soporte.AutenticacionDePrueba;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import ar.turnotarde.turno.TokenTurno;
import ar.turnotarde.turno.TurnoRepository;
import ar.turnotarde.shared.error.ManejadorErrores;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "app.frontend.ubicacion=classpath:/spa-prueba/")
@Import({ConfiguracionRelojDePrueba.class, ConfidencialidadTurnoTokenIT.Instrumentacion.class})
class ConfidencialidadTurnoTokenIT extends IntegracionTest {
    private static final String TOKEN_SINTETICO = "CredencialSintetica" + "A".repeat(24);
    private static final String HASH_SINTETICO = TokenTurno.hashear(TOKEN_SINTETICO);
    private static final String API = "/api/public/turnos/";
    private static final String EMAIL_SINTETICO = "centinela-log@example.test";
    private static final String URI_SINTETICA = API + TOKEN_SINTETICO;
    private static final String MENSAJE_INTERNO = "Diagnostico interno sintetico";
    private static final String CLAVE = "PruebaSintetica123!";
    private static final String CATEGORIA = "GESTION_TURNO_ERROR_INESPERADO";

    @LocalServerPort
    private int puerto;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private Environment entorno;
    @MockitoSpyBean
    private TurnoRepository turnos;

    @BeforeEach
    void preparar() {
        LimpiezaBase.limpiar(jdbc);
        var fx = new FixturesJdbc(jdbc);
        long od = fx.odontologo("demo", "odontologo@example.test", AutenticacionDePrueba.hash(CLAVE), true);
        long pr = fx.prestacion(od, "Consulta", 30);
        long paciente = fx.paciente(od, "12345678");
        Instant inicio = Instant.parse("2026-11-27T12:00:00Z");
        fx.turno(od, paciente, pr, inicio, inicio.plusSeconds(1800), "RESERVADO", HASH_SINTETICO);
    }

    @AfterEach
    void limpiar() {
        LimpiezaBase.limpiar(jdbc);
    }

    @Test
    void protegeElDetalleLos404YElDocumentoDelLinkSinSesion() throws Exception {
        var detalle = get(API + TOKEN_SINTETICO);
        var desconocido = get(API + "B".repeat(42) + "A");
        var invalido = get(API + "B".repeat(42));
        var documento = get("/turno/" + TOKEN_SINTETICO);
        assertThat(detalle.statusCode()).isEqualTo(200);
        assertThat(desconocido.statusCode()).isEqualTo(404);
        assertThat(invalido.statusCode()).isEqualTo(404);
        assertThat(documento.statusCode()).isEqualTo(200);
        assertThat(get("/api/horarios").statusCode()).isEqualTo(401);
        var nombres = new ArrayList<String>();
        for (var respuesta : List.of(detalle, desconocido, invalido, documento)) {
            nombres.addAll(sinSesionNiCredenciales(respuesta));
        }
        assertThat(nombres.stream().distinct().toList()).containsExactly("XSRF-TOKEN");
        for (var respuesta : List.of(detalle, desconocido, invalido, documento)) {
            headersPrivados(respuesta);
        }
        assertThat(get("/api/public/demo").headers().firstValue("Referrer-Policy")).isEmpty();
        assertThat(get(API + "disponibilidad").headers().firstValue("Referrer-Policy")).isEmpty();
    }

    @Test
    void elEntornoLocalNoTieneHabilitadoElAccessLogDeTomcat() {
        assertThat(entorno.getProperty("server.tomcat.accesslog.enabled", Boolean.class, false)).isFalse();
    }

    @Test
    void unFalloInesperadoAnidadoNoExponeDatosEnRespuestaNiLog() throws Exception {
        var causa = new IllegalArgumentException(MENSAJE_INTERNO + " " + EMAIL_SINTETICO);
        var fallo = new IllegalStateException(URI_SINTETICA + " " + HASH_SINTETICO, causa);
        errorSinExponer(fallo, "java.lang.IllegalStateException", "java.lang.IllegalArgumentException");
    }

    @Test
    void otroFalloDeIntegridadConserva500YSanitizaTodaLaCadena() throws Exception {
        errorSinExponer(new DataIntegrityViolationException(URI_SINTETICA,
                        new SQLException(MENSAJE_INTERNO + " " + EMAIL_SINTETICO + " " + HASH_SINTETICO, "23505")),
                "org.springframework.dao.DataIntegrityViolationException", "java.sql.SQLException");
    }

    @Test
    void solapamientoNombradoConserva409SinRegistrarLaExcepcion() throws Exception {
        doThrow(new DataIntegrityViolationException(MENSAJE_INTERNO,
                new SQLException("turno_sin_solapamiento", "23P01")))
                .when(turnos).findByTokenHash(HASH_SINTETICO);
        try (var captura = new LogAdvice()) {
            var respuesta = get(URI_SINTETICA);
            assertThat(respuesta.statusCode()).isEqualTo(409);
            assertThat(respuesta.body()).contains("RN-TU-01").doesNotContain("23P01", "turno_sin_solapamiento");
            assertThat(captura.eventos()).isEmpty();
        }
    }

    @Test
    void elAdviceConservaThrowableCompletoParaUnaRutaPrivadaAutenticada() throws Exception {
        var login = enviar(HttpRequest.newBuilder(uri("/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("""
                        {"email":"odontologo@example.test","password":"%s"}""".formatted(CLAVE))));
        assertThat(login.statusCode()).isEqualTo(200);
        String sesion = login.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith("JSESSIONID="))
                .map(c -> c.split(";", 2)[0]).findFirst().orElseThrow();
        try (var captura = new LogAdvice()) {
            var respuesta = enviar(HttpRequest.newBuilder(uri("/api/prueba-confidencialidad/privada"))
                    .header("Cookie", sesion).GET());
            assertThat(respuesta.statusCode()).isEqualTo(500);
            assertThat(captura.eventos()).hasSize(1);
            var excepcion = captura.eventos().getFirst().getThrowableProxy();
            assertThat(excepcion).isNotNull();
            assertThat(excepcion.getMessage()).isEqualTo(TOKEN_SINTETICO);
            assertThat(excepcion.getCause().getMessage()).isEqualTo(EMAIL_SINTETICO);
            assertThat(excepcion.getStackTraceElementProxyArray()).isNotEmpty();
        }
    }

    private void errorSinExponer(RuntimeException fallo, String... clases) throws Exception {
        doThrow(fallo).when(turnos).findByTokenHash(HASH_SINTETICO);
        try (var captura = new LogAdvice()) {
            var respuesta = get(URI_SINTETICA);
            assertThat(respuesta.statusCode()).isEqualTo(500);
            assertThat(respuesta.body()).isEqualTo("""
                    {"codigo":"ERROR_INTERNO","mensaje":"Ocurrió un error inesperado. Probá de nuevo en unos minutos."}""");
            sinSesionNiCredenciales(respuesta);
            assertThat(captura.eventos()).hasSize(1);
            var evento = captura.eventos().getFirst();
            assertThat(evento.getThrowableProxy()).as("el advice no registra throwable ni stacktrace en C-09").isNull();
            assertThat(evento.getFormattedMessage()).isEqualTo(CATEGORIA + " causas=" + List.of(clases));
            assertThat(hayCentinelas(evento.getFormattedMessage())).isFalse();
            headersPrivados(respuesta);
        }
    }

    private static void headersPrivados(HttpResponse<String> respuesta) {
        assertThat(respuesta.headers().firstValue("Location")).isEmpty();
        assertThat(respuesta.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        assertThat(respuesta.headers().firstValue("Referrer-Policy")).contains("no-referrer");
    }

    private static List<String> sinSesionNiCredenciales(HttpResponse<String> respuesta) {
        var cookies = respuesta.headers().allValues("Set-Cookie");
        var nombres = cookies.stream().map(c -> c.split("=", 2)[0]).toList();
        assertThat(nombres).isSubsetOf("XSRF-TOKEN");
        assertThat(cookies.stream().anyMatch(ConfidencialidadTurnoTokenIT::hayCentinelas)).isFalse();
        assertThat(respuesta.headers().firstValue("X-Test-Sesion")).contains("false");
        return nombres;
    }

    private static boolean hayCentinelas(String texto) {
        return List.of(TOKEN_SINTETICO, HASH_SINTETICO, EMAIL_SINTETICO, URI_SINTETICA, MENSAJE_INTERNO, API)
                .stream().anyMatch(texto::contains);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return enviar(HttpRequest.newBuilder(uri(path)).GET());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + puerto + path);
    }

    private HttpResponse<String> enviar(HttpRequest.Builder solicitud) throws Exception {
        try (var cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            return cliente.send(solicitud.timeout(Duration.ofSeconds(10)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private static final class LogAdvice implements AutoCloseable {
        private final Logger logger = (Logger) LoggerFactory.getLogger(ManejadorErrores.class);
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        LogAdvice() {
            appender.start();
            logger.addAppender(appender);
        }
        List<ILoggingEvent> eventos() {
            return List.copyOf(appender.list);
        }
        @Override
        public void close() {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Instrumentacion {
        @Bean
        FilterRegistrationBean<jakarta.servlet.Filter> observarSesion() {
            return new FilterRegistrationBean<>((req, res, chain) -> {
                var request = (HttpServletRequest) req;
                var respuesta = (jakarta.servlet.http.HttpServletResponse) res;
                // El interceptor del contenedor de pruebas observa la sesión antes de enviar el cuerpo.
                chain.doFilter(req, new jakarta.servlet.http.HttpServletResponseWrapper(respuesta) {
                    private void observar() {
                        setHeader("X-Test-Sesion", Boolean.toString(request.getSession(false) != null));
                    }
                    @Override
                    public java.io.PrintWriter getWriter() throws java.io.IOException {
                        observar();
                        return super.getWriter();
                    }
                    @Override
                    public jakarta.servlet.ServletOutputStream getOutputStream() throws java.io.IOException {
                        observar();
                        return super.getOutputStream();
                    }
                });
            });
        }

        @Bean
        PrivadoDePrueba privadoDePrueba() {
            return new PrivadoDePrueba();
        }
    }

    @RestController
    static class PrivadoDePrueba {
        @GetMapping("/api/prueba-confidencialidad/privada")
        String fallar() {
            throw new IllegalArgumentException(TOKEN_SINTETICO, new IllegalStateException(EMAIL_SINTETICO));
        }
    }
}
