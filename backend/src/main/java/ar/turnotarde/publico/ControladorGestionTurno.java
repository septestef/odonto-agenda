package ar.turnotarde.publico;

import ar.turnotarde.turno.GestionTurnoService;
import ar.turnotarde.turno.GuardiaSolapamiento;
import ar.turnotarde.turno.TokenTurno;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/public/turnos")
public class ControladorGestionTurno {
    private final GestionTurnoService gestion;
    private final GuardiaSolapamiento guardia;

    public ControladorGestionTurno(GestionTurnoService gestion, GuardiaSolapamiento guardia) {
        this.gestion = gestion;
        this.guardia = guardia;
    }

    @GetMapping("/{token:" + TokenTurno.FORMATO + "}")
    public GestionTurnoService.Detalle consultar(@PathVariable String token) {
        return gestion.consultar(token);
    }

    @PostMapping("/{token}/cancelar")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@PathVariable String token) {
        gestion.cancelar(token);
    }

    @PostMapping(value = "/{token}/reprogramar", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reprogramar(@PathVariable String token, @Valid @RequestBody SolicitudReprogramacion solicitud) {
        guardia.ejecutar(() -> {
            gestion.reprogramar(token, solicitud.inicio().toInstant());
            return null;
        });
    }
}
