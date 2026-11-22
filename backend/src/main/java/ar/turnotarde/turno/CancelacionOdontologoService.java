package ar.turnotarde.turno;

import ar.turnotarde.odontologo.OdontologoRepository;
import ar.turnotarde.shared.correo.BuzonCorreo;
import ar.turnotarde.shared.seguridad.AislamientoOdontologo;
import ar.turnotarde.shared.seguridad.OdontologoActual;
import ar.turnotarde.shared.tiempo.ZonaNegocio;
import ar.turnotarde.turno.eventos.ResumenTurno;
import ar.turnotarde.turno.eventos.TurnoCancelado;
import java.time.Duration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancelacionOdontologoService {
    private final TurnoRepository turnos;
    private final OdontologoRepository odontologos;
    private final OdontologoActual odontologo;
    private final ZonaNegocio zona;
    private final ApplicationEventPublisher publicador;

    public CancelacionOdontologoService(TurnoRepository turnos, OdontologoRepository odontologos,
            OdontologoActual odontologo, ZonaNegocio zona, ApplicationEventPublisher publicador) {
        this.turnos = turnos;
        this.odontologos = odontologos;
        this.odontologo = odontologo;
        this.zona = zona;
        this.publicador = publicador;
    }

    @Transactional
    public void cancelar(long id) {
        long dueno = odontologo.id();
        Turno turno = AislamientoOdontologo.exigir(turnos.bloquearPropio(id, dueno));
        var ahora = zona.ahora();
        if (!ReglasCancelacionOdontologo.debeCancelar(turno.getEstado(), turno.getInicio(), ahora)) {
            return;
        }
        turno.cancelarPorOdontologo(ahora);
        turnos.flush();
        // Lecturas ordinarias posteriores, nunca lock/upsert de paciente ni advisory.
        var paciente = turno.getPaciente();
        var prestacion = turno.getPrestacion();
        var profesional = AislamientoOdontologo.exigir(odontologos.findById(dueno));
        String email = turno.getEmailGestion();
        var resumen = new ResumenTurno(turno.getId(), dueno, turno.getInicio(), turno.getFin(),
                prestacion.getId(), prestacion.getNombre(),
                Math.toIntExact(Duration.between(turno.getInicio(), turno.getFin()).toMinutes()),
                paciente.getNombre(), paciente.getApellido(), BuzonCorreo.admisible(email) ? email : null,
                paciente.getTelefono(), profesional.getNombre(), profesional.getApellido(),
                profesional.getDireccionConsultorio());
        publicador.publishEvent(new TurnoCancelado(resumen, CanceladoPor.ODONTOLOGO, ahora));
    }
}
