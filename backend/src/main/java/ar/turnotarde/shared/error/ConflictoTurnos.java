package ar.turnotarde.shared.error;

import java.sql.SQLException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.dao.PessimisticLockingFailureException;

/**
 * Detecta, por SQLState y nombre de constraint (el driver es {@code runtimeOnly}, no se compila
 * contra {@code PSQLException}), los fallos de base que significan "ese horario ya no está" (D8).
 */
public final class ConflictoTurnos {

    private static final String EXCLUSION = "23P01";
    private static final String DEADLOCK = "40P01";
    private static final String LOCK_NO_DISPONIBLE = "55P03";
    private static final String CONSTRAINT = "turno_sin_solapamiento";

    private ConflictoTurnos() {
    }

    /** Violación de exclusión ({@code 23P01}) de la constraint {@code turno_sin_solapamiento}. */
    public static boolean esSolapamiento(Throwable error) {
        return recorrer(error, sql -> EXCLUSION.equals(sql.getSQLState())
                && sql.getMessage() != null && sql.getMessage().contains(CONSTRAINT));
    }

    /** Deadlock ({@code 40P01}), lock no disponible ({@code 55P03}) o falla de lock traducida por Spring. */
    public static boolean esFallaDeLock(Throwable error) {
        Set<Throwable> vistos = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable actual = error; actual != null && vistos.add(actual); actual = actual.getCause()) {
            if (actual instanceof PessimisticLockingFailureException) {
                return true;
            }
        }
        return recorrer(error, sql -> DEADLOCK.equals(sql.getSQLState())
                || LOCK_NO_DISPONIBLE.equals(sql.getSQLState()));
    }

    private static boolean recorrer(Throwable error, Predicate<SQLException> criterio) {
        Set<Throwable> vistos = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable actual = error; actual != null && vistos.add(actual); actual = actual.getCause()) {
            if (actual instanceof SQLException sql) {
                Set<Throwable> encadenadas = Collections.newSetFromMap(new IdentityHashMap<>());
                for (SQLException s = sql; s != null && encadenadas.add(s); s = s.getNextException()) {
                    if (criterio.test(s)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
