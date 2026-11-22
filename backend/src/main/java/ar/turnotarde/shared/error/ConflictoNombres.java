package ar.turnotarde.shared.error;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Detecta la violación de {@code prestacion_nombre_por_odontologo_uk} por SQLState y nombre de
 * constraint, igual que {@link ConflictoTurnos} lo hace con el solape de turnos (D5).
 */
public final class ConflictoNombres {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String CONSTRAINT = "prestacion_nombre_por_odontologo_uk";

    private ConflictoNombres() {
    }

    public static boolean esNombreRepetido(Throwable error) {
        Set<Throwable> vistos = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable actual = error; actual != null && vistos.add(actual); actual = actual.getCause()) {
            if (actual instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState())
                    && sql.getMessage() != null && sql.getMessage().contains(CONSTRAINT)) {
                return true;
            }
        }
        return false;
    }
}
