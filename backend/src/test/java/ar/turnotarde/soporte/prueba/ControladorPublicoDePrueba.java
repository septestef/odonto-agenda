package ar.turnotarde.soporte.prueba;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Verifica que la reserva pública de C-07 siga sin sesión ni token CSRF. */
@RestController
public class ControladorPublicoDePrueba {

    @PostMapping("/api/public/prueba")
    public String publico() {
        return "publico";
    }
}
