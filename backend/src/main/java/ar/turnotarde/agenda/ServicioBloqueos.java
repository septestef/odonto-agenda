package ar.turnotarde.agenda;

import ar.turnotarde.agenda.dto.BloqueoDto;
import ar.turnotarde.agenda.dto.PeticionBloqueo;
import ar.turnotarde.agenda.dto.TurnoAfectadoDto;
import ar.turnotarde.shared.error.ErroresValidacion;
import ar.turnotarde.shared.seguridad.AislamientoOdontologo;
import ar.turnotarde.shared.seguridad.OdontologoActual;
import ar.turnotarde.shared.tiempo.ZonaNegocio;
import ar.turnotarde.turno.TurnoRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bloqueos con sus turnos afectados: nunca cancela turnos (RN-AG-02, D6). */
@Service
public class ServicioBloqueos {

    private final BloqueoRepository bloqueos;
    private final TurnoRepository turnos;
    private final ZonaNegocio zonaNegocio;
    private final OdontologoActual actual;

    public ServicioBloqueos(BloqueoRepository bloqueos, TurnoRepository turnos, ZonaNegocio zonaNegocio,
            OdontologoActual actual) {
        this.bloqueos = bloqueos;
        this.turnos = turnos;
        this.zonaNegocio = zonaNegocio;
        this.actual = actual;
    }

    @Transactional(readOnly = true)
    public List<BloqueoDto> listar(OffsetDateTime desde, OffsetDateTime hasta) {
        Instant desdeInstant = desde == null ? null : desde.toInstant();
        Instant hastaInstant = hasta == null ? null : hasta.toInstant();
        long odontologoId = actual.id();
        return bloqueos.findByOdontologoIdOrderByInicio(odontologoId).stream()
                .filter(bloqueo -> desdeInstant == null || bloqueo.getFin().isAfter(desdeInstant))
                .filter(bloqueo -> hastaInstant == null || bloqueo.getInicio().isBefore(hastaInstant))
                .map(bloqueo -> aDto(bloqueo, odontologoId))
                .toList();
    }

    @Transactional
    public BloqueoDto crear(PeticionBloqueo peticion) {
        Instant inicio = peticion.inicio().toInstant();
        Instant fin = peticion.fin().toInstant();
        if (!inicio.isBefore(fin)) {
            throw ErroresValidacion.campo("fin", "El fin tiene que ser posterior al inicio.");
        }
        long odontologoId = actual.id();
        Bloqueo bloqueo = bloqueos.saveAndFlush(
                new Bloqueo(odontologoId, inicio, fin, peticion.motivo()));
        return aDto(bloqueo, odontologoId);
    }

    @Transactional
    public void borrar(long id) {
        Bloqueo bloqueo = AislamientoOdontologo.exigir(
                bloqueos.findByIdAndOdontologoId(id, actual.id()));
        bloqueos.delete(bloqueo);
    }

    private BloqueoDto aDto(Bloqueo bloqueo, long odontologoId) {
        List<TurnoAfectadoDto> afectados = turnos
                .turnosAfectados(odontologoId, bloqueo.getInicio(), bloqueo.getFin()).stream()
                .map(turno -> new TurnoAfectadoDto(
                        turno.id(),
                        zonaNegocio.aLocal(turno.inicio()).toOffsetDateTime(),
                        zonaNegocio.aLocal(turno.fin()).toOffsetDateTime(),
                        turno.estado().name(),
                        turno.pacienteNombre(),
                        turno.pacienteApellido(),
                        turno.prestacionNombre()))
                .toList();
        return new BloqueoDto(
                bloqueo.getId(),
                zonaNegocio.aLocal(bloqueo.getInicio()).toOffsetDateTime(),
                zonaNegocio.aLocal(bloqueo.getFin()).toOffsetDateTime(),
                bloqueo.getMotivo(),
                afectados);
    }
}
