package ar.turnotarde.publico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnotarde.soporte.ConfiguracionRelojDePrueba;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import ar.turnotarde.soporte.RelojDePrueba;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Escenarios de {@code specs/reserva-online}. Reloj fijo en 2026-11-25T12:00:00Z (09:00 local); {@code demo} atiende
 * jueves y viernes de 09:00 a 13:00 y los lunes de 09:00 a 13:00, con "Consulta" de 30 minutos.
 */
@AutoConfigureMockMvc
@Import(ConfiguracionRelojDePrueba.class)
class ReservaOnlineIT extends IntegracionTest {

    private static final Instant AHORA = Instant.parse("2026-11-25T12:00:00Z");
    private static final String VIERNES_0900 = "2026-11-27T09:00:00-03:00";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RelojDePrueba reloj;

    private FixturesJdbc fixtures;
    private long demo;
    private long consulta;

    @AfterEach
    void dejarLaBaseVacia() {
        LimpiezaBase.limpiar(jdbc);
    }

    @BeforeEach
    void preparar() {
        reloj.fijar(AHORA);
        LimpiezaBase.limpiar(jdbc);
        fixtures = new FixturesJdbc(jdbc);
        demo = fixtures.odontologo("demo");
        jdbc.update("UPDATE odontologo SET direccion_consultorio = 'Av. Colón 1234, Córdoba' WHERE id = ?", demo);
        consulta = fixtures.prestacion(demo, "Consulta", 30);
        fixtures.horario(demo, 1, "09:00", "13:00");
        fixtures.horario(demo, 4, "09:00", "13:00");
        fixtures.horario(demo, 5, "09:00", "13:00");
    }

    private String cuerpo(long prestacionId, String inicio, String dni, String nombre, String email) {
        return """
                {"prestacionId": %d, "inicio": "%s", "dni": "%s", "nombre": "%s", "apellido": "Gómez",
                 "telefono": "+5493511234567", "email": "%s"}""".formatted(prestacionId, inicio, dni, nombre, email);
    }

