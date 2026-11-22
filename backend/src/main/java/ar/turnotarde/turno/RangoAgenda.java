package ar.turnotarde.turno;

import ar.turnotarde.shared.error.ErroresValidacion;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

record RangoAgenda(LocalDate desde, LocalDate hasta) {
    RangoAgenda {
        if (!hasta.isAfter(desde)) {
            throw ErroresValidacion.campo("hasta", "Elegí una fecha posterior a desde.");
        }
        if (ChronoUnit.DAYS.between(desde, hasta) > 31) {
            throw ErroresValidacion.campo("hasta", "Elegí un rango de hasta 31 días.");
        }
    }
}
