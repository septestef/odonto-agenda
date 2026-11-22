package ar.turnotarde.auth.dto;

import java.time.Instant;

/** Detalle del 429 `RN-AU-03`: segundos que faltan e instante exacto de desbloqueo. */
public record DetalleBloqueo(long reintentarEnSegundos, Instant bloqueadoHasta) {
}
