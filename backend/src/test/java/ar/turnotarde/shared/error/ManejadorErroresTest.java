package ar.turnotarde.shared.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(controllers = ControladorDePrueba.class)
@Import(ManejadorErrores.class)
@ExtendWith(OutputCaptureExtension.class)
class ManejadorErroresTest {

    @Autowired
    private MockMvc mvc;

    private static ResultActions esJson(ResultActions resultado) throws Exception {
        return resultado.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void reglaDeNegocioRespondeConSuCodigoMensajeYEstado() throws Exception {
        esJson(mvc.perform(post("/api/prueba/regla")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RN-TU-01"))
                .andExpect(jsonPath("$.mensaje").value("Ese horario acaba de ser tomado, elegí otro"));
    }

    @Test
    void reglaDeNegocioSinDetallesOmiteLaClaveDetalles() throws Exception {
        esJson(mvc.perform(post("/api/prueba/regla")))
                .andExpect(jsonPath("$.detalles").doesNotExist());
    }

    @Test
    void reglaDeNegocioConDetallesLosIncluye() throws Exception {
        esJson(mvc.perform(post("/api/prueba/regla-con-detalles")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.codigo").value("RN-TU-03"))
                .andExpect(jsonPath("$.detalles.franja").value("09:00-12:00"));
    }

    @Test
    void campoObligatorioVacioDevuelveValidacionConDetallePorCampo() throws Exception {
        esJson(mvc.perform(post("/api/prueba/validacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles[0].campo").value("nombre"))
                .andExpect(jsonPath("$.detalles[0].mensaje").value("es obligatorio"));
    }

    @Test
    void parametroInvalidoDevuelveValidacionConElNombreDelParametro() throws Exception {
        esJson(mvc.perform(get("/api/prueba/parametro").param("n", "0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles[0].campo").value("n"))
                .andExpect(jsonPath("$.detalles[0].mensaje").value("debe ser al menos 1"));
    }

    @Test
    void jsonMalFormadoDevuelveSolicitudInvalida() throws Exception {
        esJson(mvc.perform(post("/api/prueba/validacion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{nombre:")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"))
                .andExpect(jsonPath("$.detalles").doesNotExist());
    }

    @Test
    void metodoNoSoportadoDevuelve405() throws Exception {
        esJson(mvc.perform(delete("/api/prueba/solo-get")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));
    }

    @Test
    void rutaDeApiInexistenteDevuelve404EnJson() throws Exception {
        esJson(mvc.perform(get("/api/no-existe")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").isString());
    }

    @Test
    void excepcionInesperadaDevuelve500GenericoSinFiltrarDetallesInternos(CapturedOutput salida) throws Exception {
        String cuerpo = esJson(mvc.perform(get("/api/prueba/explota")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(jsonPath("$.detalles").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo)
                .doesNotContain("secreto interno")
                .doesNotContain("tabla_privada")
                .doesNotContain("IllegalStateException")
                .doesNotContain("ar.turnotarde")
                .doesNotContain("trace");
        assertThat(salida.getAll()).contains("secreto interno").contains("IllegalStateException");
    }
}
