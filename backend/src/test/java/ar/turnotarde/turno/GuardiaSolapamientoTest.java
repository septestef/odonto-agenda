package ar.turnotarde.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnotarde.shared.error.ReglaNegocioException;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;

class GuardiaSolapamientoTest {

    private final GuardiaSolapamiento guardia = new GuardiaSolapamiento();

    private static void esHorarioTomado(Throwable t) {
        assertThat(t).isInstanceOf(ReglaNegocioException.class);
        ReglaNegocioException regla = (ReglaNegocioException) t;
        assertThat(regla.codigo()).isEqualTo("RN-TU-01");
        assertThat(regla.estado()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(regla.getMessage()).isEqualTo("Ese horario acaba de ser tomado, elegí otro");
    }

    @Test
    void devuelveElResultadoCuandoLaEscrituraSale() {
        assertThat(guardia.ejecutar(() -> "ok")).isEqualTo("ok");
    }

    @Test
    void exclusionDeSolapamientoSeTraduceARnTu01() {
        assertThatThrownBy(() -> guardia.ejecutar(() -> {
            throw new DataIntegrityViolationException("x",
                    new SQLException("violates exclusion constraint \"turno_sin_solapamiento\"", "23P01"));
        })).satisfies(GuardiaSolapamientoTest::esHorarioTomado);
    }

    @Test
    void fallasDeLockYDeadlockSeTraducenARnTu01() {
        assertThatThrownBy(() -> guardia.ejecutar(() -> {
            throw new CannotAcquireLockException("lock");
        })).satisfies(GuardiaSolapamientoTest::esHorarioTomado);
        assertThatThrownBy(() -> guardia.ejecutar(() -> {
            throw new RuntimeException("x", new SQLException("deadlock detected", "40P01"));
        })).satisfies(GuardiaSolapamientoTest::esHorarioTomado);
        assertThatThrownBy(() -> guardia.ejecutar(() -> {
            throw new RuntimeException("x", new SQLException("lock not available", "55P03"));
        })).satisfies(GuardiaSolapamientoTest::esHorarioTomado);
    }

    @Test
    void otraExclusionYUnicidadSeRelanzanIntactas() {
        DataIntegrityViolationException otraExclusion = new DataIntegrityViolationException("x",
                new SQLException("violates exclusion constraint \"otra\"", "23P01"));
        DataIntegrityViolationException unicidad = new DataIntegrityViolationException("x",
                new SQLException("duplicate key", "23505"));

        assertThatThrownBy(() -> guardia.ejecutar(() -> {
            throw otraExclusion;
        })).isSameAs(otraExclusion);
        assertThatThrownBy(() -> guardia.ejecutar(() -> {
            throw unicidad;
        })).isSameAs(unicidad);
    }
}
