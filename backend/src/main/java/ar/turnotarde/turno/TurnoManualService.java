package ar.turnotarde.turno;

import ar.turnotarde.agenda.DisponibilidadService;
import ar.turnotarde.odontologo.Odontologo;
import ar.turnotarde.odontologo.OdontologoRepository;
import ar.turnotarde.prestacion.Prestacion;
import ar.turnotarde.prestacion.PrestacionRepository;
import ar.turnotarde.publico.DisponibilidadPublica;
import ar.turnotarde.publico.HorarioLibre;
import ar.turnotarde.publico.OdontologoPublico;
import ar.turnotarde.publico.PrestacionPublica;
import ar.turnotarde.publico.ReservaConfirmada;
import ar.turnotarde.shared.seguridad.AislamientoOdontologo;
import ar.turnotarde.shared.seguridad.OdontologoActual;
import ar.turnotarde.shared.tiempo.ZonaNegocio;
import ar.turnotarde.turno.dto.SolicitudTurnoManual;
import ar.turnotarde.turno.eventos.ResumenTurno;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * C-15: resuelve el dueño desde la sesión y delega el alta en {@link ReservaService}. No es transaccional: la
 * transacción de escritura es la de {@link ReservaService#reservarManual}.
 */
@Service
public class TurnoManualService {

    private static final DateTimeFormatter HORA_LOCAL = DateTimeFormatter.ofPattern("HH:mm");

    private final OdontologoActual odontologoActual;
    private final OdontologoRepository odontologos;
    private final PrestacionRepository prestaciones;
    private final DisponibilidadService disponibilidad;
    private final ReservaService reservas;
    private final ZonaNegocio zonaNegocio;

    public TurnoManualService(OdontologoActual odontologoActual, OdontologoRepository odontologos,
            PrestacionRepository prestaciones, DisponibilidadService disponibilidad, ReservaService reservas,
            ZonaNegocio zonaNegocio) {
        this.odontologoActual = odontologoActual;
        this.odontologos = odontologos;
        this.prestaciones = prestaciones;
        this.disponibilidad = disponibilidad;
        this.reservas = reservas;
        this.zonaNegocio = zonaNegocio;
    }

    /**
     * Misma forma de respuesta que la ventanilla pública, con los horarios futuros del día según la agenda
     * autenticada (sin la anticipación de 24 h). La prestación se resuelve dentro del odontólogo de la sesión.
     */
    @Transactional(readOnly = true)
    public DisponibilidadPublica disponibilidad(long prestacionId, LocalDate fecha) {
        long odontologoId = odontologoActual.id();
        Prestacion prestacion = AislamientoOdontologo.exigir(
                prestaciones.findByIdAndOdontologoIdAndActivaTrue(prestacionId, odontologoId));
        List<HorarioLibre> horarios = disponibilidad
                .horariosLibresManual(odontologoId, prestacion, fecha).stream()
                .map(inicio -> {
                    var local = zonaNegocio.aLocal(inicio);
                    return new HorarioLibre(local.toOffsetDateTime(), local.format(HORA_LOCAL));
                }).toList();
        return new DisponibilidadPublica(fecha, prestacion.getId(), prestacion.getDuracionMinutos(), horarios);
    }

    public ReservaConfirmada crear(SolicitudTurnoManual solicitud) {
        long odontologoId = odontologoActual.id();
        Odontologo odontologo = AislamientoOdontologo.exigir(odontologos.findById(odontologoId));
        Prestacion prestacion = AislamientoOdontologo.exigir(
                prestaciones.findByIdAndOdontologoIdAndActivaTrue(solicitud.prestacionId(), odontologoId));

        ResumenTurno turno = reservas.reservarManual(odontologo, prestacion, solicitud.inicio().toInstant(),
                new ReservaService.DatosPaciente(solicitud.dni(), solicitud.nombre(), solicitud.apellido(),
                        solicitud.telefono(), solicitud.email()));

        return new ReservaConfirmada(zonaNegocio.aLocal(turno.inicio()).toOffsetDateTime(),
                zonaNegocio.aLocal(turno.fin()).toOffsetDateTime(),
                new PrestacionPublica(turno.prestacionId(), turno.prestacionNombre(), turno.duracionMinutos()),
                new OdontologoPublico(turno.odontologoNombre(), turno.odontologoApellido(),
                        turno.direccionConsultorio()),
                turno.pacienteEmail());
    }
}
