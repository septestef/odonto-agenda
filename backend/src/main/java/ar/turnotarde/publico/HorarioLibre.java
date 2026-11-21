package ar.turnotarde.publico;

import java.time.OffsetDateTime;

/** Un inicio libre: el instante con el offset del negocio y la hora local {@code HH:mm}. */
public record HorarioLibre(OffsetDateTime inicio, String hora) {
}
