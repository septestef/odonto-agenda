package ar.turnotarde.agenda;

import java.time.Instant;

/** Intervalo semiabierto {@code [inicio, fin)} en instantes absolutos (bloqueos y turnos que ocupan lugar). */
public record Intervalo(Instant inicio, Instant fin) {

    /** Dos intervalos semiabiertos se cruzan si cada uno empieza antes de que termine el otro (contiguos no). */
    public boolean cruza(Instant otroInicio, Instant otroFin) {
        return inicio.isBefore(otroFin) && otroInicio.isBefore(fin);
    }
}
