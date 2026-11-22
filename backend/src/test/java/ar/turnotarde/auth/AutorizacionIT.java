package ar.turnotarde.auth;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnotarde.soporte.AutenticacionDePrueba;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.prueba.ControladorProtegidoDePrueba;
import ar.turnotarde.soporte.prueba.ControladorPublicoDePrueba;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** D5: público sin sesión, protegido con rol, SPA siempre servida, 401 en JSON sin redirect. */
@AutoConfigureMockMvc
@Import({ControladorProtegidoDePrueba.class, ControladorPublicoDePrueba.class})
@TestPropertySource(properties = "app.frontend.ubicacion=classpath:/spa-prueba/")
class AutorizacionIT extends IntegracionTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void unaEscrituraPublicaPasaSinSesionNiCsrf() throws Exception {
        mvc.perform(post("/api/public/prueba"))
                .andExpect(status().isOk())
                .andExpect(content().string("publico"));
    }

    @Test
    void unaRutaProtegidaSinSesionDa401JsonSinRedirect() throws Exception {
        mvc.perform(get("/api/prueba/protegido"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"))
                .andExpect(jsonPath("$.mensaje").isString())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void unaRutaProtegidaConSesionDeOdontologoPasa() throws Exception {
        mvc.perform(get("/api/prueba/protegido").session(AutenticacionDePrueba.sesionConRol()))
                .andExpect(status().isOk())
                .andExpect(content().string("protegido"));
    }

    @Test
    void unaRutaDeApiInexistenteSinSesionDa401YNoElIndex() throws Exception {
        mvc.perform(get("/api/no-existe"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void elFrontendSeSirveSinSesion() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SPA-DE-PRUEBA")));
        mvc.perform(get("/assets/app.js"))
                .andExpect(status().isOk());
        mvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SPA-DE-PRUEBA")));
    }
}
