package ar.turnotarde.agenda;

import java.time.LocalTime;

/** Franja de atención en hora local del consultorio: {@code [inicio, fin]} dentro del día. */
public record Franja(LocalTime inicio, LocalTime fin) {
}
