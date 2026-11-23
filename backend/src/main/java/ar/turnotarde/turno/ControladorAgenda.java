package ar.turnotarde.turno;

import ar.turnotarde.publico.ReservaConfirmada;
import ar.turnotarde.turno.dto.SolicitudTurnoManual;
import ar.turnotarde.turno.dto.TurnoAgendaRespuesta;
import jakarta.validation.Valid;
import java.beans.PropertyEditorSupport;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/turnos")
public class ControladorAgenda {
    private final ConsultaAgendaService agenda;
    private final TurnoManualService turnosManuales;

    public ControladorAgenda(ConsultaAgendaService agenda, TurnoManualService turnosManuales) {
        this.agenda = agenda;
        this.turnosManuales = turnosManuales;
    }

    @InitBinder
    void fechasIsoLocales(WebDataBinder binder) {
        // ISO.DATE de Spring admite offsets; este contrato acepta solamente la fecha local.
        binder.registerCustomEditor(LocalDate.class, new PropertyEditorSupport() {
            @Override
            public void setAsText(String texto) {
                try {
                    setValue(LocalDate.parse(texto));
                } catch (DateTimeParseException e) {
                    throw new IllegalArgumentException("Fecha ISO local inválida.");
                }
            }
        });
    }

    @GetMapping
    public List<TurnoAgendaRespuesta> consultar(
            @RequestParam LocalDate desde, @RequestParam LocalDate hasta) {
        return agenda.consultar(desde, hasta);
    }

    /** C-15: alta manual por el odontólogo autenticado; el dueño nunca viene del cuerpo (RN-SE-01). */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ReservaConfirmada crear(@Valid @RequestBody SolicitudTurnoManual solicitud) {
        return turnosManuales.crear(solicitud);
    }
}
