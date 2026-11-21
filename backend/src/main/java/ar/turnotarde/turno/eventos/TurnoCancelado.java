package ar.turnotarde.turno.eventos;

import ar.turnotarde.turno.CanceladoPor;
import java.time.Instant;

/** Se publica dentro de la transacción de la cancelación (C-09, C-13). No lleva token: ya no sirve. */
public record TurnoCancelado(ResumenTurno turno, CanceladoPor canceladoPor, Instant canceladoEn) {
}
