package ar.turnotarde.turno;

import ar.turnotarde.turno.dto.TurnoAgendaRespuesta;
import java.beans.PropertyEditorSupport;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/turnos")
public class ControladorAgenda {
    private final ConsultaAgendaService agenda;

    public ControladorAgenda(ConsultaAgendaService agenda) {
        this.agenda = agenda;
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
}
