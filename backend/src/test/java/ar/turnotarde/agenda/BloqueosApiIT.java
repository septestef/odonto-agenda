package ar.turnotarde.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnotarde.soporte.CuentaDePrueba;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Un bloqueo anula la disponibilidad pero no cancela turnos (RN-AG-02). Reloj fijo. */
@AutoConfigureMockMvc
@Import(ConfiguracionRelojDePrueba.class)
class BloqueosApiIT extends IntegracionTest {

    private static final String INICIO = "2026-11-27T13:00:00Z"; // 10:00 local
    private static final String FIN = "2026-11-27T15:00:00Z"; // 12:00 local

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    private CuentaDePrueba.Cuenta cuenta;
    private long consulta;
    private long reservado;
    private long fueraDelBloque;

    @BeforeEach
    void preparar() throws Exception {
        LimpiezaBase.limpiar(jdbc);
        cuenta = CuentaDePrueba.crear(mvc, jdbc);
        FixturesJdbc fixtures = new FixturesJdbc(jdbc);
        consulta = fixtures.prestacion(cuenta.id(), "Consulta", 30);
        fixtures.horario(cuenta.id(), 5, "09:00", "13:00");
        long paciente = fixtures.paciente(cuenta.id(), "12345678");
        reservado = fixtures.turno(cuenta.id(), paciente, consulta,
                Instant.parse("2026-11-27T13:30:00Z"), Instant.parse("2026-11-27T14:00:00Z"), "RESERVADO");
        fueraDelBloque = fixtures.turno(cuenta.id(), paciente, consulta,
                Instant.parse("2026-11-27T15:00:00Z"), Instant.parse("2026-11-27T15:30:00Z"), "RESERVADO");
        fixtures.turno(cuenta.id(), paciente, consulta,
                Instant.parse("2026-11-27T14:00:00Z"), Instant.parse("2026-11-27T14:30:00Z"), "CANCELADO");
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
                .contentType(MediaType.APPLICATION_JSON));
    }

    private ResultActions crear(String json) throws Exception {
        return escribir(post("/api/bloqueos").content(json));
    }

    private String estadoDelTurno(long id) {
        return jdbc.queryForObject("SELECT estado FROM turno WHERE id = ?", String.class, id);
    }

    @Test
    void altaResponde201ConTurnosAfectadosSinCancelarlos() throws Exception {
        crear("""
                {"inicio": "2026-11-27T10:00:00-03:00", "fin": "2026-11-27T12:00:00-03:00",
                 "motivo": "Control particular"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.inicio").value("2026-11-27T10:00:00-03:00"))
                .andExpect(jsonPath("$.fin").value("2026-11-27T12:00:00-03:00"))
                .andExpect(jsonPath("$.motivo").value("Control particular"))
                .andExpect(jsonPath("$.turnosAfectados.length()").value(1))
                .andExpect(jsonPath("$.turnosAfectados[0].id").value(reservado))
                .andExpect(jsonPath("$.turnosAfectados[0].estado").value("RESERVADO"))
                .andExpect(jsonPath("$.turnosAfectados[0].pacienteNombre").value("Paciente"))
                .andExpect(jsonPath("$.turnosAfectados[0].prestacionNombre").value("Consulta"));

        assertThat(estadoDelTurno(reservado)).isEqualTo("RESERVADO");
    }

    @Test
    void unRangoInvertidoDa400ConDetalleEnFin() throws Exception {
        crear("""
                {"inicio": "2026-11-27T12:00:00-03:00", "fin": "2026-11-27T10:00:00-03:00"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles[0].campo").value("fin"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM bloqueo", Integer.class)).isZero();
    }

    @Test
    void getDevuelveElListadoConLosMismosTurnosAfectados() throws Exception {
        crear("""
                {"inicio": "2026-11-27T10:00:00-03:00", "fin": "2026-11-27T12:00:00-03:00"}""")
                .andExpect(status().isCreated());

        mvc.perform(get("/api/bloqueos").session(cuenta.sesion().sesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bloqueos.length()").value(1))
                .andExpect(jsonPath("$.bloqueos[0].turnosAfectados.length()").value(1))
                .andExpect(jsonPath("$.bloqueos[0].turnosAfectados[0].estado").value("RESERVADO"));
    }

    @Test
    void borrarResponde204YSinTokenDa403() throws Exception {
        String json = """
                {"inicio": "2026-11-27T10:00:00-03:00", "fin": "2026-11-27T12:00:00-03:00"}""";
        String respuesta = crear(json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = new tools.jackson.databind.ObjectMapper().readTree(respuesta).get("id").asLong();

        mvc.perform(delete("/api/bloqueos/{id}", id).session(cuenta.sesion().sesion())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));

        escribir(delete("/api/bloqueos/{id}", id)).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM bloqueo", Integer.class)).isZero();
    }

    @Test
    void elRangoBloqueadoDejaDeOfrecerseEnLaDisponibilidadPublica() throws Exception {
        mvc.perform(get("/api/public/{slug}/disponibilidad", cuenta.slug())
                        .param("prestacionId", String.valueOf(consulta)).param("fecha", "2026-11-27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horarios[*].hora", hasItem("10:00")))
                .andExpect(jsonPath("$.horarios[*].hora", not(hasItem("12:00"))));

        crear("""
                {"inicio": "2026-11-27T10:00:00-03:00", "fin": "2026-11-27T12:00:00-03:00"}""")
                .andExpect(status().isCreated());

        mvc.perform(get("/api/public/{slug}/disponibilidad", cuenta.slug())
                        .param("prestacionId", String.valueOf(consulta)).param("fecha", "2026-11-27"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horarios[*].hora", not(hasItem("10:00"))))
                .andExpect(jsonPath("$.horarios[*].hora", not(hasItem("11:00"))))
                .andExpect(jsonPath("$.horarios[*].hora", not(hasItem("12:00"))));
        // El bloqueo no cancela turnos: el que estaba fuera del rango sigue ocupando su horario.
        assertThat(estadoDelTurno(reservado)).isEqualTo("RESERVADO");
        assertThat(estadoDelTurno(fueraDelBloque)).isEqualTo("RESERVADO");
    }

    @Test
    void borrarUnBloqueoAjenoDa404() throws Exception {
        CuentaDePrueba.Cuenta otra = CuentaDePrueba.crear(mvc, jdbc, "otra");
        String json = """
                {"inicio": "2026-11-27T10:00:00-03:00", "fin": "2026-11-27T12:00:00-03:00"}""";
        mvc.perform(post("/api/bloqueos").session(otra.sesion().sesion())
                        .cookie(otra.sesion().csrfCookie())
                        .header("X-XSRF-TOKEN", otra.sesion().csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated());
        long ajeno = jdbc.queryForObject("SELECT id FROM bloqueo", Long.class);

        escribir(delete("/api/bloqueos/{id}", ajeno))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM bloqueo", Integer.class)).isEqualTo(1);
    }
}
