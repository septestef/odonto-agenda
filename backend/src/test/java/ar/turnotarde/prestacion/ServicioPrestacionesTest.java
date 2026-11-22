package ar.turnotarde.prestacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.turnotarde.prestacion.dto.PeticionPrestacion;
import ar.turnotarde.prestacion.dto.PrestacionDto;
import ar.turnotarde.shared.error.ReglaNegocioException;
import ar.turnotarde.shared.seguridad.OdontologoActual;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

/** Duración RN-AG-03, nombres únicos por dueño, 404 uniforme y baja lógica (D5). */
@ExtendWith(MockitoExtension.class)
class ServicioPrestacionesTest {

    private static final long DUENO = 42L;
    private static final long ID = 7L;

    @Mock
    private PrestacionRepository prestaciones;
    @Mock
    private OdontologoActual actual;

    private ServicioPrestaciones servicio;

    @BeforeEach
    void preparar() {
        servicio = new ServicioPrestaciones(prestaciones, actual);
    }

    private static Prestacion prestacion(String nombre, int duracion) {
        Prestacion prestacion = new Prestacion(DUENO, nombre, (short) duracion);
        ReflectionTestUtils.setField(prestacion, "id", ID);
        return prestacion;
    }

    @Test
    void duracionesQueNoSonMultiploDe15SeRechazanSinTocarLaBase() {
        // La regla se aplica antes de resolver la sesión: no hace falta stubear actual.id().
        for (int duracion : List.of(20, 1, 50)) {
            assertThatThrownBy(() -> servicio.crear(new PeticionPrestacion("Consulta", duracion)))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                        assertThat(error.codigo()).isEqualTo("RN-AG-03");
                        assertThat(error.estado().value()).isEqualTo(422);
                    });
        }
        verifyNoInteractions(prestaciones);
    }

    @Test
    void duracionesValidasSeGuardanBajoElIdDeLaSesion() {
        when(actual.id()).thenReturn(DUENO);
        when(prestaciones.saveAndFlush(any(Prestacion.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        PrestacionDto treinta = servicio.crear(new PeticionPrestacion("Consulta", 30));
        PrestacionDto cuarentaYcinco = servicio.crear(new PeticionPrestacion("Limpieza", 45));

        assertThat(treinta.nombre()).isEqualTo("Consulta");
        assertThat(treinta.duracionMinutos()).isEqualTo(30);
        assertThat(treinta.activa()).isTrue();
        assertThat(cuarentaYcinco.duracionMinutos()).isEqualTo(45);
        ArgumentCaptor<Prestacion> captor = ArgumentCaptor.forClass(Prestacion.class);
        verify(prestaciones, org.mockito.Mockito.times(2)).saveAndFlush(captor.capture());
        assertThat(captor.getAllValues()).extracting(Prestacion::getOdontologoId)
                .containsExactly(DUENO, DUENO);
    }

    @Test
    void nombreRepetidoDelMismoDuenoSeRechazaCon400YCampoNombre() {
        when(actual.id()).thenReturn(DUENO);
        when(prestaciones.existsByOdontologoIdAndNombre(DUENO, "Consulta")).thenReturn(true);

        assertThatThrownBy(() -> servicio.crear(new PeticionPrestacion("Consulta", 30)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo("VALIDACION");
                    assertThat(error.estado().value()).isEqualTo(400);
                    assertThat(error.detalles().toString()).contains("nombre");
                });
        verify(prestaciones, never()).saveAndFlush(any(Prestacion.class));
    }

    @Test
    void laGuardiaTraduceSoloLaViolacion23505DeLaUnicaDeNombres() {
        when(actual.id()).thenReturn(DUENO);
        SQLException duplicado = new SQLException("prestacion_nombre_por_odontologo_uk", "23505", 23505);
        when(prestaciones.saveAndFlush(any(Prestacion.class)))
                .thenThrow(new DataIntegrityViolationException("conflicto", duplicado));

        assertThatThrownBy(() -> servicio.crear(new PeticionPrestacion("Consulta", 30)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo("VALIDACION");
                    assertThat(error.estado().value()).isEqualTo(400);
                });
    }

    @Test
    void otraViolacionDeBaseNoSeTraduceComoNombreRepetido() {
        when(actual.id()).thenReturn(DUENO);
        SQLException otra = new SQLException("prestacion_odontologo_fkey", "23503", 23503);
        DataIntegrityViolationException fallo = new DataIntegrityViolationException("conflicto", otra);
        when(prestaciones.saveAndFlush(any(Prestacion.class))).thenThrow(fallo);

        assertThatThrownBy(() -> servicio.crear(new PeticionPrestacion("Consulta", 30))).isSameAs(fallo);
    }

    @Test
    void unIdAjenoOInexistenteDaElMismo404() {
        when(actual.id()).thenReturn(DUENO);
        when(prestaciones.findByIdAndOdontologoId(ID, DUENO)).thenReturn(Optional.empty());

        for (int veces = 0; veces < 2; veces++) {
            assertThatThrownBy(() -> servicio.actualizar(ID, new PeticionPrestacion("Otra", 30)))
                    .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                        assertThat(error.codigo()).isEqualTo("NO_ENCONTRADO");
                        assertThat(error.estado().value()).isEqualTo(404);
                    });
        }
        verify(prestaciones, never()).saveAndFlush(any(Prestacion.class));
    }

    @Test
    void actualizarCambiaNombreYDuracionSinTocarTurnos() {
        when(actual.id()).thenReturn(DUENO);
        Prestacion existente = prestacion("Vieja", 30);
        when(prestaciones.findByIdAndOdontologoId(ID, DUENO)).thenReturn(Optional.of(existente));
        when(prestaciones.existsByOdontologoIdAndNombre(DUENO, "Nueva")).thenReturn(false);

        PrestacionDto resultado = servicio.actualizar(ID, new PeticionPrestacion("Nueva", 45));

        assertThat(resultado.nombre()).isEqualTo("Nueva");
        assertThat(resultado.duracionMinutos()).isEqualTo(45);
        assertThat(existente.getNombre()).isEqualTo("Nueva");
        assertThat(existente.getDuracionMinutos()).isEqualTo((short) 45);
        verify(prestaciones).flush();
        // ServicioPrestaciones no tiene TurnoRepository inyectado: no puede tocar turnos.
    }

    @Test
    void darDeBajaEsIdempotenteYConservaLaFila() {
        when(actual.id()).thenReturn(DUENO);
        Prestacion existente = prestacion("Consulta", 30);
        when(prestaciones.findByIdAndOdontologoId(ID, DUENO)).thenReturn(Optional.of(existente));

        servicio.darDeBaja(ID);
        servicio.darDeBaja(ID);

        assertThat(existente.isActiva()).isFalse();
        verify(prestaciones, org.mockito.Mockito.times(2)).flush();
        verify(prestaciones, never()).delete(any(Prestacion.class));
    }

    @Test
    void listarIncluyeInactivasDelDuenoDeLaSesion() {
        when(actual.id()).thenReturn(DUENO);
        when(prestaciones.findByOdontologoIdOrderByNombre(DUENO)).thenReturn(List.of(
                prestacion("Activa", 30), prestacion("Inactiva", 45)));

        assertThat(servicio.listar()).extracting(PrestacionDto::nombre)
                .containsExactly("Activa", "Inactiva");
    }
}
