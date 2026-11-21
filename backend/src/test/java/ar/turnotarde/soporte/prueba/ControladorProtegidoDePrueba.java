package ar.turnotarde.soporte.prueba;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Controlador de tests para comprobar autorización y CSRF; se registra con {@code @Import}, nunca por scan. */
@RestController
public class ControladorProtegidoDePrueba {

    @GetMapping("/api/prueba/protegido")
    public String protegido() {
        return "protegido";
    }

    @PostMapping("/api/prueba/escritura")
    public String escritura() {
        return "escritura";
    }
}
