package ar.turnotarde.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnotarde.soporte.AutenticacionDePrueba;
import ar.turnotarde.soporte.ConfiguracionRelojDePrueba;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/** Sesión de servidor: /me, logout con CSRF y rotación del id al autenticar (D2, D3, D4). */
@AutoConfigureMockMvc
@Import(ConfiguracionRelojDePrueba.class)
@TestPropertySource(properties = "app.frontend.ubicacion=classpath:/spa-prueba/")
class SesionIT extends IntegracionTest {

    private static final String CLAVE = "ClaveSegura123!";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper json;

    private String email;
    private long odontologoId;

    @BeforeEach
    void preparar() {
        LimpiezaBase.limpiar(jdbc);
        email = "u-" + UUID.randomUUID() + "@example.com";
        odontologoId = new FixturesJdbc(jdbc).odontologo("demo-" + UUID.randomUUID(), email,
                AutenticacionDePrueba.hash(CLAVE), true);
    }

    @AfterEach
    void dejarLaBaseVacia() {
        LimpiezaBase.limpiar(jdbc);
    }

    @Test
    void meSinSesionDa401() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void meConSesionDevuelveLosDatosDelOdontologo() throws Exception {
        AutenticacionDePrueba.SesionDePrueba sesion = AutenticacionDePrueba.login(mvc, email, CLAVE);

        mvc.perform(get("/api/auth/me").session(sesion.sesion()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(odontologoId))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void logoutConTokenCsrfInvalidaLaSesion() throws Exception {
        AutenticacionDePrueba.SesionDePrueba sesion = AutenticacionDePrueba.login(mvc, email, CLAVE);
        Cookie csrf = tokenCsrf(sesion.sesion());

        mvc.perform(post("/api/auth/logout").session(sesion.sesion()).cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").session(sesion.sesion()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void logoutSinTokenCsrfDa403YLaSesionSigueViva() throws Exception {
        AutenticacionDePrueba.SesionDePrueba sesion = AutenticacionDePrueba.login(mvc, email, CLAVE);

        mvc.perform(post("/api/auth/logout").session(sesion.sesion()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));

        mvc.perform(get("/api/auth/me").session(sesion.sesion()))
                .andExpect(status().isOk());
    }

    @Test
    void logoutSinSesionDa401() throws Exception {
        // Se toma la cookie CSRF de una visita anónima para que el rechazo venga de la autorización, no del CSRF.
        Cookie csrf = AutenticacionDePrueba.cookieCsrf(mvc.perform(get("/index.html"))
                .andExpect(status().isOk()).andReturn().getResponse());

        mvc.perform(post("/api/auth/logout").cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void elIdDeSesionCambiaDespuesDelLogin() throws Exception {
        MockHttpSession sesionPrevia = new MockHttpSession();
        String idPrevio = sesionPrevia.getId();

        // MockMvc no marca la sesión entrante como válida: se hace explícito para que la estrategia
        // de fijación rote el id (en Tomcat el contenedor lo hace por la cookie recibida).
        MvcResult resultado = mvc.perform(post("/api/auth/login").session(sesionPrevia)
                        .with(pedido -> {
                            pedido.setRequestedSessionId(idPrevio);
                            pedido.setRequestedSessionIdValid(true);
                            return pedido;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", email, "password", CLAVE))))
                .andExpect(status().isOk())
                .andReturn();

        String idNuevo = resultado.getRequest().getSession().getId();
        assertThat(idNuevo).isNotEqualTo(idPrevio);
    }

    @Test
    void meConLaCuentaDesactivadaDa401YLaSesionDejaDeServir() throws Exception {
        AutenticacionDePrueba.SesionDePrueba sesion = AutenticacionDePrueba.login(mvc, email, CLAVE);
        jdbc.update("UPDATE odontologo SET activo = false WHERE id = ?", odontologoId);

        mvc.perform(get("/api/auth/me").session(sesion.sesion()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        mvc.perform(get("/api/auth/me").session(sesion.sesion()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    /** Levanta el token CSRF de un GET autenticado, igual que haría el frontend. */
    private Cookie tokenCsrf(MockHttpSession sesion) throws Exception {
        return AutenticacionDePrueba.cookieCsrf(mvc.perform(get("/api/auth/me").session(sesion))
                .andExpect(status().isOk()).andReturn().getResponse());
    }
}
