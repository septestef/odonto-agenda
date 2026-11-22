package ar.turnotarde.prestacion;

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
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/** Contrato HTTP de {@code /api/prestaciones}: baja lógica, nombres por dueño y regla RN-AG-03. */
@AutoConfigureMockMvc
class PrestacionesApiIT extends IntegracionTest {

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

    private ResultActions escribir(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder peticion)
            throws Exception {
        return mvc.perform(peticion.session(cuenta.sesion().sesion())
                .cookie(cuenta.sesion().csrfCookie())
                .header("X-XSRF-TOKEN", cuenta.sesion().csrf())
                .contentType(MediaType.APPLICATION_JSON));
    }

    private ResultActions crear(String json) throws Exception {
        return escribir(post("/api/prestaciones").content(json));
    }

    private boolean activa(long id) {
        return jdbc.queryForObject("SELECT activa FROM prestacion WHERE id = ?", Boolean.class, id);
    }

    @Test
    void altaResponde201ConElDtoCreado() throws Exception {
        crear("{\"nombre\": \"Consulta\", \"duracionMinutos\": 30}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.nombre").value("Consulta"))
                .andExpect(jsonPath("$.duracionMinutos").value(30))
                .andExpect(jsonPath("$.activa").value(true));
    }

    @Test
    void edicionResponde200ConLosDatosActualizados() throws Exception {
        long id = new FixturesJdbc(jdbc).prestacion(cuenta.id(), "Vieja", 30);

        escribir(put("/api/prestaciones/{id}", id)
                        .content("{\"nombre\": \"Nueva\", \"duracionMinutos\": 45}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.nombre").value("Nueva"))
                .andExpect(jsonPath("$.duracionMinutos").value(45));
    }

    @Test
    void duracionNoMultipleDe15Da422() throws Exception {
        crear("{\"nombre\": \"Consulta\", \"duracionMinutos\": 20}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RN-AG-03"))
                .andExpect(jsonPath("$.detalles[0].campo").value("duracionMinutos"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM prestacion", Integer.class)).isZero();
    }

    @Test
    void nombreRepetidoDelMismoDuenoDa400ConDetalleEnNombre() throws Exception {
        crear("{\"nombre\": \"Consulta\", \"duracionMinutos\": 30}").andExpect(status().isCreated());

        crear("{\"nombre\": \"Consulta\", \"duracionMinutos\": 45}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles[0].campo").value("nombre"));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM prestacion", Integer.class)).isEqualTo(1);
    }

    @Test
    void elMismoNombreEnOtraCuentaEstaPermitido() throws Exception {
        CuentaDePrueba.Cuenta otra = CuentaDePrueba.crear(mvc, jdbc, "otra");
        new FixturesJdbc(jdbc).prestacion(otra.id(), "Consulta", 30);

        crear("{\"nombre\": \"Consulta\", \"duracionMinutos\": 30}").andExpect(status().isCreated());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM prestacion WHERE nombre = 'Consulta'",
                Integer.class)).isEqualTo(2);
    }

    @Test
    void bajaResponde204ConservaLaFilaYLaListaIncluyeInactivas() throws Exception {
        long id = new FixturesJdbc(jdbc).prestacion(cuenta.id(), "Limpieza", 45);

        escribir(delete("/api/prestaciones/{id}", id)).andExpect(status().isNoContent());
        assertThat(activa(id)).isFalse();

        mvc.perform(get("/api/prestaciones").session(cuenta.sesion().sesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prestaciones.length()").value(1))
                .andExpect(jsonPath("$.prestaciones[0].id").value(id))
                .andExpect(jsonPath("$.prestaciones[0].activa").value(false));

        // Idempotente.
        escribir(delete("/api/prestaciones/{id}", id)).andExpect(status().isNoContent());
        assertThat(activa(id)).isFalse();
    }

    @Test
    void unaPrestacionDadaDeBajaDesapareceDelPerfilPublico() throws Exception {
        long id = new FixturesJdbc(jdbc).prestacion(cuenta.id(), "Limpieza", 45);
        mvc.perform(get("/api/public/{slug}", cuenta.slug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prestaciones.length()").value(1));

        escribir(delete("/api/prestaciones/{id}", id)).andExpect(status().isNoContent());

        mvc.perform(get("/api/public/{slug}", cuenta.slug()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prestaciones.length()").value(0));
    }

    @Test
    void cambiarLaDuracionNoReescribeUnTurnoExistente() throws Exception {
        FixturesJdbc fixtures = new FixturesJdbc(jdbc);
        long id = fixtures.prestacion(cuenta.id(), "Consulta", 30);
        long paciente = fixtures.paciente(cuenta.id(), "12345678");
        Instant inicio = Instant.parse("2026-11-27T13:00:00Z");
        long turno = fixtures.turno(cuenta.id(), paciente, id, inicio, inicio.plusSeconds(1800), "RESERVADO");

        escribir(put("/api/prestaciones/{id}", id)
                        .content("{\"nombre\": \"Consulta\", \"duracionMinutos\": 60}"))
                .andExpect(status().isOk());

        Long minutos = jdbc.queryForObject("""
                SELECT (EXTRACT(EPOCH FROM (fin - inicio)) / 60)::bigint FROM turno WHERE id = ?""", Long.class, turno);
        assertThat(minutos).isEqualTo(30L);
    }

    @Test
    void sinSesionDa401YSinCsrfDa403() throws Exception {
        String json = "{\"nombre\": \"Consulta\", \"duracionMinutos\": 30}";
        // Con CSRF válido pero sin sesión, la cadena llega a autorización y responde 401.
        mvc.perform(post("/api/prestaciones")
                        .cookie(cuenta.sesion().csrfCookie())
                        .header("X-XSRF-TOKEN", cuenta.sesion().csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mvc.perform(post("/api/prestaciones").session(cuenta.sesion().sesion())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test
    void editarOBorrarUnaPrestacionAjenaDa404() throws Exception {
        CuentaDePrueba.Cuenta otra = CuentaDePrueba.crear(mvc, jdbc, "otra");
        long ajena = new FixturesJdbc(jdbc).prestacion(otra.id(), "Ajena", 30);
        String json = "{\"nombre\": \"Robada\", \"duracionMinutos\": 30}";

        escribir(put("/api/prestaciones/{id}", ajena).content(json))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
        escribir(delete("/api/prestaciones/{id}", ajena)).andExpect(status().isNotFound());

        assertThat(activa(ajena)).isTrue();
        assertThat(jdbc.queryForObject("SELECT nombre FROM prestacion WHERE id = ?", String.class, ajena))
                .isEqualTo("Ajena");
    }

    private static String correoNuevo() {
        return "u-" + UUID.randomUUID() + "@example.com";
    }
}
