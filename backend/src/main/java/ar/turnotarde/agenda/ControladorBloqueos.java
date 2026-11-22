package ar.turnotarde.agenda;

import ar.turnotarde.agenda.dto.BloqueoDto;
import ar.turnotarde.agenda.dto.ListaBloqueosDto;
import ar.turnotarde.agenda.dto.PeticionBloqueo;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Configuración de bloqueos. Los filtros de fecha son opcionales y viajan con offset. */
@RestController
@RequestMapping("/api/bloqueos")
public class ControladorBloqueos {

    private final ServicioBloqueos servicio;

    public ControladorBloqueos(ServicioBloqueos servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public ListaBloqueosDto listar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime hasta) {
        return new ListaBloqueosDto(servicio.listar(desde, hasta));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BloqueoDto crear(@Valid @RequestBody PeticionBloqueo peticion) {
        return servicio.crear(peticion);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void borrar(@PathVariable long id) {
        servicio.borrar(id);
    }
}
