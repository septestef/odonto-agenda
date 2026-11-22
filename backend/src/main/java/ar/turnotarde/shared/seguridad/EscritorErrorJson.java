package ar.turnotarde.shared.seguridad;

import ar.turnotarde.shared.error.ErrorApi;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Serializa los rechazos de la cadena de filtros con el mismo formato que el {@code @RestControllerAdvice}.
 * Los filtros no pasan por el advice, así que este es el único punto que escribe el 401/403 de la API.
 */
@Component
public class EscritorErrorJson {

    private final ObjectMapper mapper;

    public EscritorErrorJson(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void escribir(HttpServletResponse respuesta, HttpStatus estado, ErrorApi error) throws IOException {
        respuesta.setStatus(estado.value());
        respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        respuesta.getWriter().write(mapper.writeValueAsString(error));
    }
}
