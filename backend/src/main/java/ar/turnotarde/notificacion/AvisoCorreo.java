package ar.turnotarde.notificacion;

import ar.turnotarde.turno.eventos.ResumenTurno;
import ar.turnotarde.turno.eventos.TurnoCancelado;
import ar.turnotarde.turno.eventos.TurnoReprogramado;
import ar.turnotarde.turno.eventos.TurnoReservado;
import java.util.Optional;
import java.util.regex.Pattern;

record AvisoCorreo(TipoNotificacion tipo, ResumenTurno turno, String token) {
    // Solo un buzón: sin display name, comentarios, listas ni espacios de encabezado.
    private static final String ATOMO = "[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+";
    private static final String ETIQUETA = "[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?";
    private static final Pattern BUZON = Pattern.compile(ATOMO + "(?:\\." + ATOMO + ")*@"
            + ETIQUETA + "(?:\\." + ETIQUETA + ")+");

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
        return buzonValido(turno.pacienteEmail()) ? Optional.of(new AvisoCorreo(tipo, turno, token)) : Optional.empty();
    }

    static boolean buzonValido(String email) {
        return email != null && email.length() <= 254 && email.indexOf('@') <= 64 && BUZON.matcher(email).matches();
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
