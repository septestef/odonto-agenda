package ar.turnotarde.agenda;

import ar.turnotarde.agenda.dto.HorarioDto;
import ar.turnotarde.agenda.dto.PeticionHorario;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Configuración del horario semanal. Delgado: el dueño y las reglas los resuelve el servicio. */
@RestController
@RequestMapping("/api/horarios")
public class ControladorHorarios {

    private final ServicioHorarios servicio;

    public ControladorHorarios(ServicioHorarios servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public HorarioDto listar() {
        return servicio.listar();
    }

    @PutMapping
    public HorarioDto reemplazar(@Valid @RequestBody PeticionHorario peticion) {
        return servicio.reemplazar(peticion);
    }
}
