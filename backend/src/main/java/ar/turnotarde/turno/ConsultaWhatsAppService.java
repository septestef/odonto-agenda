package ar.turnotarde.turno;

import ar.turnotarde.shared.seguridad.AislamientoOdontologo;
import ar.turnotarde.shared.seguridad.OdontologoActual;
import ar.turnotarde.turno.dto.WhatsAppRespuesta;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ConsultaWhatsAppService {
    private final TurnoRepository turnos;
    private final OdontologoActual odontologo;
    private final MensajeWhatsApp mensaje;

    ConsultaWhatsAppService(TurnoRepository turnos, OdontologoActual odontologo, MensajeWhatsApp mensaje) {
        this.turnos = turnos;
        this.odontologo = odontologo;
        this.mensaje = mensaje;
    }

    @Transactional(readOnly = true)
    public WhatsAppRespuesta consultar(long id) {
        var turno = AislamientoOdontologo.exigir(turnos.whatsappPropio(id, odontologo.id()));
        return new WhatsAppRespuesta(mensaje.preparar(turno.estado(), turno.inicio(), turno.telefono(),
                turno.prestacion(), turno.direccionConsultorio()));
    }
}
