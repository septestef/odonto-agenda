package ar.turnotarde.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnotarde.soporte.CuentaDePrueba;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Contrato HTTP de {@code /api/horarios}: reemplazo semanal, errores 422 y seguridad. */
@AutoConfigureMockMvc
class HorariosApiIT extends IntegracionTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    private CuentaDePrueba.Cuenta cuenta;

    @BeforeEach
    void preparar() throws Exception {
        LimpiezaBase.limpiar(jdbc);
        cuenta = CuentaDePrueba.crear(mvc, jdbc);
    }

    @AfterEach
    void dejarLaBaseVacia() {
        LimpiezaBase.limpiar(jdbc);
    }

    private org.springframework.test.web.servlet.ResultActions escribir(String json) throws Exception {
        return mvc.perform(put("/api/horarios")
                .session(cuenta.sesion().sesion())
                .cookie(cuenta.sesion().csrfCookie())
                .header("X-XSRF-TOKEN", cuenta.sesion().csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private int franjasGuardadas(long odontologoId) {
        return jdbc.queryForObject("SELECT count(*) FROM horario_atencion WHERE odontologo_id = ?",
                Integer.class, odontologoId);
    }

    @Test
    void getDevuelveLasFranjasOrdenadasPorDiaYHora() throws Exception {
        new FixturesJdbc(jdbc).horario(cuenta.id(), 5, "16:00", "20:00");
        new FixturesJdbc(jdbc).horario(cuenta.id(), 1, "09:00", "13:00");
        new FixturesJdbc(jdbc).horario(cuenta.id(), 1, "16:00", "20:00");

        mvc.perform(get("/api/horarios").session(cuenta.sesion().sesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.franjas.length()").value(3))
                .andExpect(jsonPath("$.franjas[0].diaSemana").value(1))
                .andExpect(jsonPath("$.franjas[0].inicio").value("09:00"))
                .andExpect(jsonPath("$.franjas[1].inicio").value("16:00"))
                .andExpect(jsonPath("$.franjas[2].diaSemana").value(5));
    }

    @Test
    void putConVariasFranjasReemplazaElHorarioYRespondaElOrden() throws Exception {
        new FixturesJdbc(jdbc).horario(cuenta.id(), 1, "08:00", "12:00");

        escribir("""
                {"franjas": [
                  {"diaSemana": 7, "inicio": "10:00", "fin": "12:00"},
                  {"diaSemana": 1, "inicio": "09:00", "fin": "13:00"},
                  {"diaSemana": 1, "inicio": "16:00", "fin": "20:00"}
                ]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.franjas[0].diaSemana").value(1))
                .andExpect(jsonPath("$.franjas[0].inicio").value("09:00"))
                .andExpect(jsonPath("$.franjas[2].diaSemana").value(7));

        assertThat(franjasGuardadas(cuenta.id())).isEqualTo(3);
    }

    @Test
    void putConFranjaInvertidaDa422YConservaElHorarioAnterior() throws Exception {
        new FixturesJdbc(jdbc).horario(cuenta.id(), 1, "09:00", "13:00");

        escribir("""
                {"franjas": [
                  {"diaSemana": 1, "inicio": "09:00", "fin": "13:00"},
                  {"diaSemana": 3, "inicio": "13:00", "fin": "09:00"}
                ]}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RN-AG-01"))
                .andExpect(jsonPath("$.detalles[0].campo").value("franjas[1]"));

        assertThat(franjasGuardadas(cuenta.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT hora_inicio FROM horario_atencion WHERE odontologo_id = ?",
                java.time.LocalTime.class, cuenta.id())).isEqualTo(java.time.LocalTime.of(9, 0));
    }

    @Test
    void putConFranjasSolapadasDa422YConservaElHorarioAnterior() throws Exception {
        new FixturesJdbc(jdbc).horario(cuenta.id(), 1, "09:00", "13:00");

        escribir("""
                {"franjas": [
                  {"diaSemana": 1, "inicio": "09:00", "fin": "11:00"},
                  {"diaSemana": 1, "inicio": "10:00", "fin": "12:00"}
                ]}""")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RN-AG-01"))
                .andExpect(jsonPath("$.detalles[0].campo").value("franjas[1]"));

        assertThat(franjasGuardadas(cuenta.id())).isEqualTo(1);
    }

    @Test
    void putVacioDejaElHorarioSinFranjas() throws Exception {
        new FixturesJdbc(jdbc).horario(cuenta.id(), 1, "09:00", "13:00");

        escribir("{\"franjas\": []}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.franjas.length()").value(0));

        assertThat(franjasGuardadas(cuenta.id())).isZero();
    }

    @Test
    void unOdontologoIdAjenoEnElCuerpoSeIgnora() throws Exception {
        CuentaDePrueba.Cuenta otra = CuentaDePrueba.crear(mvc, jdbc, "otra");
        new FixturesJdbc(jdbc).horario(otra.id(), 1, "08:00", "12:00");

        escribir("""
                {"odontologoId": %d, "franjas": [{"diaSemana": 2, "inicio": "10:00", "fin": "14:00"}]}
                """.formatted(otra.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.franjas[0].diaSemana").value(2));

        assertThat(franjasGuardadas(cuenta.id())).isEqualTo(1);
        assertThat(franjasGuardadas(otra.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT dia_semana FROM horario_atencion WHERE odontologo_id = ?",
                Short.class, otra.id())).isEqualTo((short) 1);
    }

    @Test
    void sinSesionDa401YSinCsrfDa403() throws Exception {
        // Con CSRF válido pero sin sesión, la cadena llega a autorización y responde 401.
        mvc.perform(put("/api/horarios")
                        .cookie(cuenta.sesion().csrfCookie())
                        .header("X-XSRF-TOKEN", cuenta.sesion().csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"franjas\": []}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        mvc.perform(put("/api/horarios").session(cuenta.sesion().sesion())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"franjas\": []}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));

        assertThat(franjasGuardadas(cuenta.id())).isZero();
    }
}
