package ar.turnotarde.publico;

import ar.turnotarde.agenda.DisponibilidadService;
import ar.turnotarde.odontologo.Odontologo;
import ar.turnotarde.odontologo.OdontologoRepository;
import ar.turnotarde.prestacion.Prestacion;
import ar.turnotarde.prestacion.PrestacionRepository;
import ar.turnotarde.shared.config.AppProperties;
import ar.turnotarde.shared.error.CodigosError;
import ar.turnotarde.shared.error.ReglaNegocioException;
import ar.turnotarde.shared.tiempo.ZonaNegocio;
import ar.turnotarde.turno.ReservaService;
import ar.turnotarde.turno.eventos.ResumenTurno;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resuelve el odontólogo por slug y la prestación activa (404 uniforme, RN-SE-01) y arma las respuestas de
 * lectura del paciente. El odontólogo nunca viene del request: sale del slug.
 */
@Service
public class PublicoService {

    private static final DateTimeFormatter HORA_LOCAL = DateTimeFormatter.ofPattern("HH:mm");

    private final OdontologoRepository odontologos;
    private final PrestacionRepository prestaciones;
    private final DisponibilidadService disponibilidad;
    private final ReservaService reservas;
    private final ZonaNegocio zonaNegocio;
    private final AppProperties propiedades;

    public PublicoService(OdontologoRepository odontologos, PrestacionRepository prestaciones,
            DisponibilidadService disponibilidad, ReservaService reservas, ZonaNegocio zonaNegocio,
            AppProperties propiedades) {
        this.odontologos = odontologos;
        this.prestaciones = prestaciones;
        this.disponibilidad = disponibilidad;
        this.reservas = reservas;
        this.zonaNegocio = zonaNegocio;
        this.propiedades = propiedades;
    }

    /** Mismo cuerpo para slug inexistente, odontólogo inactivo y prestación inexistente, de baja o ajena. */
    public static ReglaNegocioException noEncontrado() {
        return new ReglaNegocioException(CodigosError.NO_ENCONTRADO, "No encontramos lo que buscabas.",
                HttpStatus.NOT_FOUND);
    }

    public Odontologo odontologoActivo(String slug) {
        return odontologos.findBySlugAndActivoTrue(slug).orElseThrow(PublicoService::noEncontrado);
    }

    public Prestacion prestacionActiva(Odontologo odontologo, long prestacionId) {
        return prestaciones.findByIdAndOdontologoIdAndActivaTrue(prestacionId, odontologo.getId())
                .orElseThrow(PublicoService::noEncontrado);
    }

    @Transactional(readOnly = true)
    public PerfilPublico perfil(String slug) {
        Odontologo odontologo = odontologoActivo(slug);
        List<PrestacionPublica> lista = prestaciones.findByOdontologoIdAndActivaTrueOrderByNombre(odontologo.getId())
                .stream().map(p -> new PrestacionPublica(p.getId(), p.getNombre(), p.getDuracionMinutos())).toList();
        LocalDate minima = LocalDate.ofInstant(zonaNegocio.ahora().plus(DisponibilidadService.ANTICIPACION_ONLINE), zonaNegocio.zona());
        LocalDate maxima = zonaNegocio.hoy().plusDays(propiedades.reservaMaxDias());
        return new PerfilPublico(odontologo.getSlug(), odontologo.getNombre(), odontologo.getApellido(),
                odontologo.getDireccionConsultorio(), zonaNegocio.zona().getId(), minima, maxima, lista);
    }

    @Transactional(readOnly = true)
    public DisponibilidadPublica disponibilidad(String slug, long prestacionId, LocalDate fecha) {
        Odontologo odontologo = odontologoActivo(slug);
        Prestacion prestacion = prestacionActiva(odontologo, prestacionId);
        List<HorarioLibre> horarios = disponibilidad.horariosLibres(odontologo.getId(), prestacion, fecha).stream()
                .map(inicio -> {
                    var local = zonaNegocio.aLocal(inicio);
                    return new HorarioLibre(local.toOffsetDateTime(), local.format(HORA_LOCAL));
                }).toList();
        return new DisponibilidadPublica(fecha, prestacion.getId(), prestacion.getDuracionMinutos(), horarios);
    }

    /**
     * Resuelve slug y prestación activos (404 uniforme, sin crear nada) y reserva. No es transaccional: la
     * transacción de escritura es la de {@link ReservaService}.
     */
    public ReservaConfirmada reservar(String slug, SolicitudReserva solicitud) {
        Odontologo odontologo = odontologoActivo(slug);
        Prestacion prestacion = prestacionActiva(odontologo, solicitud.prestacionId());
        ResumenTurno turno = reservas.reservar(odontologo, prestacion, solicitud.inicio().toInstant(),
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
