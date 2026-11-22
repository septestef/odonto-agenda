package ar.turnotarde.agenda;

import ar.turnotarde.agenda.dto.FranjaDto;
import ar.turnotarde.shared.reglas.ReglasAgenda;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * RN-AG-01 pura: día ISO válido, franja con {@code inicio < fin} y sin solapes dentro del mismo día.
 * No depende de Spring ni del reloj: solo mira el cuerpo enviado.
 */
public final class ValidadorFranjas {

    private ValidadorFranjas() {
    }

    public static void validar(List<FranjaDto> franjas) {
        List<Horario> horarios = new ArrayList<>(franjas.size());
        for (int indice = 0; indice < franjas.size(); indice++) {
            FranjaDto franja = franjas.get(indice);
            LocalTime inicio = hora(franja.inicio());
            LocalTime fin = hora(franja.fin());
            if (franja.diaSemana() < 1 || franja.diaSemana() > 7) {
                throw ReglasAgenda.franjaInvalida(indice, "Elegí un día entre el lunes (1) y el domingo (7).");
            }
            if (inicio == null || fin == null) {
                throw ReglasAgenda.franjaInvalida(indice, "Escribí las horas con el formato HH:mm.");
            }
            if (!inicio.isBefore(fin)) {
                throw ReglasAgenda.franjaInvalida(indice, "La franja tiene que empezar antes de terminar.");
            }
            horarios.add(new Horario(franja.diaSemana(), inicio, fin));
        }

        for (int primero = 0; primero < horarios.size(); primero++) {
            for (int segundo = primero + 1; segundo < horarios.size(); segundo++) {
                Horario a = horarios.get(primero);
                Horario b = horarios.get(segundo);
                if (a.dia() == b.dia() && a.inicio().isBefore(b.fin()) && b.inicio().isBefore(a.fin())) {
                    throw ReglasAgenda.franjasSolapadas(primero, segundo);
                }
            }
        }
    }

    private static LocalTime hora(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            return LocalTime.parse(texto);
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private record Horario(short dia, LocalTime inicio, LocalTime fin) {
    }
}
