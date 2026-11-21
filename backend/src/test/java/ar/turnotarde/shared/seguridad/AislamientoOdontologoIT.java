package ar.turnotarde.shared.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnotarde.soporte.AutenticacionDePrueba;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import ar.turnotarde.soporte.prueba.ControladorAislamientoDePrueba;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** RN-SE-01 de punta a punta: un recurso ajeno es indistinguible de uno inexistente. */
@AutoConfigureMockMvc
@Import(ControladorAislamientoDePrueba.class)
class AislamientoOdontologoIT extends IntegracionTest {

    private static final String CLAVE = "ClaveSegura123!";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    private long idA;
    private long idB;
    private long prestacionA;
    private long prestacionB;
    private org.springframework.mock.web.MockHttpSession sesionA;

    @BeforeEach
    void preparar() throws Exception {
        LimpiezaBase.limpiar(jdbc);
        FixturesJdbc fixtures = new FixturesJdbc(jdbc);
        String emailA = correoNuevo();
        String emailB = correoNuevo();
        idA = fixtures.odontologo("odontologo-a-" + UUID.randomUUID(), emailA,
                AutenticacionDePrueba.hash(CLAVE), true);
        idB = fixtures.odontologo("odontologo-b-" + UUID.randomUUID(), emailB,
                AutenticacionDePrueba.hash(CLAVE), true);
        prestacionA = fixtures.prestacion(idA, "Limpieza de A", 30);
        prestacionB = fixtures.prestacion(idB, "Limpieza de B", 30);
        sesionA = AutenticacionDePrueba.login(mvc, emailA, CLAVE).sesion();
    }

    @AfterEach
    void dejarLaBaseVacia() {
        LimpiezaBase.limpiar(jdbc);
    }

    @Test
    void elOdontologoVeSusPropiosRecursos() throws Exception {
        mvc.perform(get("/api/prueba/aislamiento/prestacion/{id}", prestacionA).session(sesionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(prestacionA))
                .andExpect(jsonPath("$.nombre").value("Limpieza de A"));
    }

    @Test
    void unRecursoAjenoDaElMismo404QueUnoInexistente() throws Exception {
        String cuerpoAjeno = mvc.perform(get("/api/prueba/aislamiento/prestacion/{id}", prestacionB)
                        .session(sesionA))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andReturn().getResponse().getContentAsString();
        String cuerpoInexistente = mvc.perform(get("/api/prueba/aislamiento/prestacion/{id}", 987654321L)
                        .session(sesionA))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpoAjeno).isEqualTo(cuerpoInexistente);
    }

    @Test
    void unIdDeOdontologoEnLaQueryNoCambiaElDueno() throws Exception {
        mvc.perform(get("/api/prueba/aislamiento/prestacion/{id}", prestacionB)
                        .param("odontologoId", String.valueOf(idB))
                        .session(sesionA))
                .andExpect(status().isNotFound());

        MvcResult propio = mvc.perform(get("/api/prueba/aislamiento/prestacion/{id}", prestacionA)
                        .param("odontologoId", String.valueOf(idB))
                        .session(sesionA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Limpieza de A"))
                .andReturn();

        assertThat(propio.getResponse().getContentAsString()).contains("Limpieza de A");
        assertThat(idB).isNotEqualTo(idA);
    }

    private static String correoNuevo() {
        return "u-" + UUID.randomUUID() + "@example.com";
    }
}
