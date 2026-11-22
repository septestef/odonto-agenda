package ar.turnotarde.publico;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnotarde.soporte.ConfiguracionRelojDePrueba;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import ar.turnotarde.soporte.RelojDePrueba;
import ar.turnotarde.turno.TokenTurno;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
@Import(ConfiguracionRelojDePrueba.class)
class ConsultaTurnoTokenIT extends IntegracionTest {
    private static final String TOKEN = "-_" + "A".repeat(41);
    private static final String TOKEN_OTRO = "B".repeat(42) + "A";
    private static final Instant AHORA = Instant.parse("2026-11-25T12:00:00Z");
    private static final Instant INICIO = Instant.parse("2026-11-27T12:00:00Z");
    private static final String EMAIL = "consulta@example.com";
    private static final String NO_ENCONTRADO = """
            {"codigo":"NO_ENCONTRADO","mensaje":"No encontramos lo que buscabas."}""";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RelojDePrueba reloj;
    private FixturesJdbc fixtures;
    private long odontologo;
    private long prestacion;

    @BeforeEach
    void preparar() {
        LimpiezaBase.limpiar(jdbc);
        reloj.fijar(AHORA);
        fixtures = new FixturesJdbc(jdbc);
        odontologo = fixtures.odontologo("turnos");
        prestacion = fixtures.prestacion(odontologo, "Consulta", 30);
        fixtures.horario(odontologo, 5, "09:00", "13:00");
    }

    @AfterEach
    void limpiar() {
        reloj.fijar(AHORA);
        LimpiezaBase.limpiar(jdbc);
    }

    @Test
    void resuelveSoloPorHashYDevuelveElDetalleExactoDelConsultorioDeCadaTurno() throws Exception {
        turno(odontologo, prestacion, INICIO, "RESERVADO", TOKEN, EMAIL);
        jdbc.update("UPDATE prestacion SET duracion_minutos = 60 WHERE id = ?", prestacion);
        long otro = fixtures.odontologo("otro");
        long otraPrestacion = fixtures.prestacion(otro, "Limpieza", 45);
        turno(otro, otraPrestacion, INICIO, "RESERVADO", TOKEN_OTRO, EMAIL);

        consultar(TOKEN).andExpect(status().isOk())
                .andExpect(content().json("""
                        {"inicio":"2026-11-27T09:00:00-03:00","fin":"2026-11-27T09:30:00-03:00",
                         "estado":"RESERVADO","prestacion":{"id":%d,"nombre":"Consulta"},
                         "odontologo":{"slug":"turnos","nombre":"Nombre","apellido":"Apellido",
                                       "direccionConsultorio":null},
                         "puedeCancelar":true,"puedeReprogramar":true}""".formatted(prestacion), JsonCompareMode.STRICT));
        consultar(TOKEN_OTRO).andExpect(status().isOk())
                .andExpect(jsonPath("$.odontologo.slug").value("otro"))
                .andExpect(jsonPath("$.prestacion.id").value(otraPrestacion));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELADO", "ATENDIDO", "AUSENTE"})
    void futuroNoReservadoSeLeeSinAcciones(String estado) throws Exception {
        turno(odontologo, prestacion, INICIO, estado, TOKEN, EMAIL);
        flags(false, false).andExpect(jsonPath("$.estado").value(estado));
    }

    @Test
    void alInicioExactoYEnElPasadoSoloSeLee() throws Exception {
        turno(odontologo, prestacion, INICIO, "RESERVADO", TOKEN, EMAIL);
        reloj.fijar(INICIO);
        flags(false, false);
        reloj.fijar(INICIO.plusSeconds(1));
        flags(false, false);
    }

    @Test
    void legadoBuzonInadmisibleOConsultorioInactivoPermitenSoloCancelar() throws Exception {
        long turno = turno(odontologo, prestacion, INICIO, "RESERVADO", TOKEN, null);
        flags(true, false);
        jdbc.update("UPDATE turno SET email_gestion = 'ana@ejemplo_com.test' WHERE id = ?", turno);
        flags(true, false);
        jdbc.update("UPDATE turno SET email_gestion = ? WHERE id = ?", EMAIL, turno);
        jdbc.update("UPDATE prestacion SET activa = false WHERE id = ?", prestacion);
        flags(true, false);
        jdbc.update("UPDATE prestacion SET activa = true WHERE id = ?", prestacion);
        jdbc.update("UPDATE odontologo SET activo = false WHERE id = ?", odontologo);
        flags(true, false);
    }

    @Test
    void tokenMalFormadoDesconocidoYHashComoCredencialTienen404Uniforme() throws Exception {
        turno(odontologo, prestacion, INICIO, "RESERVADO", TOKEN, EMAIL);
        for (String segmento : new String[] {"A".repeat(42), "A".repeat(44), "." + "A".repeat(42),
                TokenTurno.hashear(TOKEN), TOKEN_OTRO}) {
            consultar(segmento).andExpect(status().isNotFound())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(content().json(NO_ENCONTRADO, JsonCompareMode.STRICT));
        }
    }

    @Test
    void elMatcherNoInterfiereConPerfilDisponibilidadYReservaDelSlugTurnos() throws Exception {
        mvc.perform(get("/api/public/turnos")).andExpect(status().isOk());
        mvc.perform(get("/api/public/turnos/disponibilidad")
                        .param("prestacionId", String.valueOf(prestacion)).param("fecha", "2026-11-27"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/public/turnos/disponibilidad")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
        mvc.perform(post("/api/public/turnos/turnos").contentType(MediaType.APPLICATION_JSON).content("""
                {"prestacionId":%d,"inicio":"2026-11-27T09:00:00-03:00","dni":"12345678",
                 "nombre":"Ana","apellido":"Pérez","telefono":"+5493511234567",
                 "email":"consulta@example.com"}""".formatted(prestacion))).andExpect(status().isCreated());
        consultar("turnos").andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));
    }

    private ResultActions flags(boolean cancelar, boolean reprogramar) throws Exception {
        return consultar(TOKEN).andExpect(status().isOk())
                .andExpect(jsonPath("$.puedeCancelar").value(cancelar))
                .andExpect(jsonPath("$.puedeReprogramar").value(reprogramar));
    }

    private ResultActions consultar(String token) throws Exception {
        return mvc.perform(get("/api/public/turnos/{token}", token));
    }

    private long turno(long od, long pr, Instant inicio, String estado, String token, String correo) {
        long paciente = fixtures.paciente(od, "12345678");
        long id = fixtures.turno(od, paciente, pr, inicio, inicio.plusSeconds(1800), estado, TokenTurno.hashear(token));
        jdbc.update("UPDATE turno SET email_gestion = ? WHERE id = ?", correo, id);
        return id;
    }
}
