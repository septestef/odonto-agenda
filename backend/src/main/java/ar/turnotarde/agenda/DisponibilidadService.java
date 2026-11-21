package ar.turnotarde.agenda;

import ar.turnotarde.prestacion.Prestacion;
import ar.turnotarde.shared.config.AppProperties;
import ar.turnotarde.shared.tiempo.ZonaNegocio;
import ar.turnotarde.turno.ReglasTurno;
import ar.turnotarde.turno.TurnoRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga franjas, bloqueos y turnos activos del día y delega en {@link CalculadoraDisponibilidad}: la
 * disponibilidad y la reserva online usan el mismo predicado (D2).
 */
@Service
@Transactional(readOnly = true)
public class DisponibilidadService {

    /** RN-TU-03: la reserva online exige 24 horas absolutas de anticipación. */
    public static final Duration ANTICIPACION_ONLINE = Duration.ofHours(24);

    private final HorarioAtencionRepository horarios;
    private final BloqueoRepository bloqueos;
    private final TurnoRepository turnos;
    private final ZonaNegocio zonaNegocio;
    private final AppProperties propiedades;

    public DisponibilidadService(HorarioAtencionRepository horarios, BloqueoRepository bloqueos,
            TurnoRepository turnos, ZonaNegocio zonaNegocio, AppProperties propiedades) {
        this.horarios = horarios;
        this.bloqueos = bloqueos;
        this.turnos = turnos;
        this.zonaNegocio = zonaNegocio;
        this.propiedades = propiedades;
    }

    /** Inicios libres de {@code fecha} para la prestación, ordenados. */
    public List<Instant> horariosLibres(Long odontologoId, Prestacion prestacion, LocalDate fecha) {
        return CalculadoraDisponibilidad.horariosLibres(agendaDelDia(odontologoId, fecha), duracion(prestacion),
                zonaNegocio.ahora(), ANTICIPACION_ONLINE, zonaNegocio.hoy(), propiedades.reservaMaxDias());
    }

    /**
     * Revalida un inicio con las mismas reglas de la disponibilidad.
     *
     * @throws ar.turnotarde.shared.error.ReglaNegocioException {@code RN-TU-03}, {@code RN-TU-11},
     *         {@code RN-AG-05} (422) o {@code RN-TU-01} (409)
     */
    public void validarInicio(Long odontologoId, Prestacion prestacion, Instant inicio) {
        LocalDate fecha = LocalDate.ofInstant(inicio, zonaNegocio.zona());
        MotivoRechazo motivo = CalculadoraDisponibilidad.evaluar(inicio, agendaDelDia(odontologoId, fecha),
                duracion(prestacion), zonaNegocio.ahora(), ANTICIPACION_ONLINE, zonaNegocio.hoy(),
                propiedades.reservaMaxDias());
        switch (motivo) {
            case NINGUNO -> { }
            case ANTICIPACION -> throw ReglasTurno.anticipacionInsuficiente();
            case HORIZONTE -> throw ReglasTurno.fueraDeHorizonte(propiedades.reservaMaxDias());
            case FUERA_DE_AGENDA -> throw ReglasTurno.fueraDeAgenda();
            case OCUPADO -> throw ReglasTurno.horarioTomado();
        }
    }

    private AgendaDelDia agendaDelDia(Long odontologoId, LocalDate fecha) {
        ZoneId zona = zonaNegocio.zona();
        Instant desde = fecha.atStartOfDay(zona).toInstant();
        Instant hasta = fecha.plusDays(1).atStartOfDay(zona).toInstant();
        List<Franja> franjas = horarios
                .findByOdontologoIdAndDiaSemanaOrderByHoraInicio(odontologoId, (short) fecha.getDayOfWeek().getValue())
                .stream().map(h -> new Franja(h.getHoraInicio(), h.getHoraFin())).toList();
        return new AgendaDelDia(fecha, zona, franjas, bloqueos.intervalosQueCruzan(odontologoId, desde, hasta),
                turnos.intervalosActivosQueCruzan(odontologoId, desde, hasta));
    }

    private static Duration duracion(Prestacion prestacion) {
        return Duration.ofMinutes(prestacion.getDuracionMinutos());
    }
}
