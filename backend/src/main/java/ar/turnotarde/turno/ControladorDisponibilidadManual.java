package ar.turnotarde.turno;

import ar.turnotarde.publico.DisponibilidadPublica;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** C-15: disponibilidad del consultorio para el odontólogo autenticado, con el contrato público (D3). */
@RestController
@RequestMapping("/api/disponibilidad")
public class ControladorDisponibilidadManual {

    private final TurnoManualService turnosManuales;

    public ControladorDisponibilidadManual(TurnoManualService turnosManuales) {
        this.turnosManuales = turnosManuales;
    }

    @GetMapping
    public DisponibilidadPublica consultar(@RequestParam long prestacionId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return turnosManuales.disponibilidad(prestacionId, fecha);
    }
}
