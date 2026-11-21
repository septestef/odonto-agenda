package ar.turnotarde.turno;

import ar.turnotarde.agenda.DisponibilidadService;
import ar.turnotarde.odontologo.Odontologo;
import ar.turnotarde.paciente.Paciente;
import ar.turnotarde.paciente.PacienteRepository;
import ar.turnotarde.paciente.RegistroPacientes;
import ar.turnotarde.prestacion.Prestacion;
import ar.turnotarde.prestacion.PrestacionRepository;
import ar.turnotarde.shared.tiempo.ZonaNegocio;
import ar.turnotarde.turno.eventos.ResumenTurno;
import ar.turnotarde.turno.eventos.TurnoReservado;
import java.time.Duration;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reserva de un turno en una única transacción (D5). Orden de locks, regla para C-09 y C-15: primero
 * {@code paciente} (upsert) y después {@code turno} (el trigger toma el advisory lock del odontólogo);
 * ningún camino toma el advisory lock y luego el lock de paciente, así que no hay ciclo posible.
 * La garantía de no superposición es la exclusion constraint de PostgreSQL, no la validación previa.
 */
@Service
public class ReservaService {

    /** RN-PA-03: máximo de turnos {@code RESERVADO} futuros por DNI y odontólogo. */
    static final int MAX_TURNOS_FUTUROS = 2;

    private final DisponibilidadService disponibilidad;
    private final RegistroPacientes registroPacientes;
    private final PacienteRepository pacientes;
    private final PrestacionRepository prestaciones;
    private final TurnoRepository turnos;
    private final GuardiaSolapamiento guardia;
    private final ApplicationEventPublisher publicador;
    private final ZonaNegocio zonaNegocio;

    public ReservaService(DisponibilidadService disponibilidad, RegistroPacientes registroPacientes,
            PacienteRepository pacientes, PrestacionRepository prestaciones, TurnoRepository turnos,
            GuardiaSolapamiento guardia, ApplicationEventPublisher publicador, ZonaNegocio zonaNegocio) {
        this.disponibilidad = disponibilidad;
        this.registroPacientes = registroPacientes;
        this.pacientes = pacientes;
        this.prestaciones = prestaciones;
        this.turnos = turnos;
        this.guardia = guardia;
        this.publicador = publicador;
        this.zonaNegocio = zonaNegocio;
    }

    /** Datos del paciente tal como los ingresó en la reserva. */
    public record DatosPaciente(String dni, String nombre, String apellido, String telefono, String email) {
    }

    /**
     * Reserva online. Cualquier rechazo ({@code ReglaNegocioException}) revierte todo, incluida la actualización
     * del contacto del paciente. Publica {@link TurnoReservado} antes del commit.
     */
    @Transactional
    public ResumenTurno reservar(Odontologo odontologo, Prestacion prestacion, Instant inicio,
            DatosPaciente datos) {
        // 1. Validación de agenda (solo lecturas): mismas reglas que la disponibilidad.
        disponibilidad.validarInicio(odontologo.getId(), prestacion, inicio);

        // 2. Upsert del paciente: deja la fila bloqueada hasta el commit y serializa reservas del mismo DNI.
        long pacienteId = registroPacientes.registrar(odontologo.getId(), datos.dni(), datos.nombre(),
                datos.apellido(), datos.telefono(), datos.email());

        // 3. Límite RN-PA-03, contado con la instantánea posterior al lock del paciente.
        if (turnos.contarReservadosFuturos(odontologo.getId(), pacienteId, zonaNegocio.ahora())
                >= MAX_TURNOS_FUTUROS) {
            throw ReglasTurno.limiteDeTurnos();
        }

        // 4. Token y escritura: la constraint decide; saveAndFlush la hace saltar dentro de la guardia.
        TokenTurno token = TokenTurno.generar();
        Paciente paciente = pacientes.getReferenceById(pacienteId);
        Instant fin = inicio.plus(Duration.ofMinutes(prestacion.getDuracionMinutos()));
        Turno turno = guardia.ejecutar(() -> turnos.saveAndFlush(new Turno(odontologo.getId(), paciente,
                prestaciones.getReferenceById(prestacion.getId()), inicio, fin, OrigenTurno.ONLINE, token.hash())));

        // 5. Evento dentro de la transacción: los consumidores AFTER_COMMIT lo reciben solo si se confirmó.
        ResumenTurno resumen = new ResumenTurno(turno.getId(), odontologo.getId(), inicio, fin, prestacion.getId(),
                prestacion.getNombre(), prestacion.getDuracionMinutos(), datos.nombre(), datos.apellido(),
                datos.email(), datos.telefono(), odontologo.getNombre(), odontologo.getApellido(),
                odontologo.getDireccionConsultorio());
        publicador.publishEvent(new TurnoReservado(resumen, OrigenTurno.ONLINE, token.plano()));
        return resumen;
    }
}
