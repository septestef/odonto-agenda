package ar.turnotarde.turno;

import ar.turnotarde.agenda.DisponibilidadService;
import ar.turnotarde.odontologo.Odontologo;
import ar.turnotarde.odontologo.OdontologoRepository;
import ar.turnotarde.prestacion.Prestacion;
import ar.turnotarde.shared.correo.BuzonCorreo;
import ar.turnotarde.shared.error.CodigosError;
import ar.turnotarde.shared.error.ReglaNegocioException;
import ar.turnotarde.shared.tiempo.ZonaNegocio;
import ar.turnotarde.turno.eventos.ResumenTurno;
import ar.turnotarde.turno.eventos.TurnoCancelado;
import ar.turnotarde.turno.eventos.TurnoReprogramado;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GestionTurnoService {
    private final TurnoRepository turnos;
    private final OdontologoRepository odontologos;
    private final ZonaNegocio zonaNegocio;
    private final ApplicationEventPublisher publicador;
    private final JdbcTemplate jdbc;
    private final DisponibilidadService disponibilidad;

    public GestionTurnoService(TurnoRepository turnos, OdontologoRepository odontologos, ZonaNegocio zonaNegocio,
            ApplicationEventPublisher publicador, JdbcTemplate jdbc, DisponibilidadService disponibilidad) {
        this.turnos = turnos;
        this.odontologos = odontologos;
        this.zonaNegocio = zonaNegocio;
        this.publicador = publicador;
        this.jdbc = jdbc;
        this.disponibilidad = disponibilidad;
    }

    @Transactional(readOnly = true)
    public Detalle consultar(String token) {
        Turno turno = turnos.findByTokenHash(hashDeGestion(token)).orElseThrow(GestionTurnoService::noEncontrado);
        Odontologo odontologo = odontologos.findById(turno.getOdontologoId())
                .orElseThrow(GestionTurnoService::noEncontrado);
        Prestacion prestacion = turno.getPrestacion();
        Instant ahora = zonaNegocio.ahora();
        boolean puedeCancelar = turno.getEstado() == EstadoTurno.RESERVADO && ahora.isBefore(turno.getInicio());
        boolean puedeReprogramar = puedeCancelar && activosYAsociados(odontologo, prestacion)
                && BuzonCorreo.admisible(turno.getEmailGestion());
        return new Detalle(zonaNegocio.aLocal(turno.getInicio()).toOffsetDateTime(),
                zonaNegocio.aLocal(turno.getFin()).toOffsetDateTime(), turno.getEstado(),
                new PrestacionDetalle(prestacion.getId(), prestacion.getNombre()),
                new OdontologoDetalle(odontologo.getSlug(), odontologo.getNombre(), odontologo.getApellido(),
                        odontologo.getDireccionConsultorio()), puedeCancelar, puedeReprogramar);
    }

    @Transactional
    public void cancelar(String token) {
        Turno turno = turnos.bloquearPorHash(hashDeGestion(token)).orElseThrow(GestionTurnoService::noEncontrado);
        Instant ahora = zonaNegocio.ahora();
        ReglasTurno.exigirOriginalEditable(turno.getInicio(), turno.getEstado(), ahora);
        turno.cancelarPorPaciente(ahora);
        turnos.flush();
        var odontologo = odontologos.findById(turno.getOdontologoId()).orElseThrow(GestionTurnoService::noEncontrado);
        String email = turno.getEmailGestion();
        var resumen = resumen(turno, odontologo, BuzonCorreo.admisible(email) ? email : null);
        publicador.publishEvent(new TurnoCancelado(resumen, CanceladoPor.PACIENTE, ahora));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void reprogramar(String token, Instant inicio) {
        String hash = hashDeGestion(token);
        var vinculo = jdbc.query("""
                SELECT id, odontologo_id, paciente_id FROM turno WHERE token_hash = ?
                """, (rs, fila) -> new VinculoOriginal(rs.getLong(1), rs.getLong(2), rs.getLong(3)), hash)
                .stream().findFirst().orElseThrow(GestionTurnoService::noEncontrado);
        bloquearPacienteYConsultorio(vinculo);
        Turno original = turnos.bloquearPorHash(hash).orElseThrow(GestionTurnoService::noEncontrado);
        if (original.getId() != vinculo.turno() || original.getOdontologoId() != vinculo.odontologo()
                || original.getPaciente().getId() != vinculo.paciente()) {
            throw noEncontrado();
        }
        Instant ahora = zonaNegocio.ahora();
        ReglasTurno.exigirOriginalEditable(original.getInicio(), original.getEstado(), ahora);
        Odontologo od = odontologos.findById(original.getOdontologoId()).orElseThrow(GestionTurnoService::noEncontrado);
        Prestacion pr = original.getPrestacion();
        if (!activosYAsociados(od, pr)) {
            throw noEncontrado();
        }
        if (!BuzonCorreo.admisible(original.getEmailGestion())) {
            throw new ReglaNegocioException("RN-SE-04", "No podés reprogramar este turno desde el link.",
                    HttpStatus.CONFLICT);
        }
        disponibilidad.validarInicio(od.getId(), pr, inicio, ahora);
        Instant fin = inicio.plus(Duration.ofMinutes(pr.getDuracionMinutos()));
        original.cancelarPorPaciente(ahora);
        // IDENTITY inserta al persistir: el UPDATE debe ejecutarse antes de intentar el alta.
        turnos.flush();
        TokenTurno nuevoToken = TokenTurno.generar();
        Turno sucesor = turnos.saveAndFlush(original.sucesor(inicio, fin, nuevoToken.hash()));
        publicador.publishEvent(new TurnoReprogramado(resumen(original, od, original.getEmailGestion()),
                resumen(sucesor, od, sucesor.getEmailGestion()), nuevoToken.plano()));
    }

    private void bloquearPacienteYConsultorio(VinculoOriginal vinculo) {
        var paciente = jdbc.queryForList("""
                SELECT id FROM paciente WHERE id = ? AND odontologo_id = ? FOR NO KEY UPDATE
                """, Long.class, vinculo.paciente(), vinculo.odontologo());
        if (paciente.isEmpty()) {
            throw noEncontrado();
        }
        jdbc.queryForObject("""
                SELECT 1 FROM pg_advisory_xact_lock(hashtext('turno_sin_solapamiento'), hashtext(CAST(? AS text)))
                """, Integer.class, vinculo.odontologo());
    }

    private record VinculoOriginal(long turno, long odontologo, long paciente) {
    }

    private static ResumenTurno resumen(Turno turno, Odontologo odontologo, String email) {
        var prestacion = turno.getPrestacion();
        return new ResumenTurno(turno.getId(), odontologo.getId(), turno.getInicio(), turno.getFin(),
                prestacion.getId(), prestacion.getNombre(),
                Math.toIntExact(Duration.between(turno.getInicio(), turno.getFin()).toMinutes()),
                null, null, email, null, odontologo.getNombre(), odontologo.getApellido(),
                odontologo.getDireccionConsultorio());
    }

    private static boolean activosYAsociados(Odontologo odontologo, Prestacion prestacion) {
        return odontologo.isActivo() && prestacion.isActiva()
                && odontologo.getId().equals(prestacion.getOdontologoId());
    }

    private static String hashDeGestion(String token) {
        if (!TokenTurno.formatoValido(token)) {
            throw noEncontrado();
        }
        return TokenTurno.hashear(token);
    }

    private static ReglaNegocioException noEncontrado() {
        return new ReglaNegocioException(CodigosError.NO_ENCONTRADO, "No encontramos lo que buscabas.",
                HttpStatus.NOT_FOUND);
    }

    public record Detalle(OffsetDateTime inicio, OffsetDateTime fin, EstadoTurno estado, PrestacionDetalle prestacion,
            OdontologoDetalle odontologo, boolean puedeCancelar, boolean puedeReprogramar) {
    }

    public record PrestacionDetalle(Long id, String nombre) {
    }

    public record OdontologoDetalle(String slug, String nombre, String apellido, String direccionConsultorio) {
    }
}
