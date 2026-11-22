package ar.turnotarde.soporte.prueba;

import ar.turnotarde.prestacion.PrestacionRepository;
import ar.turnotarde.shared.seguridad.AislamientoOdontologo;
import ar.turnotarde.shared.seguridad.OdontologoActual;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador de tests para RN-SE-01: la consulta se filtra por la sesión y el parámetro
 * {@code odontologoId} se recibe a propósito para demostrar que no tiene efecto.
 */
@RestController
public class ControladorAislamientoDePrueba {

    private final PrestacionRepository prestaciones;
    private final OdontologoActual odontologoActual;

    public ControladorAislamientoDePrueba(PrestacionRepository prestaciones,
            OdontologoActual odontologoActual) {
        this.prestaciones = prestaciones;
        this.odontologoActual = odontologoActual;
    }

    public record PrestacionPruebaDto(Long id, String nombre) {
    }

    @GetMapping("/api/prueba/aislamiento/prestacion/{id}")
    public PrestacionPruebaDto prestacion(@PathVariable Long id,
            @RequestParam(required = false) Long odontologoId) {
        var prestacion = AislamientoOdontologo.exigir(
                prestaciones.findByIdAndOdontologoIdAndActivaTrue(id, odontologoActual.id()));
        return new PrestacionPruebaDto(prestacion.getId(), prestacion.getNombre());
    }
}
