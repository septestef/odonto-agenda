package ar.turnotarde.auth;

import ar.turnotarde.auth.dto.OdontologoDto;
import ar.turnotarde.auth.dto.PeticionLogin;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Los tres endpoints de sesión (D2): el controlador solo traduce HTTP. */
@RestController
@RequestMapping("/api/auth")
public class ControladorAuth {

    private final ServicioAutenticacion servicio;

    public ControladorAuth(ServicioAutenticacion servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/login")
    public OdontologoDto login(@Valid @RequestBody PeticionLogin peticion, HttpServletRequest solicitud,
            HttpServletResponse respuesta) {
        return servicio.login(peticion, solicitud, respuesta);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest solicitud) {
        servicio.logout(solicitud);
    }

    @GetMapping("/me")
    public OdontologoDto me(HttpServletRequest solicitud) {
        return servicio.me(solicitud);
    }
}
