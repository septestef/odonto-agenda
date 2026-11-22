package ar.turnotarde.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.turnotarde.agenda.dto.FranjaDto;
import ar.turnotarde.agenda.dto.HorarioDto;
import ar.turnotarde.agenda.dto.PeticionHorario;
import ar.turnotarde.shared.error.ReglaNegocioException;
import ar.turnotarde.shared.seguridad.OdontologoActual;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** El dueño siempre es el de la sesión y la validación ocurre antes de borrar (D4). */
@ExtendWith(MockitoExtension.class)
class ServicioHorariosTest {

    private static final long DUENO = 42L;

    @Mock
    private HorarioAtencionRepository horarios;
    @Mock
    private OdontologoActual actual;

    private ServicioHorarios servicio;

    @BeforeEach
    void preparar() {
        servicio = new ServicioHorarios(horarios, actual);
    }

    @Test
    void unaFranjaInvalidaNoBorraElHorarioAnterior() {
        when(actual.id()).thenReturn(DUENO);

        assertThatThrownBy(() -> servicio.reemplazar(new PeticionHorario(
                List.of(new FranjaDto((short) 1, "13:00", "09:00")))))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo("RN-AG-01");
                    assertThat(error.estado().value()).isEqualTo(422);
                });

        verifyNoInteractions(horarios);
    }

    @Test
    void dosFranjasSolapadasTampocoBorranNada() {
        when(actual.id()).thenReturn(DUENO);

        assertThatThrownBy(() -> servicio.reemplazar(new PeticionHorario(List.of(
                new FranjaDto((short) 1, "09:00", "11:00"),
                new FranjaDto((short) 1, "10:00", "12:00")))))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error -> assertThat(error.codigo()).isEqualTo("RN-AG-01"));

        verify(horarios, never()).deleteByOdontologoId(anyLong());
    }

    @Test
    void reemplazaBajoElIdDeLaSesionYHaceFlushDentroDeLaTransaccion() {
        when(actual.id()).thenReturn(DUENO);

        HorarioDto resultado = servicio.reemplazar(new PeticionHorario(List.of(
                new FranjaDto((short) 5, "16:00", "20:00"),
                new FranjaDto((short) 1, "09:00", "13:00"))));

        verify(horarios).deleteByOdontologoId(DUENO);
        ArgumentCaptor<List<HorarioAtencion>> captor = ArgumentCaptor.forClass(List.class);
        verify(horarios).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(HorarioAtencion::getOdontologoId)
                .containsExactly(DUENO, DUENO);
        assertThat(captor.getValue()).extracting(HorarioAtencion::getDiaSemana, HorarioAtencion::getHoraInicio)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple((short) 5, LocalTime.of(16, 0)),
                        org.assertj.core.groups.Tuple.tuple((short) 1, LocalTime.of(9, 0)));
        verify(horarios).flush();
        assertThat(resultado.franjas()).extracting(FranjaDto::diaSemana).containsExactly((short) 1, (short) 5);
    }

    @Test
    void unaListaVaciaDejaElHorarioSinFranjas() {
        when(actual.id()).thenReturn(DUENO);

        HorarioDto resultado = servicio.reemplazar(new PeticionHorario(List.of()));

        verify(horarios).deleteByOdontologoId(DUENO);
        verify(horarios).saveAll(List.of());
        verify(horarios).flush();
        assertThat(resultado.franjas()).isEmpty();
    }

    @Test
    void listarDevuelveLasFranjasOrdenadasQueDaElRepositorio() {
        when(actual.id()).thenReturn(DUENO);
        when(horarios.findByOdontologoIdOrderByDiaSemanaHoraInicio(DUENO)).thenReturn(List.of(
                new HorarioAtencion(DUENO, (short) 1, LocalTime.of(9, 0), LocalTime.of(13, 0)),
                new HorarioAtencion(DUENO, (short) 3, LocalTime.of(16, 0), LocalTime.of(20, 0))));

        assertThat(servicio.listar().franjas()).containsExactly(
                new FranjaDto((short) 1, "09:00", "13:00"),
                new FranjaDto((short) 3, "16:00", "20:00"));
        verify(horarios, never()).deleteByOdontologoId(anyLong());
    }
}
