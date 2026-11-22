package ar.turnotarde.auth;

import ar.turnotarde.auth.ControlIntentosLogin.EstadoBloqueo;
import ar.turnotarde.auth.dto.DetalleBloqueo;
import ar.turnotarde.auth.dto.OdontologoDto;
import ar.turnotarde.auth.dto.PeticionLogin;
import ar.turnotarde.odontologo.Odontologo;
import ar.turnotarde.odontologo.OdontologoRepository;
import ar.turnotarde.shared.error.CodigosError;
import ar.turnotarde.shared.error.ReglaNegocioException;
import ar.turnotarde.shared.seguridad.OdontologoActual;
import ar.turnotarde.shared.seguridad.OdontologoAutenticado;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

/** Reglas de login, logout y sesión: traduce el resultado en DTOs o excepciones de la API (D2, D3, D8). */
@Service
public class ServicioAutenticacion {

    private static final String CREDENCIALES = "El email o la contraseña no son correctos.";
    private static final String BLOQUEADO = "Demasiados intentos fallidos. Probá de nuevo en unos minutos.";
    private static final String SIN_SESION = "Tu sesión no está iniciada o expiró. Volvé a ingresar.";

    private final AuthenticationManager autenticador;
    private final OdontologoRepository odontologos;
    private final OdontologoActual odontologoActual;
    private final ControlIntentosLogin control;
    private final SessionAuthenticationStrategy estrategiaSesion;
    private final SecurityContextRepository repositorioContexto;
    private final PasswordEncoder codificador;
    /** Hash BCrypt de un usuario que no existe, para igualar tiempos (D8); nunca se versiona ni se loguea. */
    private final String hashFicticio;

    public ServicioAutenticacion(AuthenticationManager autenticador, OdontologoRepository odontologos,
            OdontologoActual odontologoActual, ControlIntentosLogin control,
            SessionAuthenticationStrategy estrategiaSesion, SecurityContextRepository repositorioContexto,
            PasswordEncoder codificador) {
        this.autenticador = autenticador;
        this.odontologos = odontologos;
        this.odontologoActual = odontologoActual;
        this.control = control;
        this.estrategiaSesion = estrategiaSesion;
        this.repositorioContexto = repositorioContexto;
        this.codificador = codificador;
        this.hashFicticio = codificador.encode("no-existe-" + UUID.randomUUID());
    }

    public OdontologoDto login(PeticionLogin peticion, HttpServletRequest solicitud,
            jakarta.servlet.http.HttpServletResponse respuesta) {
        String email = peticion.email().strip();
        EstadoBloqueo bloqueo = control.estado(email);
        if (bloqueo.bloqueado()) {
            throw bloqueado(bloqueo);
        }

        Authentication autenticacion;
        try {
            autenticacion = autenticador.authenticate(
                    new UsernamePasswordAuthenticationToken(email, peticion.password()));
        } catch (UsernameNotFoundException | DisabledException sinComparacion) {
            // El proveedor no corrió BCrypt: lo hacemos igual contra un hash ficticio (D8).
            codificador.matches(peticion.password(), hashFicticio);
            throw credencialesInvalidas(email);
        } catch (AuthenticationException credenciales) {
            throw credencialesInvalidas(email);
        }

        control.registrarExito(email);
        estrategiaSesion.onAuthentication(autenticacion, solicitud, respuesta);

        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticacion);
        SecurityContextHolder.setContext(contexto);
        repositorioContexto.saveContext(contexto, solicitud, respuesta);

        OdontologoAutenticado principal = (OdontologoAutenticado) autenticacion.getPrincipal();
        return OdontologoDto.desde(recargar(principal.id()));
    }

    public void logout(HttpServletRequest solicitud) {
        olvidarSesion(solicitud);
    }

    public OdontologoDto me(HttpServletRequest solicitud) {
        OdontologoAutenticado principal = odontologoActual.autenticado();
        Odontologo odontologo = recargar(principal.id());
        if (!odontologo.isActivo()) {
            olvidarSesion(solicitud);
            throw sinSesion();
        }
        return OdontologoDto.desde(odontologo);
    }

    private ReglaNegocioException credencialesInvalidas(String email) {
        // El quinto fallo ya responde 429 (RN-AU-03); no hace falta otro pedido para ver el bloqueo.
        EstadoBloqueo estado = control.registrarFallo(email);
        if (estado.bloqueado()) {
            return bloqueado(estado);
        }
        return new ReglaNegocioException("RN-AU-01", CREDENCIALES, HttpStatus.UNAUTHORIZED);
    }

    private static ReglaNegocioException bloqueado(EstadoBloqueo estado) {
        return new ReglaNegocioException("RN-AU-03", BLOQUEADO, HttpStatus.TOO_MANY_REQUESTS,
                new DetalleBloqueo(estado.restante().toSeconds(), estado.bloqueadoHasta()));
    }

    private Odontologo recargar(Long id) {
        return odontologos.findById(id).orElseThrow(this::sinSesion);
    }

    private ReglaNegocioException sinSesion() {
        return new ReglaNegocioException(CodigosError.NO_AUTENTICADO, SIN_SESION, HttpStatus.UNAUTHORIZED);
    }

    /** Quita la autenticación de la sesión: el pedido siguiente la ve como anónima (401, no 500). */
    private static void olvidarSesion(HttpServletRequest solicitud) {
        HttpSession sesion = solicitud.getSession(false);
        if (sesion != null) {
            sesion.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        }
        SecurityContextHolder.clearContext();
    }
}
