package ar.turnotarde.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnotarde.soporte.AutenticacionDePrueba;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.prueba.ControladorProtegidoDePrueba;
import ar.turnotarde.soporte.prueba.ControladorPublicoDePrueba;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/** D4: la cookie {@code XSRF-TOKEN} se reenvía en {@code X-XSRF-TOKEN} solo en escrituras protegidas. */
@AutoConfigureMockMvc
@Import({ControladorProtegidoDePrueba.class, ControladorPublicoDePrueba.class})
class CsrfIT extends IntegracionTest {

    private static final String CABECERA = "X-XSRF-TOKEN";

    @Autowired
    private MockMvc mvc;

    @Test
    void unaEscrituraSinTokenDa403YLaSesionSigueViva() throws Exception {
        MockHttpSession sesion = AutenticacionDePrueba.sesionConRol();

        mvc.perform(post("/api/prueba/escritura").session(sesion))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));

        mvc.perform(get("/api/prueba/protegido").session(sesion))
                .andExpect(status().isOk());
    }

    @Test
    void unaEscrituraConElTokenDeLaCookiePasa() throws Exception {
        MockHttpSession sesion = AutenticacionDePrueba.sesionConRol();
        MockHttpServletResponse lectura = mvc.perform(get("/api/prueba/protegido").session(sesion))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        Cookie csrf = AutenticacionDePrueba.cookieCsrf(lectura);

        mvc.perform(post("/api/prueba/escritura").session(sesion).cookie(csrf).header(CABECERA, csrf.getValue()))
                .andExpect(status().isOk());
    }

    @Test
    void unaLecturaNoPideToken() throws Exception {
        mvc.perform(get("/api/prueba/protegido").session(AutenticacionDePrueba.sesionConRol()))
                .andExpect(status().isOk());
    }

    @Test
    void unaEscrituraPublicaNoPideToken() throws Exception {
        mvc.perform(post("/api/public/prueba"))
                .andExpect(status().isOk());
    }
}
