package ar.turnotarde.publico;

import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints sin sesión del paciente. Delgado: toda la lógica vive en {@link PublicoService}. */
@RestController
@RequestMapping("/api/public/{slug}")
public class ControladorPublico {

    private final PublicoService publico;

    public ControladorPublico(PublicoService publico) {
        this.publico = publico;
    }

    @GetMapping
    public PerfilPublico perfil(@PathVariable String slug) {
        return publico.perfil(slug);
    }

    @GetMapping("/disponibilidad")
    public DisponibilidadPublica disponibilidad(@PathVariable String slug, @RequestParam long prestacionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return publico.disponibilidad(slug, prestacionId, fecha);
    }

    @PostMapping("/turnos")
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaConfirmada reservar(@PathVariable String slug, @Valid @RequestBody SolicitudReserva solicitud) {
        return publico.reservar(slug, solicitud);
    }
}
