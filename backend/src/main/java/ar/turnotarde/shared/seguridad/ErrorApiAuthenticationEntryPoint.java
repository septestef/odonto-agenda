package ar.turnotarde.shared.seguridad;

import ar.turnotarde.shared.error.CodigosError;
import ar.turnotarde.shared.error.ErrorApi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/** 401 sin sesión: JSON uniforme, sin formulario ni redirect (D6). */
@Component
public class ErrorApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final String MENSAJE = "Tu sesión no está iniciada o expiró. Volvé a ingresar.";

    private final EscritorErrorJson escritor;

    public ErrorApiAuthenticationEntryPoint(EscritorErrorJson escritor) {
        this.escritor = escritor;
    }

    @Override
    public void commence(HttpServletRequest solicitud, HttpServletResponse respuesta,
            AuthenticationException error) throws IOException {
        escritor.escribir(respuesta, HttpStatus.UNAUTHORIZED, new ErrorApi(CodigosError.NO_AUTENTICADO, MENSAJE));
    }
}
