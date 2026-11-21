package ar.turnotarde.shared.seguridad;

import ar.turnotarde.shared.error.CodigosError;
import ar.turnotarde.shared.error.ReglaNegocioException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Lee el odontólogo de la sesión; si no está, responde 401 en vez de dejar un 500 (D9). */
@Component
public class OdontologoActual {

    private static final String MENSAJE = "Tu sesión no está iniciada o expiró. Volvé a ingresar.";

    public long id() {
        return autenticado().id();
    }

    public OdontologoAutenticado autenticado() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.getPrincipal() instanceof OdontologoAutenticado principal) {
            return principal;
        }
        throw new ReglaNegocioException(CodigosError.NO_AUTENTICADO, MENSAJE, HttpStatus.UNAUTHORIZED);
    }
}
