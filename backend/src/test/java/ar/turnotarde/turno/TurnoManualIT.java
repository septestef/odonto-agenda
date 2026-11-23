package ar.turnotarde.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnotarde.odontologo.Odontologo;
import ar.turnotarde.odontologo.OdontologoRepository;
import ar.turnotarde.prestacion.Prestacion;
import ar.turnotarde.prestacion.PrestacionRepository;
import ar.turnotarde.shared.error.ReglaNegocioException;
import ar.turnotarde.soporte.ConfiguracionRelojDePrueba;
import ar.turnotarde.soporte.CuentaDePrueba;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import java.sql.Timestamp;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * C-15: alta manual autenticada contra PostgreSQL real. Reloj fijo en 2026-11-25T12:00:00Z (09:00 local),
 * con franjas el miércoles 25 y el viernes 27 de 09:00 a 13:00.
 */
@AutoConfigureMockMvc
@Import(ConfiguracionRelojDePrueba.class)
class TurnoManualIT extends IntegracionTest {

    private static final String CABECERA_CSRF = "X-XSRF-TOKEN";
    private static final String HOY_0915 = "2026-11-25T09:15:00-03:00";
    private static final String VIERNES_0900 = "2026-11-27T09:00:00-03:00";
    private static final String VIERNES_0900_UTC = "2026-11-27T12:00:00Z";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ReservaService reservas;
    @Autowired
    private OdontologoRepository odontologos;
    @Autowired
    private PrestacionRepository prestaciones;

    private FixturesJdbc fixtures;
    private CuentaDePrueba.Cuenta cuenta;
    private long consulta;

    @BeforeEach
    void preparar() throws Exception {
        LimpiezaBase.limpiar(jdbc);
        cuenta = CuentaDePrueba.crear(mvc, jdbc, "manual");
        fixtures = new FixturesJdbc(jdbc);
        consulta = fixtures.prestacion(cuenta.id(), "Consulta", 30);
        fixtures.horario(cuenta.id(), 3, "09:00", "13:00");
        fixtures.horario(cuenta.id(), 5, "09:00", "13:00");
        jdbc.update("UPDATE odontologo SET direccion_consultorio = 'Av. Colón 1234' WHERE id = ?", cuenta.id());
    }

    @AfterEach
    void limpiar() {
        LimpiezaBase.limpiar(jdbc);
    }

    private String cuerpo(long prestacionId, String inicio, String dni, String email) {
        return """
                {"prestacionId": %d, "inicio": "%s", "dni": "%s", "nombre": "Luis", "apellido": "Gómez",
                 "telefono": "+5493511234567", "email": "%s"}""".formatted(prestacionId, inicio, dni, email);
    }

