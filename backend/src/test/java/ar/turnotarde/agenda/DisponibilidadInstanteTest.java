package ar.turnotarde.agenda;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ar.turnotarde.prestacion.Prestacion;
import ar.turnotarde.shared.config.AppProperties;
import ar.turnotarde.shared.error.ReglaNegocioException;
import ar.turnotarde.shared.tiempo.ZonaNegocio;
import ar.turnotarde.turno.TurnoRepository;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DisponibilidadInstanteTest {
    private static final long OD = 1L;
    private static final Instant ANTES_DE_MEDIANOCHE = Instant.parse("2026-11-26T02:59:59Z");
    private static final Instant DESPUES_DE_MEDIANOCHE = ANTES_DE_MEDIANOCHE.plusSeconds(2);
    private static final Instant EN_HORIZONTE = Instant.parse("2027-01-24T12:00:00Z");
    private static final Instant SIGUIENTE_DIA = Instant.parse("2027-01-25T12:00:00Z");
    private final Clock reloj = mock(Clock.class);
    private final HorarioAtencionRepository horarios = mock(HorarioAtencionRepository.class);
    private final BloqueoRepository bloqueos = mock(BloqueoRepository.class);
    private final TurnoRepository turnos = mock(TurnoRepository.class);
    private final AppProperties propiedades = new AppProperties(ZoneId.of("America/Argentina/Buenos_Aires"),
            URI.create("http://localhost:8080"), 60);
    private final DisponibilidadService service = new DisponibilidadService(horarios, bloqueos, turnos,
            new ZonaNegocio(reloj, propiedades), propiedades);
    private final Prestacion prestacion = new Prestacion(OD, "Consulta", (short) 30);

    @BeforeEach
    void preparar() {
        when(horarios.findByOdontologoIdAndDiaSemanaOrderByHoraInicio(eq(OD), anyShort()))
                .thenAnswer(invocacion -> List.of(new HorarioAtencion(OD, invocacion.getArgument(1),
                        LocalTime.of(9, 0), LocalTime.of(13, 0))));
    }

    @Test
    void c09NoLeeRelojYDerivaHorizonteDelInstanteRecibido() {
        assertThatCode(() -> service.validarInicio(OD, prestacion, EN_HORIZONTE, ANTES_DE_MEDIANOCHE))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> service.validarInicio(OD, prestacion, SIGUIENTE_DIA, ANTES_DE_MEDIANOCHE))
                .isInstanceOfSatisfying(ReglaNegocioException.class, e -> assertThat(e.codigo()).isEqualTo("RN-TU-11"));
        verifyNoInteractions(reloj);
    }

    @Test
    void c07ConservaLecturasSeparadasDespuesDePrepararAgendaAunqueCruceMedianoche() {
        when(reloj.instant()).thenReturn(ANTES_DE_MEDIANOCHE, DESPUES_DE_MEDIANOCHE);
        assertThatCode(() -> service.validarInicio(OD, prestacion, SIGUIENTE_DIA)).doesNotThrowAnyException();
        var orden = inOrder(turnos, reloj);
        orden.verify(turnos).intervalosActivosQueCruzan(eq(OD), any(), any());
        orden.verify(reloj, org.mockito.Mockito.times(2)).instant();
        orden.verifyNoMoreInteractions();
    }
}
