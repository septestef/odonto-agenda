package ar.turnotarde.notificacion;

import ar.turnotarde.turno.TurnoRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
class RegistroNotificacion {
    private final TurnoRepository turnos;
    private final NotificacionRepository notificaciones;

    RegistroNotificacion(TurnoRepository turnos, NotificacionRepository notificaciones) {
        this.turnos = turnos;
        this.notificaciones = notificaciones;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(long turnoId, TipoNotificacion tipo, String destinatario, Instant intento,
            ErrorNotificacion error) {
        var aviso = new Notificacion(turnos.getReferenceById(turnoId), tipo, destinatario, intento,
                error == null ? EstadoNotificacion.ENVIADO : EstadoNotificacion.ERROR,
                error == null ? null : error.name());
        notificaciones.insertarSinDiagnosticoJdbc(aviso);
    }
}
