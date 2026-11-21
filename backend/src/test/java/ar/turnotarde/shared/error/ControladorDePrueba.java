package ar.turnotarde.shared.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Controlador solo de tests: dispara cada tipo de error que maneja el advice. */
@RestController
class ControladorDePrueba {

    record Entrada(@NotBlank(message = "es obligatorio") String nombre) {
    }

    @PostMapping("/api/prueba/regla")
    void regla() {
        throw new ReglaNegocioException("RN-TU-01", "Ese horario acaba de ser tomado, elegí otro", HttpStatus.CONFLICT);
    }

    @PostMapping("/api/prueba/regla-con-detalles")
    void reglaConDetalles() {
        throw new ReglaNegocioException("RN-TU-03", "Fuera de agenda", HttpStatus.UNPROCESSABLE_ENTITY,
                java.util.Map.of("franja", "09:00-12:00"));
    }

    @PostMapping("/api/prueba/validacion")
    void validacion(@Valid @RequestBody Entrada entrada) {
    }

    @GetMapping("/api/prueba/parametro")
    String parametro(@RequestParam @Min(value = 1, message = "debe ser al menos 1") int n) {
        return "ok";
    }

    @GetMapping("/api/prueba/solo-get")
    String soloGet() {
        return "ok";
    }

    @GetMapping("/api/prueba/explota")
    String explota() {
        throw new IllegalStateException("secreto interno: SELECT * FROM tabla_privada");
    }

    @PostMapping("/api/prueba/solapamiento")
    void solapamiento() {
        throw new org.springframework.dao.DataIntegrityViolationException("could not execute statement [SELECT secreto]",
                new java.sql.SQLException(
                        "ERROR: conflicting key value violates exclusion constraint \"turno_sin_solapamiento\"",
                        "23P01"));
    }

    @PostMapping("/api/prueba/integridad")
    void integridad() {
        throw new org.springframework.dao.DataIntegrityViolationException("could not execute statement [INSERT x]",
                new java.sql.SQLException("duplicate key value violates unique constraint \"paciente_x\"", "23505"));
    }
}
