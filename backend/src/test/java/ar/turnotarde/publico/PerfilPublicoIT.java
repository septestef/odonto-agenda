package ar.turnotarde.publico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnotarde.soporte.ConfiguracionRelojDePrueba;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Perfil público sin sesión. Reloj fijo en 2026-11-25T12:00:00Z (09:00 local), horizonte de 60 días. */
@AutoConfigureMockMvc
@Import(ConfiguracionRelojDePrueba.class)
class PerfilPublicoIT extends IntegracionTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    private FixturesJdbc fixtures;
    private long demo;

    @AfterEach
    void dejarLaBaseVacia() {
        LimpiezaBase.limpiar(jdbc);
    }

    @BeforeEach
    void preparar() {
        LimpiezaBase.limpiar(jdbc);
        fixtures = new FixturesJdbc(jdbc);
        demo = fixtures.odontologo("demo");
        jdbc.update("UPDATE odontologo SET direccion_consultorio = 'Av. Colón 1234, Córdoba', telefono = '+5493510001111'"
                + " WHERE id = ?", demo);
    }

    @Test
    void perfilConPrestacionesActivasOrdenadasPorNombreYSinPrestacionesDeBaja() throws Exception {
        long blanqueamiento = fixtures.prestacion(demo, "Blanqueamiento", 60);
        jdbc.update("UPDATE prestacion SET activa = false WHERE id = ?", blanqueamiento);
        long limpieza = fixtures.prestacion(demo, "Limpieza", 45);
        long consulta = fixtures.prestacion(demo, "Consulta", 30);

        mvc.perform(get("/api/public/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("demo"))
                .andExpect(jsonPath("$.nombre").value("Nombre"))
                .andExpect(jsonPath("$.apellido").value("Apellido"))
                .andExpect(jsonPath("$.direccionConsultorio").value("Av. Colón 1234, Córdoba"))
                .andExpect(jsonPath("$.zonaHoraria").value("America/Argentina/Buenos_Aires"))
                .andExpect(jsonPath("$.prestaciones", hasSize(2)))
                .andExpect(jsonPath("$.prestaciones[0].id").value(consulta))
                .andExpect(jsonPath("$.prestaciones[0].nombre").value("Consulta"))
                .andExpect(jsonPath("$.prestaciones[0].duracionMinutos").value(30))
                .andExpect(jsonPath("$.prestaciones[1].id").value(limpieza))
                .andExpect(jsonPath("$.prestaciones[1].nombre").value("Limpieza"))
                .andExpect(jsonPath("$.prestaciones[1].duracionMinutos").value(45))
                .andExpect(jsonPath("$.prestaciones[0]", aMapWithSize(3)));
    }

    @Test
    void elPerfilTieneSoloLosOchoCamposPublicosYNoExponeEmailTelefonoNiIdDelOdontologo() throws Exception {
        fixtures.prestacion(demo, "Consulta", 30);

        String cuerpo = mvc.perform(get("/api/public/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(8)))
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).doesNotContain("demo@example.com").doesNotContain("+5493510001111")
                .doesNotContain("passwordHash").doesNotContain("password");
    }

    @Test
    void direccionDelConsultorioVaciaSeDevuelveNula() throws Exception {
        jdbc.update("UPDATE odontologo SET direccion_consultorio = NULL WHERE id = ?", demo);

        mvc.perform(get("/api/public/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direccionConsultorio").value((Object) null))
                .andExpect(jsonPath("$.prestaciones", hasSize(0)));
    }

    @Test
    void ventanaDeFechasReservablesSalDelRelojDelServidor() throws Exception {
        mvc.perform(get("/api/public/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fechaMinima").value("2026-11-26"))
                .andExpect(jsonPath("$.fechaMaxima").value("2027-01-24"));
    }

    @Test
    void slugInexistenteEInactivoDanElMismoCuerpo404() throws Exception {
        long inactivo = fixtures.odontologo("inactivo");
        jdbc.update("UPDATE odontologo SET activo = false WHERE id = ?", inactivo);

        String inexistente = mvc.perform(get("/api/public/no-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andReturn().getResponse().getContentAsString();
        String deBaja = mvc.perform(get("/api/public/inactivo"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andReturn().getResponse().getContentAsString();

        assertThat(deBaja).isEqualTo(inexistente);
    }
}
