package ar.turnotarde.agenda;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;

/**
 * Función pura que decide qué inicios son reservables (D2). La disponibilidad y la reserva usan el mismo
 * predicado ({@link #evaluar}) para no divergir. No usa reloj ni repositorios: todo entra por parámetro.
 * Aritmética de candidatos en minutos enteros y comparaciones entre {@link Instant}.
 */
public final class CalculadoraDisponibilidad {

    private static final int PASO_MINUTOS = 15;

    private CalculadoraDisponibilidad() {
    }

    /** Inicios libres de la fecha de la agenda, ordenados y sin duplicados. */
    public static List<Instant> horariosLibres(AgendaDelDia agenda, Duration duracion, Instant ahora,
            Duration anticipacionMinima, LocalDate hoy, int maxDias) {
        NavigableSet<Instant> candidatos = candidatos(agenda, duracion);
        return candidatos.stream()
                .filter(inicio -> evaluar(inicio, agenda, candidatos, duracion, ahora, anticipacionMinima, hoy,
                        maxDias) == MotivoRechazo.NINGUNO)
                .toList();
    }

    /** Motivo por el que {@code inicio} no se puede reservar, o {@code NINGUNO}. */
    public static MotivoRechazo evaluar(Instant inicio, AgendaDelDia agenda, Duration duracion, Instant ahora,
            Duration anticipacionMinima, LocalDate hoy, int maxDias) {
        return evaluar(inicio, agenda, candidatos(agenda, duracion), duracion, ahora, anticipacionMinima, hoy,
                maxDias);
    }

    private static MotivoRechazo evaluar(Instant inicio, AgendaDelDia agenda, NavigableSet<Instant> candidatos,
            Duration duracion, Instant ahora, Duration anticipacionMinima, LocalDate hoy, int maxDias) {
        if (inicio.isBefore(ahora.plus(anticipacionMinima))) {
            return MotivoRechazo.ANTICIPACION;
        }
        if (fechaLocal(inicio, agenda.zona()).isAfter(hoy.plusDays(maxDias))) {
            return MotivoRechazo.HORIZONTE;
        }
        Instant fin = inicio.plus(duracion);
        if (!candidatos.contains(inicio) || cruzaAlguno(agenda.bloqueos(), inicio, fin)) {
            return MotivoRechazo.FUERA_DE_AGENDA;
        }
        if (cruzaAlguno(agenda.turnos(), inicio, fin)) {
            return MotivoRechazo.OCUPADO;
        }
        return MotivoRechazo.NINGUNO;
    }

    /**
     * Inicios posibles: desde el comienzo de cada franja, de a 15 minutos, mientras el turno completo termine
     * a la hora de cierre o antes. En un salto de hora un candidato inexistente se corre y uno repetido se
     * deduplica (el {@link TreeSet}).
     */
    private static NavigableSet<Instant> candidatos(AgendaDelDia agenda, Duration duracion) {
        NavigableSet<Instant> resultado = new TreeSet<>();
        long minutosDuracion = duracion.toMinutes();
        for (Franja franja : agenda.franjas()) {
            int desde = minutosDelDia(franja.inicio());
            int hasta = minutosDelDia(franja.fin());
            Instant cierre = instante(agenda.fecha(), franja.fin(), agenda.zona());
            for (long m = desde; m + minutosDuracion <= hasta; m += PASO_MINUTOS) {
                Instant inicio = instante(agenda.fecha(), LocalTime.of((int) (m / 60), (int) (m % 60)),
                        agenda.zona());
                if (!inicio.plus(duracion).isAfter(cierre)) {
                    resultado.add(inicio);
                }
            }
        }
        return resultado;
    }

    private static boolean cruzaAlguno(List<Intervalo> intervalos, Instant inicio, Instant fin) {
        return intervalos.stream().anyMatch(i -> i.cruza(inicio, fin));
    }

    private static int minutosDelDia(LocalTime hora) {
        return hora.getHour() * 60 + hora.getMinute();
    }

    private static Instant instante(LocalDate fecha, LocalTime hora, ZoneId zona) {
        return ZonedDateTime.of(fecha, hora, zona).toInstant();
    }

    private static LocalDate fechaLocal(Instant instante, ZoneId zona) {
        return LocalDate.ofInstant(instante, zona);
    }
}
