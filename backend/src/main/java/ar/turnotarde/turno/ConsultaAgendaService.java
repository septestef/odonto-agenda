package ar.turnotarde.turno;

import ar.turnotarde.paciente.dto.PacienteRespuesta;
import ar.turnotarde.shared.seguridad.OdontologoActual;
import ar.turnotarde.shared.tiempo.ZonaNegocio;
import ar.turnotarde.turno.dto.TurnoAgendaRespuesta;
import ar.turnotarde.turno.dto.TurnoAgendaRespuesta.PrestacionAgendaRespuesta;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultaAgendaService {
    private final TurnoRepository turnos;
    private final OdontologoActual odontologo;
    private final ZonaNegocio zona;

    public ConsultaAgendaService(TurnoRepository turnos, OdontologoActual odontologo, ZonaNegocio zona) {
        this.turnos = turnos;
        this.odontologo = odontologo;
        this.zona = zona;
    }

    @Transactional(readOnly = true)
    public List<TurnoAgendaRespuesta> consultar(LocalDate desde, LocalDate hasta) {
        var rango = new RangoAgenda(desde, hasta);
        return turnos.agenda(odontologo.id(), rango.desde().atStartOfDay(zona.zona()).toInstant(),
                        rango.hasta().atStartOfDay(zona.zona()).toInstant())
                .stream().map(this::aDto).toList();
    }

    private TurnoAgendaRespuesta aDto(TurnoAgendaProyeccion t) {
        return new TurnoAgendaRespuesta(t.id(), zona.aLocal(t.inicio()).toOffsetDateTime(),
                zona.aLocal(t.fin()).toOffsetDateTime(), t.estado(), t.origen(),
                new PacienteRespuesta(t.pacienteId(), t.dni(), t.nombre(), t.apellido(), t.telefono(), t.email()),
                new PrestacionAgendaRespuesta(t.prestacionId(), t.prestacionNombre(),
                        Duration.between(t.inicio(), t.fin()).toMinutes()),
                t.canceladoPor(), t.canceladoEn() == null ? null : zona.aLocal(t.canceladoEn()).toOffsetDateTime());
    }
}
