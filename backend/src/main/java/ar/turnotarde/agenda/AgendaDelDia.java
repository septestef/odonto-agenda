package ar.turnotarde.agenda;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Todo lo que la calculadora necesita saber de un día: franjas locales, y bloqueos y turnos activos
 * que lo tocan, como instantes.
 */
public record AgendaDelDia(LocalDate fecha, ZoneId zona, List<Franja> franjas, List<Intervalo> bloqueos,
        List<Intervalo> turnos) {
}
