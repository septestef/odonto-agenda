package ar.turnotarde.shared.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnotarde.shared.error.ErrorApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

/** Los rechazos de la cadena de filtros salen en el mismo formato que los del advice. */
class EscritorErrorJsonTest {

    private final EscritorErrorJson escritor = new EscritorErrorJson(new ObjectMapper());
    private MockHttpServletResponse respuesta;

    @BeforeEach
    void preparar() {
        respuesta = new MockHttpServletResponse();
    }

    @Test
    void el401EsJsonSinRedirectNiHtml() throws Exception {
        escritor.escribir(respuesta, HttpStatus.UNAUTHORIZED,
                new ErrorApi("NO_AUTENTICADO", "Tu sesión no está iniciada o expiró. Volvé a ingresar."));

        assertThat(respuesta.getStatus()).isEqualTo(401);
        assertThat(MediaType.parseMediaType(respuesta.getContentType()).getType()).isEqualTo("application");
        assertThat(respuesta.getHeader("Location")).isNull();
        assertThat(respuesta.getContentAsString())
                .contains("\"codigo\":\"NO_AUTENTICADO\"")
                .doesNotContain("<html")
                .doesNotContain("<!DOCTYPE");
    }

    @Test
    void el403UsaElMismoFormato() throws Exception {
        escritor.escribir(respuesta, HttpStatus.FORBIDDEN,
                new ErrorApi("ACCESO_DENEGADO", "No pudimos verificar la solicitud. Recargá la página e intentá de nuevo."));

        assertThat(respuesta.getStatus()).isEqualTo(403);
        assertThat(respuesta.getContentAsString()).contains("\"codigo\":\"ACCESO_DENEGADO\"");
        assertThat(respuesta.getContentAsString()).doesNotContain("detalles");
    }
}
