package ar.turnotarde.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.turnotarde.agenda.dto.BloqueoDto;
import ar.turnotarde.agenda.dto.PeticionBloqueo;
import ar.turnotarde.shared.error.ReglaNegocioException;
import ar.turnotarde.shared.seguridad.OdontologoActual;
import ar.turnotarde.shared.tiempo.ZonaNegocio;
import ar.turnotarde.turno.EstadoTurno;
import ar.turnotarde.turno.TurnoRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Un bloqueo no cancela turnos: solo los lista con offset de negocio (D6). */
@ExtendWith(MockitoExtension.class)
class ServicioBloqueosTest {

    private static final long DUENO = 42L;
    private static final long ID = 9L;
    private static final Instant INICIO = Instant.parse("2026-11-27T13:00:00Z");
    private static final Instant FIN = Instant.parse("2026-11-27T15:00:00Z");

    @Mock
    private BloqueoRepository bloqueos;
    @Mock
    private TurnoRepository turnos;
    @Mock
    private ZonaNegocio zonaNegocio;
    @Mock
    private OdontologoActual actual;

    private ServicioBloqueos servicio;

    @BeforeEach
    void preparar() {
        servicio = new ServicioBloqueos(bloqueos, turnos, zonaNegocio, actual);
        org.mockito.Mockito.lenient().when(zonaNegocio.aLocal(any(Instant.class)))
                .thenAnswer(invocacion -> ((Instant) invocacion.getArgument(0)).atZone(ZoneOffset.UTC));
    }

    private static OffsetDateTime offset(Instant instante) {
        return instante.atOffset(ZoneOffset.UTC);
    }

    private static TurnoAfectadoProyeccion afectado(long id, Instant inicio, EstadoTurno estado) {
        return new TurnoAfectadoProyeccion(id, inicio, inicio.plusSeconds(1800), estado, "Luis", "Gómez", "Consulta");
    }

    @Test
    void unRangoInvertidoSeRechazaCon400YDetalleEnFin() {
        assertThatThrownBy(() -> servicio.crear(new PeticionBloqueo(offset(FIN), offset(INICIO), null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo("VALIDACION");
                    assertThat(error.estado().value()).isEqualTo(400);
                    assertThat(error.detalles().toString()).contains("fin");
                });
        verify(bloqueos, never()).saveAndFlush(any(Bloqueo.class));
        verify(actual, never()).id();
    }

    @Test
    void unRangoConFinIgualAlInicioSeRechaza() {
        assertThatThrownBy(() -> servicio.crear(new PeticionBloqueo(offset(INICIO), offset(INICIO), null)))
                .isInstanceOfSatisfying(ReglaNegocioException.class,
                        error -> assertThat(error.codigo()).isEqualTo("VALIDACION"));
        verifyNoGuardado();
    }

    @Test
    void altaDevuelveLosTurnosAfectadosConOffsetYNocancelaNada() {
        when(actual.id()).thenReturn(DUENO);
        when(bloqueos.saveAndFlush(any(Bloqueo.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(turnos.turnosAfectados(DUENO, INICIO, FIN)).thenReturn(List.of(
                afectado(11L, INICIO, EstadoTurno.RESERVADO),
                afectado(12L, INICIO.plusSeconds(1800), EstadoTurno.ATENDIDO),
                afectado(13L, INICIO.plusSeconds(3600), EstadoTurno.AUSENTE)));

        BloqueoDto resultado = servicio.crear(new PeticionBloqueo(offset(INICIO), offset(FIN), "Vacaciones"));

        assertThat(resultado.inicio()).isEqualTo(offset(INICIO));
        assertThat(resultado.fin()).isEqualTo(offset(FIN));
        assertThat(resultado.motivo()).isEqualTo("Vacaciones");
        assertThat(resultado.turnosAfectados()).extracting(t -> t.estado())
                .containsExactly("RESERVADO", "ATENDIDO", "AUSENTE");
        assertThat(resultado.turnosAfectados()).extracting(t -> t.pacienteNombre())
                .containsExactly("Luis", "Luis", "Luis");
        assertThat(resultado.turnosAfectados().get(0).inicio()).isEqualTo(offset(INICIO));
        verify(turnos).turnosAfectados(DUENO, INICIO, FIN);
        verify(turnos, never()).save(any());
    }

    @Test
    void laProyeccionYaExcluyeCanceladosAsiQueNoSeReagregan() {
        when(actual.id()).thenReturn(DUENO);
        when(bloqueos.saveAndFlush(any(Bloqueo.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(turnos.turnosAfectados(DUENO, INICIO, FIN))
                .thenReturn(List.of(afectado(11L, INICIO, EstadoTurno.RESERVADO)));

        assertThat(servicio.crear(new PeticionBloqueo(offset(INICIO), offset(FIN), null)).turnosAfectados())
                .extracting(t -> t.estado()).containsExactly("RESERVADO");
    }

    @Test
    void borrarUnBloqueoAjenoDa404YSigueExistiendo() {
        when(actual.id()).thenReturn(DUENO);
        when(bloqueos.findByIdAndOdontologoId(ID, DUENO)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.borrar(ID))
                .isInstanceOfSatisfying(ReglaNegocioException.class, error -> {
                    assertThat(error.codigo()).isEqualTo("NO_ENCONTRADO");
                    assertThat(error.estado().value()).isEqualTo(404);
                });
        verify(bloqueos, never()).delete(any(Bloqueo.class));
    }

    @Test
    void listarUsaElDuenoDeLaSesionYFiltroPorIntervalo() {
        when(actual.id()).thenReturn(DUENO);
        Bloqueo propio = new Bloqueo(DUENO, INICIO, FIN, "Vacaciones");
        org.springframework.test.util.ReflectionTestUtils.setField(propio, "id", ID);
        Bloqueo anterior = new Bloqueo(DUENO, INICIO.minusSeconds(7200), INICIO, "Anterior");
        when(bloqueos.findByOdontologoIdOrderByInicio(DUENO)).thenReturn(List.of(anterior, propio));
        when(turnos.turnosAfectados(anyLong(), any(Instant.class), any(Instant.class))).thenReturn(List.of());

        List<BloqueoDto> todos = servicio.listar(null, null);
        assertThat(todos).hasSize(2);

        List<BloqueoDto> filtrados = servicio.listar(offset(INICIO), offset(FIN));
        assertThat(filtrados).extracting(BloqueoDto::id).containsExactly(ID);
        verify(bloqueos, org.mockito.Mockito.times(2)).findByOdontologoIdOrderByInicio(DUENO);
    }

    private void verifyNoGuardado() {
        verify(bloqueos, never()).saveAndFlush(any(Bloqueo.class));
        verify(actual, never()).id();
    }
}
