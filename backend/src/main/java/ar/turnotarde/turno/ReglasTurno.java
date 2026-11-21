package ar.turnotarde.turno;

import ar.turnotarde.shared.error.ReglaNegocioException;
import org.springframework.http.HttpStatus;

/** Fábrica de los rechazos de reglas de negocio de turnos con su código, estado y mensaje (D4). */
public final class ReglasTurno {

    public static final String HORARIO_TOMADO = "RN-TU-01";
    public static final String LIMITE_DE_TURNOS = "RN-PA-03";
    public static final String ANTICIPACION = "RN-TU-03";
    public static final String HORIZONTE = "RN-TU-11";
    public static final String FUERA_DE_AGENDA = "RN-AG-05";

    private ReglasTurno() {
    }

    public static ReglaNegocioException horarioTomado() {
        return new ReglaNegocioException(HORARIO_TOMADO, "Ese horario acaba de ser tomado, elegí otro",
                HttpStatus.CONFLICT);
    }

    public static ReglaNegocioException limiteDeTurnos() {
        return new ReglaNegocioException(LIMITE_DE_TURNOS,
                "Ya tenés 2 turnos reservados en este consultorio. Cancelá uno desde el link del email para sacar otro.",
                HttpStatus.CONFLICT);
    }

    public static ReglaNegocioException anticipacionInsuficiente() {
        return new ReglaNegocioException(ANTICIPACION,
                "Los turnos se reservan con al menos 24 horas de anticipación. Elegí otro horario.",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static ReglaNegocioException fueraDeHorizonte(int maxDias) {
        return new ReglaNegocioException(HORIZONTE,
                "Todavía no se puede reservar para esa fecha. Elegí un día dentro de los próximos " + maxDias
                        + " días.",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static ReglaNegocioException fueraDeAgenda() {
        return new ReglaNegocioException(FUERA_DE_AGENDA, "Ese horario no está disponible. Elegí uno de la lista.",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
