package ar.turnotarde.turno;

import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/turnos")
public class ControladorCancelacionOdontologo {
    private final CancelacionOdontologoService cancelacion;
    private final GuardiaSolapamiento guardia;

    public ControladorCancelacionOdontologo(CancelacionOdontologoService cancelacion, GuardiaSolapamiento guardia) {
        this.cancelacion = cancelacion;
        this.guardia = guardia;
    }

    @PostMapping("/{id}/cancelar")
    public ResponseEntity<Void> cancelar(@PathVariable @Positive(message = "Ingresá un identificador mayor a 0.") long id) {
        // El proxy transaccional confirma antes de retornar: la guardia cubre también el commit.
        guardia.ejecutar(() -> { cancelacion.cancelar(id); return null; });
        return ResponseEntity.noContent().build();
    }
}
