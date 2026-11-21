package ar.turnotarde.shared.seguridad;

import ar.turnotarde.shared.error.CodigosError;
import ar.turnotarde.shared.error.ErrorApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/** 403 por rol o CSRF: JSON uniforme y mensaje que no filtra el motivo interno (D6). */
@Component
public class ErrorApiAccessDeniedHandler implements AccessDeniedHandler {

    private static final String MENSAJE = "No pudimos verificar la solicitud. Recargá la página e intentá de nuevo.";

    private final EscritorErrorJson escritor;

    public ErrorApiAccessDeniedHandler(EscritorErrorJson escritor) {
        this.escritor = escritor;
    }

    @Override
    public void handle(HttpServletRequest solicitud, HttpServletResponse respuesta,
            AccessDeniedException error) throws IOException {
        escritor.escribir(respuesta, HttpStatus.FORBIDDEN, new ErrorApi(CodigosError.ACCESO_DENEGADO, MENSAJE));
    }
}
