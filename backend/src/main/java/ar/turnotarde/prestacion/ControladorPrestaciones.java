package ar.turnotarde.prestacion;

import ar.turnotarde.prestacion.dto.ListaPrestacionesDto;
import ar.turnotarde.prestacion.dto.PeticionPrestacion;
import ar.turnotarde.prestacion.dto.PrestacionDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Configuración de prestaciones. Delgado: el dueño sale de la sesión, nunca del request. */
@RestController
@RequestMapping("/api/prestaciones")
public class ControladorPrestaciones {

    private final ServicioPrestaciones servicio;

    public ControladorPrestaciones(ServicioPrestaciones servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public ListaPrestacionesDto listar() {
        return new ListaPrestacionesDto(servicio.listar());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PrestacionDto crear(@Valid @RequestBody PeticionPrestacion peticion) {
        return servicio.crear(peticion);
    }

    @PutMapping("/{id}")
    public PrestacionDto actualizar(@PathVariable long id, @Valid @RequestBody PeticionPrestacion peticion) {
        return servicio.actualizar(id, peticion);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void darDeBaja(@PathVariable long id) {
        servicio.darDeBaja(id);
    }
}
