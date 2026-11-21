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
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Disponibilidad pública sin sesión. Reloj fijo en 2026-11-25T12:00:00Z (09:00 local). */
@AutoConfigureMockMvc
@Import(ConfiguracionRelojDePrueba.class)
class DisponibilidadPublicaIT extends IntegracionTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    private FixturesJdbc fixtures;
    private long demo;
    private long consulta;

    @AfterEach
    void dejarLaBaseVacia() {
        LimpiezaBase.limpiar(jdbc);
    }

    @BeforeEach
    void preparar() {
        LimpiezaBase.limpiar(jdbc);
        fixtures = new FixturesJdbc(jdbc);
        demo = fixtures.odontologo("demo");
        consulta = fixtures.prestacion(demo, "Consulta", 30);
        fixtures.horario(demo, 5, "09:00", "10:00");
    }

    private ResultActions consultar(String slug, Object prestacionId, String fecha) throws Exception {
        return mvc.perform(get("/api/public/{slug}/disponibilidad", slug)
                .param("prestacionId", String.valueOf(prestacionId)).param("fecha", fecha));
    }

    @Test
    void consultaExitosaDevuelveElContratoDeD3() throws Exception {
        consultar("demo", consulta, "2026-11-27")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(4)))
                .andExpect(jsonPath("$.fecha").value("2026-11-27"))
                .andExpect(jsonPath("$.prestacionId").value(consulta))
                .andExpect(jsonPath("$.duracionMinutos").value(30))
                .andExpect(jsonPath("$.horarios", hasSize(3)))
                .andExpect(jsonPath("$.horarios[0]", aMapWithSize(2)))
                .andExpect(jsonPath("$.horarios[0].inicio").value("2026-11-27T09:00:00-03:00"))
                .andExpect(jsonPath("$.horarios[0].hora").value("09:00"))
                .andExpect(jsonPath("$.horarios[1].inicio").value("2026-11-27T09:15:00-03:00"))
                .andExpect(jsonPath("$.horarios[1].hora").value("09:15"))
                .andExpect(jsonPath("$.horarios[2].hora").value("09:30"));
    }

    @Test
    void sinFranjasOFechaPasadaOFueraDelHorizonteLaListaEsVaciaYLaRespuestaExitosa() throws Exception {
        fixtures.horario(demo, 1, "09:00", "10:00");
        fixtures.horario(demo, 2, "09:00", "10:00");

        consultar("demo", consulta, "2026-11-28").andExpect(status().isOk())
                .andExpect(jsonPath("$.horarios", hasSize(0)));
        consultar("demo", consulta, "2026-11-24").andExpect(status().isOk())
                .andExpect(jsonPath("$.horarios", hasSize(0)));
        consultar("demo", consulta, "2027-01-25").andExpect(status().isOk())
                .andExpect(jsonPath("$.horarios", hasSize(0)));
    }

    @Test
    void elTurnoReservadoYElBloqueoOcupanLugarSinExponerDatosDeNadie() throws Exception {
        fixtures.horario(demo, 5, "10:00", "11:00");
        long paciente = fixtures.paciente(demo, "12345678");
        long turno = fixtures.turno(demo, paciente, consulta, Instant.parse("2026-11-27T12:00:00Z"),
                Instant.parse("2026-11-27T12:30:00Z"), "RESERVADO");
        long bloqueo = fixtures.bloqueo(demo, Instant.parse("2026-11-27T12:30:00Z"),
                Instant.parse("2026-11-27T12:45:00Z"));
        jdbc.update("UPDATE bloqueo SET motivo = 'Vacaciones en Bariloche' WHERE id = ?", bloqueo);

        String cuerpo = consultar("demo", consulta, "2026-11-27")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horarios", hasSize(3)))
                .andExpect(jsonPath("$.horarios[0].hora").value("10:00"))
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).doesNotContain("12345678").doesNotContain("Paciente").doesNotContain("Ficticio")
                .doesNotContain("p12345678@example.com").doesNotContain("Vacaciones").doesNotContain("Bariloche")
                .doesNotContain("turno").doesNotContain("\"" + turno + "\"");
    }

    @Test
    void prestacionDeOtroOdontologoDeBajaOSlugInexistenteOInactivoDanElMismo404() throws Exception {
        long otro = fixtures.odontologo("otro");
        long ajena = fixtures.prestacion(otro, "Ajena", 30);
        long deBaja = fixtures.prestacion(demo, "Vieja", 30);
        jdbc.update("UPDATE prestacion SET activa = false WHERE id = ?", deBaja);
        long inactivo = fixtures.odontologo("inactivo");
        long prestacionInactivo = fixtures.prestacion(inactivo, "Consulta", 30);
        jdbc.update("UPDATE odontologo SET activo = false WHERE id = ?", inactivo);

        String esperado = consultar("no-existe", 1, "2026-11-27")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andReturn().getResponse().getContentAsString();

        assertThat(consultar("demo", ajena, "2026-11-27").andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString()).isEqualTo(esperado);
        assertThat(consultar("demo", deBaja, "2026-11-27").andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString()).isEqualTo(esperado);
        assertThat(consultar("demo", 999999, "2026-11-27").andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString()).isEqualTo(esperado);
        assertThat(consultar("inactivo", prestacionInactivo, "2026-11-27").andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString()).isEqualTo(esperado);
    }

    @Test
    void parametrosFaltantesOMalFormadosDan400() throws Exception {
        mvc.perform(get("/api/public/demo/disponibilidad").param("fecha", "2026-11-27"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
        mvc.perform(get("/api/public/demo/disponibilidad").param("prestacionId", String.valueOf(consulta)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
        consultar("demo", consulta, "27/11/2026").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
        consultar("demo", "abc", "2026-11-27").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }
}