    private ResultActions crear(String json) throws Exception {
        return mvc.perform(post("/api/turnos")
                .session(cuenta.sesion().sesion())
                .cookie(cuenta.sesion().csrfCookie())
                .header(CABECERA_CSRF, cuenta.sesion().csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions crear(String inicio, String dni, String email) throws Exception {
        return crear(cuerpo(consulta, inicio, dni, email));
    }

    private int contar(String tabla) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Integer.class);
    }

    private Instant instante(Object timestamp) {
        return ((Timestamp) timestamp).toInstant();
    }

    @Test
    void altaManualDeHoyResponde201ConOrigenManualYSinSecretos() throws Exception {
        String respuesta = crear(HOY_0915, "12345678", "luis@example.com")
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$", aMapWithSize(5)))
                .andExpect(jsonPath("$.inicio").value("2026-11-25T09:15:00-03:00"))
                .andExpect(jsonPath("$.fin").value("2026-11-25T09:45:00-03:00"))
                .andExpect(jsonPath("$.prestacion.id").value(consulta))
                .andExpect(jsonPath("$.prestacion.nombre").value("Consulta"))
                .andExpect(jsonPath("$.prestacion.duracionMinutos").value(30))
                .andExpect(jsonPath("$.odontologo", aMapWithSize(3)))
                .andExpect(jsonPath("$.odontologo.nombre").value("Nombre"))
                .andExpect(jsonPath("$.odontologo.direccionConsultorio").value("Av. Colón 1234"))
                .andExpect(jsonPath("$.email").value("luis@example.com"))
                .andReturn().getResponse().getContentAsString();

        var fila = jdbc.queryForMap("SELECT * FROM turno");
        assertThat(fila).containsEntry("estado", "RESERVADO").containsEntry("origen", "MANUAL")
                .containsEntry("odontologo_id", cuenta.id()).containsEntry("prestacion_id", consulta)
                .containsEntry("email_gestion", "luis@example.com");
        assertThat(instante(fila.get("inicio"))).isEqualTo(Instant.parse("2026-11-25T12:15:00Z"));
        assertThat(instante(fila.get("fin"))).isEqualTo(Instant.parse("2026-11-25T12:45:00Z"));
        assertThat(fila.get("token_hash").toString()).matches("[0-9a-f]{64}");
        assertThat(respuesta).doesNotContain(fila.get("token_hash").toString())
                .doesNotContainIgnoringCase("token").doesNotContain("email_gestion");
    }

    @Test
    void identificadoresAjenosEnCuerpoYHeaderSonIgnorados() throws Exception {
        CuentaDePrueba.Cuenta otra = CuentaDePrueba.crear(mvc, jdbc, "otra-manual");
        String json = """
                {"odontologoId": %d, "prestacionId": %d, "inicio": "%s", "dni": "12345678",
                 "nombre": "Luis", "apellido": "Gómez", "telefono": "+5493511234567",
                 "email": "luis@example.com"}""".formatted(otra.id(), consulta, HOY_0915);

        mvc.perform(post("/api/turnos")
                        .session(cuenta.sesion().sesion())
                        .cookie(cuenta.sesion().csrfCookie())
                        .header(CABECERA_CSRF, cuenta.sesion().csrf())
                        .header("X-Odontologo-Id", String.valueOf(otra.id()))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT odontologo_id FROM turno", Long.class)).isEqualTo(cuenta.id());
    }

    @Test
    void sinSesionDa401YSinCsrfDa403SinCrearNada() throws Exception {
        var csrf = cuenta.sesion().csrfCookie();
        String json = cuerpo(consulta, HOY_0915, "12345678", "luis@example.com");

        // Con CSRF válido pero sin sesión la cadena llega a autorización.
        mvc.perform(post("/api/turnos").cookie(csrf).header(CABECERA_CSRF, csrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mvc.perform(post("/api/turnos").session(cuenta.sesion().sesion())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));

        assertThat(contar("turno")).isZero();
        assertThat(contar("paciente")).isZero();
    }

    @Test
    void datosInvalidosDan400ConUnDetallePorCampoYNoCreanNada() throws Exception {
        crear("""
                {"prestacionId": %d, "inicio": "%s", "dni": "12.345.678", "nombre": "Luis",
                 "apellido": "Gómez", "telefono": "3511234567", "email": "luis@"}"""
                .formatted(consulta, HOY_0915))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles", hasSize(3)))
                .andExpect(jsonPath("$.detalles[*].campo", hasItem("dni")))
                .andExpect(jsonPath("$.detalles[*].campo", hasItem("telefono")))
                .andExpect(jsonPath("$.detalles[*].campo", hasItem("email")));

        crear("""
                {"inicio": "%s", "dni": "12345678", "nombre": "Luis", "apellido": "Gómez",
                 "telefono": "+5493511234567", "email": "luis@example.com"}""".formatted(HOY_0915))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles[*].campo", hasItem("prestacionId")));

        crear(cuerpo(consulta, "2026-11-25T09:15:00", "12345678", "luis@example.com"))
                .andExpect(status().isBadRequest());

        assertThat(contar("turno")).isZero();
        assertThat(contar("paciente")).isZero();
    }

    @Test
    void prestacionAjenaODadaDeBajaDa404Generico() throws Exception {
        CuentaDePrueba.Cuenta otra = CuentaDePrueba.crear(mvc, jdbc, "otra-prest");
        long ajena = fixtures.prestacion(otra.id(), "Ajena", 30);

        crear(cuerpo(ajena, HOY_0915, "12345678", "luis@example.com"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("No encontramos lo que buscabas."));

        jdbc.update("UPDATE prestacion SET activa = false WHERE id = ?", consulta);
        crear(HOY_0915, "12345678", "luis@example.com").andExpect(status().isNotFound());

        assertThat(contar("turno")).isZero();
        assertThat(contar("paciente")).isZero();
    }

    @ParameterizedTest
    @CsvSource({
            "2026-11-25T09:10:00-03:00, RN-AG-05",
            "2026-11-25T08:45:00-03:00, RN-TU-12",
            "2026-11-25T09:00:00-03:00, RN-TU-12",
            "2027-01-25T09:00:00-03:00, RN-TU-11"
    })
    void reglasDeAgendaPasadoYHorizonteUsanSuCodigo(String inicio, String codigo) throws Exception {
        crear(inicio, "12345678", "luis@example.com")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$", aMapWithSize(2)))
                .andExpect(jsonPath("$.codigo").value(codigo));

        assertThat(contar("turno")).isZero();
        assertThat(contar("paciente")).isZero();
    }

    @Test
    void unInicioQueCruzaUnBloqueoDa422RnAg05() throws Exception {
        fixtures.bloqueo(cuenta.id(), Instant.parse("2026-11-25T12:45:00Z"),
                Instant.parse("2026-11-25T13:00:00Z"));

        crear("2026-11-25T09:45:00-03:00", "12345678", "luis@example.com")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RN-AG-05"));

        assertThat(contar("turno")).isZero();
    }

    @Test
    void solapamientoConTurnoActivoDa409RnTu01() throws Exception {
        long paciente = fixtures.paciente(cuenta.id(), "11111111");
        fixtures.turno(cuenta.id(), paciente, consulta, Instant.parse("2026-11-25T12:00:00Z"),
                Instant.parse("2026-11-25T12:30:00Z"), "RESERVADO", OrigenTurno.MANUAL);

        crear(HOY_0915, "22222222", "luis@example.com")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$", aMapWithSize(2)))
                .andExpect(jsonPath("$.codigo").value("RN-TU-01"))
                .andExpect(jsonPath("$.mensaje").value("Ese horario acaba de ser tomado, elegí otro"));

        assertThat(contar("turno")).isEqualTo(1);
        assertThat(contar("paciente")).isEqualTo(1);
    }

    @RepeatedTest(3)
    void dosAltasManualesSimultaneasSoloConfirmanUna() throws Exception {
        List<Throwable> fallas = enParalelo(
                () -> reservas.reservarManual(odontologo(), prestacion(), Instant.parse("2026-11-25T12:15:00Z"),
                        new ReservaService.DatosPaciente("11111111", "Luis", "Gómez", "+5493511111111",
                                "luis@example.com")),
                () -> reservas.reservarManual(odontologo(), prestacion(), Instant.parse("2026-11-25T12:15:00Z"),
                        new ReservaService.DatosPaciente("22222222", "Ana", "Pérez", "+5493512222222",
                                "ana@example.com")));

        assertThat(fallas).as("exactamente una transacción falla").hasSize(1);
        assertThat(fallas.get(0)).isInstanceOfAny(ReglaNegocioException.class, DataIntegrityViolationException.class);
        assertThat(contar("turno")).isEqualTo(1);
    }

    @RepeatedTest(3)
    void altaManualYReservaOnlineSimultaneasSoloConfirmanUna() throws Exception {
        List<Throwable> fallas = enParalelo(
                () -> reservas.reservarManual(odontologo(), prestacion(), Instant.parse(VIERNES_0900_UTC),
                        new ReservaService.DatosPaciente("33333333", "Luis", "Gómez", "+5493513333333",
                                "luis@example.com")),
                () -> reservas.reservar(odontologo(), prestacion(), Instant.parse(VIERNES_0900_UTC),
                        new ReservaService.DatosPaciente("44444444", "Ana", "Pérez", "+5493514444444",
                                "ana@example.com")));

        assertThat(fallas).as("la constraint deja pasar una sola").hasSize(1);
        assertThat(fallas.get(0)).isInstanceOfAny(ReglaNegocioException.class, DataIntegrityViolationException.class);
        assertThat(contar("turno")).isEqualTo(1);
    }

    @Test
    void tercerTurnoManualNoAplicaLimitePeroElOnlineSiCuentaLosManuales() throws Exception {
        long paciente = fixtures.paciente(cuenta.id(), "99999999");
        fixtures.turno(cuenta.id(), paciente, consulta, Instant.parse("2026-11-27T12:00:00Z"),
                Instant.parse("2026-11-27T12:30:00Z"), "RESERVADO", OrigenTurno.MANUAL);
        fixtures.turno(cuenta.id(), paciente, consulta, Instant.parse("2026-11-27T13:00:00Z"),
                Instant.parse("2026-11-27T13:30:00Z"), "RESERVADO", OrigenTurno.MANUAL);

        crear("2026-11-27T11:00:00-03:00", "99999999", "actualizado@example.com")
                .andExpect(status().isCreated());

        mvc.perform(post("/api/public/{slug}/turnos", cuenta.slug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(consulta, "2026-11-27T12:00:00-03:00", "99999999",
                                "actualizado@example.com")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RN-PA-03"));

        assertThat(contar("turno")).isEqualTo(3);
        assertThat(contar("paciente")).isEqualTo(1);
    }

    @Test
    void upsertDePacienteSeRevierteEnElRechazoYActualizaEnElExito() throws Exception {
        long paciente = fixtures.paciente(cuenta.id(), "55555555");
        jdbc.update("UPDATE paciente SET email = 'viejo@example.com' WHERE id = ?", paciente);
        fixtures.turno(cuenta.id(), paciente, consulta, Instant.parse("2026-11-25T12:00:00Z"),
                Instant.parse("2026-11-25T12:30:00Z"), "RESERVADO", OrigenTurno.MANUAL);

        crear(HOY_0915, "55555555", "nuevo@example.com")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RN-TU-01"));
        assertThat(jdbc.queryForObject("SELECT email FROM paciente WHERE id = ?", String.class, paciente))
                .isEqualTo("viejo@example.com");
        assertThat(contar("notificacion")).isZero();

        crear("2026-11-25T11:00:00-03:00", "55555555", "nuevo@example.com")
                .andExpect(status().isCreated());

        assertThat(contar("paciente")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT email FROM paciente WHERE id = ?", String.class, paciente))
                .isEqualTo("nuevo@example.com");
        assertThat(jdbc.queryForObject("SELECT email_gestion FROM turno ORDER BY id DESC LIMIT 1", String.class))
                .isEqualTo("nuevo@example.com");
    }

    @Test
    void elTurnoManualNoSeVeComoDisponibleParaElPaciente() throws Exception {
        crear(HOY_0915, "12345678", "luis@example.com").andExpect(status().isCreated());

        mvc.perform(get("/api/public/{slug}/disponibilidad", cuenta.slug())
                        .param("prestacionId", String.valueOf(consulta)).param("fecha", "2026-11-25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horarios", hasSize(0)));

        mvc.perform(post("/api/public/{slug}/turnos", cuenta.slug())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(consulta, HOY_0915, "87654321", "ana@example.com")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RN-TU-03"));
    }

    private Odontologo odontologo() {
        return odontologos.findById(cuenta.id()).orElseThrow();
    }

    private Prestacion prestacion() {
        return prestaciones.findById(consulta).orElseThrow();
    }

    /** Devuelve las fallas de dos tareas largadas a la vez; null significa que confirmó. */
    private List<Throwable> enParalelo(Callable<?> primera, Callable<?> segunda) throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Throwable>> futuras = new ArrayList<>();
            for (Callable<?> tarea : List.of(primera, segunda)) {
                futuras.add(pool.submit(() -> intentar(largada, tarea)));
            }
            largada.countDown();
            List<Throwable> fallas = new ArrayList<>();
            for (Future<Throwable> futura : futuras) {
                Throwable falla = futura.get(30, TimeUnit.SECONDS);
                if (falla != null) {
                    fallas.add(falla);
                }
            }
            return fallas;
        } finally {
            pool.shutdownNow();
        }
    }

    private static Throwable intentar(CountDownLatch largada, Callable<?> tarea) {
        try {
            largada.await();
            tarea.call();
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return e;
        } catch (Throwable e) {
            return e;
        }
    }
}
