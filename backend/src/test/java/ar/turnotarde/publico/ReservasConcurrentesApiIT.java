package ar.turnotarde.publico;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnotarde.soporte.ConfiguracionRelojDePrueba;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import ar.turnotarde.soporte.RelojDePrueba;
import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/**
 * Test obligatorio de reglas duras (RN-TU-01, DD-03) por HTTP real: servidor Tomcat con hilos propios y cada
 * request en su propio hilo, sin MockMvc compartido. Reloj fijo en 2026-11-25T12:00:00Z (09:00 local).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ConfiguracionRelojDePrueba.class)
class ReservasConcurrentesApiIT extends IntegracionTest {

    private record Respuesta(int estado, String cuerpo) {
        String codigo() {
            return JsonPath.read(cuerpo, "$.codigo");
        }

        String mensaje() {
            return JsonPath.read(cuerpo, "$.mensaje");
        }
    }

    @LocalServerPort
    private int puerto;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RelojDePrueba reloj;

    private RestClient cliente;
    private FixturesJdbc fixtures;
    private long demo;
    private long consulta;

    @AfterEach
    void dejarLaBaseVacia() {
        LimpiezaBase.limpiar(jdbc);
    }

    @BeforeEach
    void preparar() {
        reloj.fijar(Instant.parse("2026-11-25T12:00:00Z"));
        LimpiezaBase.limpiar(jdbc);
        fixtures = new FixturesJdbc(jdbc);
        demo = fixtures.odontologo("demo");
        consulta = fixtures.prestacion(demo, "Consulta", 30);
        fixtures.horario(demo, 5, "09:00", "13:00");
        cliente = RestClient.builder().baseUrl("http://localhost:" + puerto)
                .requestFactory(new JdkClientHttpRequestFactory(
                        HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()))
                .build();
        // Calienta el servidor para que las dos requests de la carrera lleguen en condiciones parejas.
        cliente.get().uri("/api/public/demo").retrieve().toBodilessEntity();
    }

    private Respuesta reservar(String inicio, String dni) {
        String json = """
                {"prestacionId": %d, "inicio": "%s", "dni": "%s", "nombre": "Luis", "apellido": "Gómez",
                 "telefono": "+5493511234567", "email": "luis%s@example.com"}""".formatted(consulta, inicio, dni, dni);
        return cliente.post().uri("/api/public/demo/turnos").contentType(MediaType.APPLICATION_JSON).body(json)
                .exchange((peticion, respuesta) -> new Respuesta(respuesta.getStatusCode().value(),
                        new String(respuesta.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)));
    }

    /** Larga las dos reservas a la vez y devuelve las dos respuestas. */
    private List<Respuesta> enParalelo(Callable<Respuesta> primera, Callable<Respuesta> segunda) throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Respuesta>> futuras = new ArrayList<>();
            for (Callable<Respuesta> tarea : List.of(primera, segunda)) {
                futuras.add(pool.submit(() -> {
                    largada.await();
                    return tarea.call();
                }));
            }
            largada.countDown();
            List<Respuesta> respuestas = new ArrayList<>();
            for (Future<Respuesta> futura : futuras) {
                respuestas.add(futura.get(30, TimeUnit.SECONDS));
            }
            return respuestas;
        } finally {
            pool.shutdownNow();
        }
    }

    @RepeatedTest(5)
    void dosPacientesQueReservanElMismoHorarioALaVezSoloConfirmaUno() throws Exception {
        List<Respuesta> respuestas = enParalelo(
                () -> reservar("2026-11-27T09:00:00-03:00", "11111111"),
                () -> reservar("2026-11-27T09:00:00-03:00", "22222222"));

        assertThat(respuestas).extracting(Respuesta::estado).containsExactlyInAnyOrder(201, 409);
        Respuesta perdedora = respuestas.stream().filter(r -> r.estado() == 409).findFirst().orElseThrow();
        assertThat(perdedora.codigo()).isEqualTo("RN-TU-01");
        assertThat(perdedora.mensaje()).isEqualTo("Ese horario acaba de ser tomado, elegí otro");
        assertThat((Object) JsonPath.read(perdedora.cuerpo(), "$.length()")).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM turno WHERE estado = 'RESERVADO' AND odontologo_id = ?", Integer.class, demo))
                .isEqualTo(1);
    }

    @RepeatedTest(5)
    void dosReservasDelMismoDniConUnTurnoFuturoEnElLimiteSoloConfirmaUna() throws Exception {
        long paciente = fixtures.paciente(demo, "12345678");
        fixtures.turno(demo, paciente, consulta, Instant.parse("2026-12-01T12:00:00Z"),
                Instant.parse("2026-12-01T12:30:00Z"), "RESERVADO");

        List<Respuesta> respuestas = enParalelo(
                () -> reservar("2026-11-27T09:00:00-03:00", "12345678"),
                () -> reservar("2026-11-27T10:00:00-03:00", "12345678"));

        assertThat(respuestas).extracting(Respuesta::estado).containsExactlyInAnyOrder(201, 409);
        Respuesta perdedora = respuestas.stream().filter(r -> r.estado() == 409).findFirst().orElseThrow();
        assertThat(perdedora.codigo()).isEqualTo("RN-PA-03");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM turno WHERE paciente_id = ? AND estado = 'RESERVADO' AND inicio > ?",
                Integer.class, paciente, java.sql.Timestamp.from(Instant.parse("2026-11-25T12:00:00Z"))))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM paciente WHERE dni = '12345678'", Integer.class))
                .isEqualTo(1);
    }
}
