package ar.turnotarde.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnotarde.soporte.CuentaDePrueba;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** RN-SE-01 en los tres recursos nuevos: ajeno e inexistente son el mismo 404. */
@AutoConfigureMockMvc
class AislamientoConfiguracionIT extends IntegracionTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    private CuentaDePrueba.Cuenta cuenta;
    private CuentaDePrueba.Cuenta otra;

    @BeforeEach
    void preparar() throws Exception {
        LimpiezaBase.limpiar(jdbc);
        cuenta = CuentaDePrueba.crear(mvc, jdbc);
        otra = CuentaDePrueba.crear(mvc, jdbc, "otra");
    }

    @AfterEach
    void dejarLaBaseVacia() {
        LimpiezaBase.limpiar(jdbc);
    }

    private ResultActions escribir(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion)
            throws Exception {
        return mvc.perform(peticion.session(cuenta.sesion().sesion())
                .cookie(cuenta.sesion().csrfCookie())
                .header("X-XSRF-TOKEN", cuenta.sesion().csrf())
                .header("odontologoId", String.valueOf(otra.id()))
                .contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void unaPrestacionAjenaDaElMismo404QueUnaInexistente() throws Exception {
        FixturesJdbc fixtures = new FixturesJdbc(jdbc);
        long ajena = fixtures.prestacion(otra.id(), "Ajena", 30);
        String json = "{\"nombre\": \"Robada\", \"duracionMinutos\": 30}";

        String cuerpoAjena = escribir(put("/api/prestaciones/{id}", ajena).content(json))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String cuerpoInexistente = escribir(put("/api/prestaciones/{id}", 987654321L).content(json))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        assertThat(cuerpoAjena).isEqualTo(cuerpoInexistente);
        assertThat(jdbc.queryForObject("SELECT nombre FROM prestacion WHERE id = ?", String.class, ajena))
                .isEqualTo("Ajena");
    }

    @Test
    void unBloqueoAjenoDaElMismo404QueUnoInexistente() throws Exception {
        long ajeno = new FixturesJdbc(jdbc).bloqueo(otra.id(),
                Instant.parse("2026-11-27T13:00:00Z"), Instant.parse("2026-11-27T14:00:00Z"));

        String cuerpoAjeno = escribir(delete("/api/bloqueos/{id}", ajeno))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        String cuerpoInexistente = escribir(delete("/api/bloqueos/{id}", 987654321L))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();

        assertThat(cuerpoAjeno).isEqualTo(cuerpoInexistente);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bloqueo", Integer.class)).isEqualTo(1);
    }

    @Test
    void unOdontologoIdEnCuerpoQueryOHeaderNoCambiaElDueno() throws Exception {
        String jsonPrestacion = """
                {"odontologoId": %d, "nombre": "Propia", "duracionMinutos": 30}
                """.formatted(otra.id());

        escribir(post("/api/prestaciones").content(jsonPrestacion))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Propia"));
        escribir(post("/api/bloqueos")
                        .content("""
                                {"odontologoId": %d, "inicio": "2026-11-27T10:00:00-03:00",
                                 "fin": "2026-11-27T11:00:00-03:00"}
                                """.formatted(otra.id())))
                .andExpect(status().isCreated());
        escribir(put("/api/horarios")
                        .content("""
                                {"odontologoId": %d, "franjas": [{"diaSemana": 1, "inicio": "09:00", "fin": "13:00"}]}
                                """.formatted(otra.id())))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM prestacion WHERE odontologo_id = ?",
                Integer.class, cuenta.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM prestacion WHERE odontologo_id = ?",
                Integer.class, otra.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bloqueo WHERE odontologo_id = ?",
                Integer.class, otra.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM horario_atencion WHERE odontologo_id = ?",
                Integer.class, otra.id())).isZero();
    }

    @Test
    void ningunListadoIncluyeRecursosAjenos() throws Exception {
        FixturesJdbc fixtures = new FixturesJdbc(jdbc);
        fixtures.prestacion(otra.id(), "Ajena", 30);
        fixtures.horario(otra.id(), 1, "08:00", "12:00");
        fixtures.bloqueo(otra.id(), Instant.parse("2026-11-27T13:00:00Z"),
                Instant.parse("2026-11-27T14:00:00Z"));
        fixtures.prestacion(cuenta.id(), "Propia", 30);

        mvc.perform(get("/api/prestaciones").session(cuenta.sesion().sesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prestaciones.length()").value(1))
                .andExpect(jsonPath("$.prestaciones[0].nombre").value("Propia"));
        mvc.perform(get("/api/horarios").session(cuenta.sesion().sesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.franjas.length()").value(0));
        mvc.perform(get("/api/bloqueos").session(cuenta.sesion().sesion())
                        .param("odontologoId", String.valueOf(otra.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bloqueos.length()").value(0));
    }

    @Test
    void turnosAfectadosNoExponenDniEmailTelefonoNiToken() throws Exception {
        FixturesJdbc fixtures = new FixturesJdbc(jdbc);
        long prestacion = fixtures.prestacion(cuenta.id(), "Consulta", 30);
        long paciente = fixtures.paciente(cuenta.id(), "12345678");
        fixtures.turno(cuenta.id(), paciente, prestacion,
                Instant.parse("2026-11-27T13:00:00Z"), Instant.parse("2026-11-27T13:30:00Z"), "RESERVADO");

        String cuerpo = crearBloqueo();

        assertThat(cuerpo).doesNotContain("12345678", "p12345678@example.com", "+5493510000000", "token_hash");
        assertThat(cuerpo).contains("pacienteNombre", "prestacionNombre", "RESERVADO");
    }

    private String crearBloqueo() throws Exception {
        return escribir(post("/api/bloqueos")
                        .content("""
                                {"inicio": "2026-11-27T10:00:00-03:00", "fin": "2026-11-27T12:00:00-03:00"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }
}
