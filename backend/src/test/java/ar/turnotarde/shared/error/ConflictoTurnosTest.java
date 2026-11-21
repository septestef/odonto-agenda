package ar.turnotarde.shared.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;

class ConflictoTurnosTest {

    private static final String MENSAJE_EXCLUSION =
            "ERROR: conflicting key value violates exclusion constraint \"turno_sin_solapamiento\"";

    @Test
    void exclusionDeTurnoSinSolapamientoEnLaCadenaDeCausasEsSolapamiento() {
        Throwable e = new DataIntegrityViolationException("no se pudo ejecutar",
                new RuntimeException("envoltorio", new SQLException(MENSAJE_EXCLUSION, "23P01")));

        assertThat(ConflictoTurnos.esSolapamiento(e)).isTrue();
    }

    @Test
    void exclusionDeOtraConstraintNoEsSolapamiento() {
        Throwable e = new DataIntegrityViolationException("x",
                new SQLException("violates exclusion constraint \"otra_exclusion\"", "23P01"));

        assertThat(ConflictoTurnos.esSolapamiento(e)).isFalse();
    }

    @Test
    void violacionDeUnicidadNoEsSolapamientoAunqueNombreLaConstraint() {
        Throwable e = new DataIntegrityViolationException("x",
                new SQLException("duplicate key value violates unique constraint \"turno_sin_solapamiento\"",
                        "23505"));

        assertThat(ConflictoTurnos.esSolapamiento(e)).isFalse();
    }

    @Test
    void excepcionSinSqlExceptionNoEsSolapamiento() {
        assertThat(ConflictoTurnos.esSolapamiento(new IllegalStateException("turno_sin_solapamiento"))).isFalse();
    }

    @Test
    void sqlExceptionEncadenadaPorSiguienteExcepcionTambienSeDetecta() {
        SQLException primera = new SQLException("otra cosa", "00000");
        primera.setNextException(new SQLException(MENSAJE_EXCLUSION, "23P01"));
        Throwable e = new DataIntegrityViolationException("x", primera);

        assertThat(ConflictoTurnos.esSolapamiento(e)).isTrue();
    }

    @Test
    void fallasDeLockSeReconocenPorTipoDeSpringOPorSqlState() {
        assertThat(ConflictoTurnos.esFallaDeLock(new CannotAcquireLockException("lock"))).isTrue();
        assertThat(ConflictoTurnos.esFallaDeLock(new PessimisticLockingFailureException("lock"))).isTrue();
        assertThat(ConflictoTurnos.esFallaDeLock(new RuntimeException("x", new SQLException("deadlock", "40P01"))))
                .isTrue();
        assertThat(ConflictoTurnos.esFallaDeLock(new RuntimeException("x", new SQLException("no lock", "55P03"))))
                .isTrue();
    }

    @Test
    void otrasExcepcionesNoSonFallaDeLock() {
        assertThat(ConflictoTurnos.esFallaDeLock(new RuntimeException("x", new SQLException("dup", "23505"))))
                .isFalse();
        assertThat(ConflictoTurnos.esFallaDeLock(new IllegalStateException("x"))).isFalse();
    }
}