    private ResultActions reservar(String slug, String json) throws Exception {
        return mvc.perform(post("/api/public/{slug}/turnos", slug).contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions reservar(String inicio, String dni, String email) throws Exception {
        return reservar("demo", cuerpo(consulta, inicio, dni, "Luis", email));
    }

    private int contar(String tabla) {
        return jdbc.queryForObject("SELECT count(*) FROM " + tabla, Integer.class);
    }

    private Instant instante(Object timestamp) {
        return ((Timestamp) timestamp).toInstant();
    }

    // ---- Reserva exitosa

    @Test
    void reservaDeUnHorarioLibreResponde201ConElContratoDeD3SinTokenNiId() throws Exception {
        String respuesta = reservar(VIERNES_0900, "12345678", "luis@example.com")
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$", aMapWithSize(5)))
                .andExpect(jsonPath("$.inicio").value("2026-11-27T09:00:00-03:00"))
                .andExpect(jsonPath("$.fin").value("2026-11-27T09:30:00-03:00"))
                .andExpect(jsonPath("$.prestacion.id").value(consulta))
                .andExpect(jsonPath("$.prestacion.nombre").value("Consulta"))
                .andExpect(jsonPath("$.prestacion.duracionMinutos").value(30))
                .andExpect(jsonPath("$.odontologo", aMapWithSize(3)))
                .andExpect(jsonPath("$.odontologo.nombre").value("Nombre"))
                .andExpect(jsonPath("$.odontologo.apellido").value("Apellido"))
                .andExpect(jsonPath("$.odontologo.direccionConsultorio").value("Av. Colón 1234, Córdoba"))
                .andExpect(jsonPath("$.email").value("luis@example.com"))
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> turno = jdbc.queryForMap("SELECT * FROM turno");
        assertThat(turno).containsEntry("estado", "RESERVADO").containsEntry("origen", "ONLINE")
                .containsEntry("odontologo_id", demo).containsEntry("prestacion_id", consulta);
        assertThat(instante(turno.get("inicio"))).isEqualTo(Instant.parse("2026-11-27T12:00:00Z"));
        assertThat(instante(turno.get("fin"))).isEqualTo(Instant.parse("2026-11-27T12:30:00Z"));
        assertThat(turno.get("token_hash").toString()).matches("[0-9a-f]{64}");
        assertThat(respuesta).doesNotContain(turno.get("token_hash").toString()).doesNotContainIgnoringCase("token");
    }

    @Test
    void dosReservasEnHorariosDistintosGuardanTokenHashDistintos() throws Exception {
        reservar(VIERNES_0900, "12345678", "luis@example.com").andExpect(status().isCreated());
        reservar("2026-11-27T10:00:00-03:00", "87654321", "ana@example.com").andExpect(status().isCreated());

        List<String> hashes = jdbc.queryForList("SELECT token_hash FROM turno", String.class);
        assertThat(hashes).hasSize(2).doesNotHaveDuplicates().allMatch(h -> h.matches("[0-9a-f]{64}"));
    }

    @Test
    void elInicioEnUtcEquivaleAlMismoInstanteQueConOffsetLocal() throws Exception {
        reservar("2026-11-27T12:00:00Z", "12345678", "luis@example.com")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inicio").value("2026-11-27T09:00:00-03:00"));
    }

    // ---- Validación

    @Test
    void datosInvalidosDan400ConUnDetallePorCampoYNoCreanNada() throws Exception {
        reservar("demo", cuerpo(consulta, VIERNES_0900, "12.345.678", "", "luis@"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles", hasSize(3)))
                .andExpect(jsonPath("$.detalles[*].campo", hasItem("dni")))
                .andExpect(jsonPath("$.detalles[*].campo", hasItem("nombre")))
                .andExpect(jsonPath("$.detalles[*].campo", hasItem("email")));

        assertThat(contar("paciente")).isZero();
        assertThat(contar("turno")).isZero();
    }

    @Test
    void telefonoQueNoEsE164Da400ConElCampoTelefono() throws Exception {
        String json = cuerpo(consulta, VIERNES_0900, "12345678", "Luis", "luis@example.com")
                .replace("+5493511234567", "3511234567");

        reservar("demo", json).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles[*].campo", hasItem("telefono")));
    }

    @Test
    void inicioSinOffsetDa400YNoCreaTurno() throws Exception {
        reservar("demo", cuerpo(consulta, "2026-11-27T09:00:00", "12345678", "Luis", "luis@example.com"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));

        assertThat(contar("turno")).isZero();
        assertThat(contar("paciente")).isZero();
    }

    // ---- Reglas de agenda

    @Test
    void conMenosDe24HorasDeAnticipacionDa422RnTu03() throws Exception {
        reloj.fijar(Instant.parse("2026-11-25T15:00:00Z"));

        reservar("2026-11-26T11:45:00-03:00", "12345678", "luis@example.com")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RN-TU-03"));

        assertThat(contar("turno")).isZero();
        assertThat(contar("paciente")).isZero();
    }

    @Test
    void fueraDelHorizonteDa422RnTu11() throws Exception {
        reservar("2027-01-25T09:00:00-03:00", "12345678", "luis@example.com")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RN-TU-11"));
    }

    @Test
    void fueraDeFranjaOFueraDePasoDa422RnAg05() throws Exception {
        reservar("2026-11-27T12:45:00-03:00", "12345678", "luis@example.com")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RN-AG-05"));
        reservar("2026-11-27T09:10:00-03:00", "12345678", "luis@example.com")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RN-AG-05"));

        assertThat(contar("turno")).isZero();
    }

    @Test
    void unBloqueoQueCruzaElHorarioDa422RnAg05() throws Exception {
        fixtures.bloqueo(demo, Instant.parse("2026-11-27T12:15:00Z"), Instant.parse("2026-11-27T12:45:00Z"));

        reservar(VIERNES_0900, "12345678", "luis@example.com")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RN-AG-05"));
    }

    @Test
    void horarioOcupadoDa409RnTu01ConCuerpoSoloDeCodigoYMensajeSinDatosAjenos() throws Exception {
        long otroPaciente = fixtures.paciente(demo, "99999999");
        fixtures.turno(demo, otroPaciente, consulta, Instant.parse("2026-11-27T12:00:00Z"),
                Instant.parse("2026-11-27T12:30:00Z"), "RESERVADO");

        String cuerpo = reservar(VIERNES_0900, "12345678", "luis@example.com")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$", aMapWithSize(2)))
                .andExpect(jsonPath("$.codigo").value("RN-TU-01"))
                .andExpect(jsonPath("$.mensaje").value("Ese horario acaba de ser tomado, elegí otro"))
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).doesNotContain("99999999").doesNotContain("Paciente").doesNotContain("p99999999");
        assertThat(contar("turno")).isEqualTo(1);
    }

    // ---- Recursos inexistentes

    @Test
    void prestacionDeBajaDa404YNoCreaPacienteNiTurno() throws Exception {
        long deBaja = fixtures.prestacion(demo, "Vieja", 30);
        jdbc.update("UPDATE prestacion SET activa = false WHERE id = ?", deBaja);

        reservar("demo", cuerpo(deBaja, VIERNES_0900, "12345678", "Luis", "luis@example.com"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));

        assertThat(contar("paciente")).isZero();
        assertThat(contar("turno")).isZero();
    }

    @Test
    void slugInexistenteOInactivoYPrestacionAjenaDanElMismo404() throws Exception {
        long otro = fixtures.odontologo("otro");
        long ajena = fixtures.prestacion(otro, "Ajena", 30);
        long inactivo = fixtures.odontologo("inactivo");
        long prestacionInactivo = fixtures.prestacion(inactivo, "Consulta", 30);
        jdbc.update("UPDATE odontologo SET activo = false WHERE id = ?", inactivo);

        String esperado = reservar("no-existe", cuerpo(consulta, VIERNES_0900, "12345678", "Luis", "l@example.com"))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        assertThat(reservar("demo", cuerpo(ajena, VIERNES_0900, "12345678", "Luis", "l@example.com"))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString())
                .isEqualTo(esperado);
        assertThat(reservar("inactivo", cuerpo(prestacionInactivo, VIERNES_0900, "12345678", "Luis", "l@example.com"))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString())
                .isEqualTo(esperado);
        assertThat(contar("paciente")).isZero();
    }

    // ---- Paciente por DNI

    @Test
    void reservasDelMismoDniConservanSuPropioDestinatarioDeGestion() throws Exception {
        String primero = "Luis.Primero@example.com";
        String segundo = "luis.segundo@example.com";
        reservar(VIERNES_0900, "12345678", primero).andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(primero));
        reservar("2026-11-27T10:00:00-03:00", "12345678", segundo).andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(segundo));

        assertThat(jdbc.queryForObject("SELECT email FROM paciente", String.class)).isEqualTo(segundo);
        assertThat(jdbc.queryForList("SELECT email_gestion FROM turno ORDER BY inicio", String.class))
                .containsExactly(primero, segundo);

        reservar(VIERNES_0900, "12345678", "rechazado@example.com").andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RN-TU-01"));

        assertThat(jdbc.queryForObject("SELECT email FROM paciente", String.class)).isEqualTo(segundo);
        assertThat(jdbc.queryForList("SELECT email_gestion FROM turno ORDER BY inicio", String.class))
                .containsExactly(primero, segundo);
    }

    @Test
    void dniNuevoCreaElPacienteDelOdontologoYElTurnoQuedaAsociado() throws Exception {
        reservar(VIERNES_0900, "12345678", "luis@example.com").andExpect(status().isCreated());

        Map<String, Object> paciente = jdbc.queryForMap("SELECT * FROM paciente");
        assertThat(paciente).containsEntry("odontologo_id", demo).containsEntry("dni", "12345678")
                .containsEntry("nombre", "Luis").containsEntry("apellido", "Gómez")
                .containsEntry("telefono", "+5493511234567").containsEntry("email", "luis@example.com");
        assertThat(jdbc.queryForObject("SELECT paciente_id FROM turno", Long.class)).isEqualTo(paciente.get("id"));
    }

    @Test
    void dniRepetidoActualizaElContactoYElTurnoNuevoQuedaConElMismoPaciente() throws Exception {
        long existente = fixtures.paciente(demo, "12345678");
        jdbc.update("UPDATE paciente SET email = 'viejo@example.com' WHERE id = ?", existente);

        reservar(VIERNES_0900, "12345678", "nuevo@example.com").andExpect(status().isCreated());

        assertThat(contar("paciente")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT email FROM paciente WHERE id = ?", String.class, existente))
                .isEqualTo("nuevo@example.com");
        assertThat(jdbc.queryForObject("SELECT paciente_id FROM turno", Long.class)).isEqualTo(existente);
    }

    @Test
    void elMismoDniConOtroOdontologoCreaOtroPacienteYElDeOtroNoCambia() throws Exception {
        long otro = fixtures.odontologo("otro");
        long deOtro = fixtures.paciente(otro, "12345678");

        reservar(VIERNES_0900, "12345678", "luis@example.com").andExpect(status().isCreated());

        assertThat(contar("paciente")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT email FROM paciente WHERE id = ?", String.class, deOtro))
                .isEqualTo("p12345678@example.com");
        assertThat(jdbc.queryForObject("SELECT paciente_id FROM turno", Long.class)).isNotEqualTo(deOtro);
    }

    @Test
    void unaReservaRechazadaPorHorarioOcupadoNoTocaElContactoDelPaciente() throws Exception {
        long existente = fixtures.paciente(demo, "12345678");
        jdbc.update("UPDATE paciente SET email = 'viejo@example.com' WHERE id = ?", existente);
        long otroPaciente = fixtures.paciente(demo, "99999999");
        fixtures.turno(demo, otroPaciente, consulta, Instant.parse("2026-11-27T12:00:00Z"),
                Instant.parse("2026-11-27T12:30:00Z"), "RESERVADO");

        reservar(VIERNES_0900, "12345678", "nuevo@example.com").andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RN-TU-01"));

        assertThat(jdbc.queryForObject("SELECT email FROM paciente WHERE id = ?", String.class, existente))
                .isEqualTo("viejo@example.com");
    }

    // ---- Límite de dos turnos futuros por DNI

    @Test
    void elTercerTurnoFuturoDa409RnPa03SinCrearloYSinTocarElContacto() throws Exception {
        long paciente = fixtures.paciente(demo, "12345678");
        jdbc.update("UPDATE paciente SET email = 'viejo@example.com' WHERE id = ?", paciente);
        fixtures.turno(demo, paciente, consulta, Instant.parse("2026-12-01T12:00:00Z"),
                Instant.parse("2026-12-01T12:30:00Z"), "RESERVADO");
        fixtures.turno(demo, paciente, consulta, Instant.parse("2026-12-01T13:00:00Z"),
                Instant.parse("2026-12-01T13:30:00Z"), "RESERVADO");

        reservar(VIERNES_0900, "12345678", "nuevo@example.com").andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RN-PA-03"))
                .andExpect(jsonPath("$.mensaje").value(
                        "Ya tenés 2 turnos reservados en este consultorio. Cancelá uno desde el link del email para"
                                + " sacar otro."));

        assertThat(contar("turno")).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT email FROM paciente WHERE id = ?", String.class, paciente))
                .isEqualTo("viejo@example.com");
    }

    @Test
    void canceladosPasadosYTurnosConOtroOdontologoNoCuentanParaElLimite() throws Exception {
        long paciente = fixtures.paciente(demo, "12345678");
        fixtures.turno(demo, paciente, consulta, Instant.parse("2026-12-01T12:00:00Z"),
                Instant.parse("2026-12-01T12:30:00Z"), "CANCELADO");
        fixtures.turno(demo, paciente, consulta, Instant.parse("2026-12-01T13:00:00Z"),
                Instant.parse("2026-12-01T13:30:00Z"), "CANCELADO");
        fixtures.turno(demo, paciente, consulta, Instant.parse("2026-11-20T12:00:00Z"),
                Instant.parse("2026-11-20T12:30:00Z"), "RESERVADO");
        long otro = fixtures.odontologo("otro");
        long pacienteOtro = fixtures.paciente(otro, "12345678");
        long prestacionOtro = fixtures.prestacion(otro, "Consulta", 30);
        fixtures.turno(otro, pacienteOtro, prestacionOtro, Instant.parse("2026-12-02T12:00:00Z"),
                Instant.parse("2026-12-02T12:30:00Z"), "RESERVADO");
        fixtures.turno(otro, pacienteOtro, prestacionOtro, Instant.parse("2026-12-02T13:00:00Z"),
                Instant.parse("2026-12-02T13:30:00Z"), "RESERVADO");

        reservar(VIERNES_0900, "12345678", "luis@example.com").andExpect(status().isCreated());
    }

    @Test
    void conUnSoloTurnoFuturoSePuedeReservarElSegundo() throws Exception {
        long paciente = fixtures.paciente(demo, "12345678");
        fixtures.turno(demo, paciente, consulta, Instant.parse("2026-12-01T12:00:00Z"),
                Instant.parse("2026-12-01T12:30:00Z"), "RESERVADO");

        reservar(VIERNES_0900, "12345678", "luis@example.com").andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno WHERE paciente_id = ?", Integer.class, paciente))
                .isEqualTo(2);
    }
}
