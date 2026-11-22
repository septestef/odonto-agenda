package ar.turnotarde.shared.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnotarde.soporte.AutenticacionDePrueba;
import ar.turnotarde.soporte.IntegracionTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@TestPropertySource(properties = "app.frontend.ubicacion=classpath:/spa-prueba/")
class EntregaSpaIT extends IntegracionTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void laRaizReenviaAlIndexDeLaSpa() throws Exception {
        // MockMvc no ejecuta el forward: se verifica el destino y que el destino responda (ver el test siguiente).
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void elIndexDeLaSpaSeSirveComoArchivoEstatico() throws Exception {
        mvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SPA-DE-PRUEBA")));
    }

    @Test
    void unArchivoEstaticoExistenteSeSirveTalCual() throws Exception {
        mvc.perform(get("/assets/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("spa-prueba")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("SPA-DE-PRUEBA"))));
    }

    @Test
    void unaRutaProfundaDeLaSpaDevuelveElIndex() throws Exception {
        mvc.perform(get("/turno/abc123"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SPA-DE-PRUEBA")));
    }

    @Test
    void unaRutaDeVariosSegmentosSinArchivoCaeEnElFallback() throws Exception {
        mvc.perform(get("/otra/ruta/pagina"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SPA-DE-PRUEBA")));
    }

    @Test
    void unaRutaDeApiInexistenteSinSesionDevuelve401YNoElIndex() throws Exception {
        // C-03 (D5): la autorización corre antes del ruteo; un anónimo no puede enumerar rutas de /api/**.
        mvc.perform(get("/api/no-existe"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
    }

    @Test
    void unaRutaDeApiInexistenteConSesionDevuelve404EnJsonYNoElIndex() throws Exception {
        mvc.perform(get("/api/no-existe").session(AutenticacionDePrueba.sesionConRol()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    void unaRutaDeApiAnidadaInexistenteConSesionTambienDevuelve404EnJson() throws Exception {
        mvc.perform(get("/api/turnos/123/no-existe").session(AutenticacionDePrueba.sesionConRol()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }
}
