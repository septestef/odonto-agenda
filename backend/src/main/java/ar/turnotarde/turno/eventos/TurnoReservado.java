package ar.turnotarde.turno.eventos;

import ar.turnotarde.turno.OrigenTurno;

/**
 * Se publica dentro de la transacción de la reserva; C-08 lo consume con
 * {@code @TransactionalEventListener(AFTER_COMMIT)}. {@code token} es el token de gestión en claro:
 * existe solo en memoria y {@link #toString()} lo oculta.
 */
public record TurnoReservado(ResumenTurno turno, OrigenTurno origen, String token) {

    @Override
    public String toString() {
        return "TurnoReservado[turno=" + turno + ", origen=" + origen + ", token=***]";
    }
}
