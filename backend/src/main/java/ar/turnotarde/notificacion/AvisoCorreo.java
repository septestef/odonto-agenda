package ar.turnotarde.notificacion;

import ar.turnotarde.shared.correo.BuzonCorreo;
import ar.turnotarde.turno.eventos.ResumenTurno;
import ar.turnotarde.turno.eventos.TurnoCancelado;
import ar.turnotarde.turno.eventos.TurnoReprogramado;
import ar.turnotarde.turno.eventos.TurnoReservado;
import java.util.Optional;

record AvisoCorreo(TipoNotificacion tipo, ResumenTurno turno, String token) {
    static Optional<AvisoCorreo> desde(TurnoReservado evento) {
        return candidato(TipoNotificacion.CONFIRMACION, evento.turno(), evento.token());
    }

    static Optional<AvisoCorreo> desde(TurnoCancelado evento) {
        return candidato(TipoNotificacion.CANCELACION, evento.turno(), null);
    }

    static Optional<AvisoCorreo> desde(TurnoReprogramado evento) {
        return candidato(TipoNotificacion.REPROGRAMACION, evento.nuevo(), evento.token());
    }

    private static Optional<AvisoCorreo> candidato(TipoNotificacion tipo, ResumenTurno turno, String token) {
        return BuzonCorreo.admisible(turno.pacienteEmail())
                ? Optional.of(new AvisoCorreo(tipo, turno, token)) : Optional.empty();
    }

    String destinatario() {
        return turno.pacienteEmail();
    }

    String asunto() {
        return switch (tipo) {
            case CONFIRMACION -> "Confirmaste tu turno";
            case CANCELACION -> "Cancelamos tu turno";
            case REPROGRAMACION -> "Reprogramamos tu turno";
            case RECORDATORIO -> throw new IllegalStateException("RENDER");
        };
    }

    @Override
    public String toString() {
        return "AvisoCorreo[" + tipo + "]";
    }
}
