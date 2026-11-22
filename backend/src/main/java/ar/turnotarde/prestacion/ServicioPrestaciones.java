package ar.turnotarde.prestacion;

import ar.turnotarde.prestacion.dto.PeticionPrestacion;
import ar.turnotarde.prestacion.dto.PrestacionDto;
import ar.turnotarde.shared.error.ConflictoNombres;
import ar.turnotarde.shared.error.ErroresValidacion;
import ar.turnotarde.shared.error.ReglaNegocioException;
import ar.turnotarde.shared.reglas.ReglasAgenda;
import ar.turnotarde.shared.seguridad.AislamientoOdontologo;
import ar.turnotarde.shared.seguridad.OdontologoActual;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Alta, edición y baja lógica de prestaciones, siempre dentro del odontólogo de la sesión (D5). */
@Service
public class ServicioPrestaciones {

    private final PrestacionRepository prestaciones;
    private final OdontologoActual actual;

    public ServicioPrestaciones(PrestacionRepository prestaciones, OdontologoActual actual) {
        this.prestaciones = prestaciones;
        this.actual = actual;
    }

    @Transactional(readOnly = true)
    public List<PrestacionDto> listar() {
        return prestaciones.findByOdontologoIdOrderByNombre(actual.id()).stream()
                .map(PrestacionDto::desde)
                .toList();
    }

    @Transactional
    public PrestacionDto crear(PeticionPrestacion peticion) {
        validarDuracion(peticion.duracionMinutos());
        long odontologoId = actual.id();
        if (prestaciones.existsByOdontologoIdAndNombre(odontologoId, peticion.nombre())) {
            throw nombreRepetido();
        }
        Prestacion prestacion = new Prestacion(odontologoId, peticion.nombre(),
                peticion.duracionMinutos().shortValue());
        guardar(prestacion);
        return PrestacionDto.desde(prestacion);
    }

    @Transactional
    public PrestacionDto actualizar(long id, PeticionPrestacion peticion) {
        validarDuracion(peticion.duracionMinutos());
        long odontologoId = actual.id();
        Prestacion prestacion = AislamientoOdontologo.exigir(
                prestaciones.findByIdAndOdontologoId(id, odontologoId));
        if (!prestacion.getNombre().equals(peticion.nombre())
                && prestaciones.existsByOdontologoIdAndNombre(odontologoId, peticion.nombre())) {
            throw nombreRepetido();
        }
        prestacion.actualizar(peticion.nombre(), peticion.duracionMinutos().shortValue());
        prestaciones.flush();
        return PrestacionDto.desde(prestacion);
    }

    @Transactional
    public void darDeBaja(long id) {
        Prestacion prestacion = AislamientoOdontologo.exigir(
                prestaciones.findByIdAndOdontologoId(id, actual.id()));
        prestacion.desactivar();
        prestaciones.flush();
    }

    private static void validarDuracion(Integer duracion) {
        if (duracion % 15 != 0) {
            throw ReglasAgenda.duracionInvalida();
        }
    }

    private void guardar(Prestacion prestacion) {
        try {
            prestaciones.saveAndFlush(prestacion);
        } catch (DataIntegrityViolationException error) {
            if (ConflictoNombres.esNombreRepetido(error)) {
                throw nombreRepetido();
            }
            throw error;
        }
    }

    private static ReglaNegocioException nombreRepetido() {
        return ErroresValidacion.campo("nombre", "Ya tenés una prestación con ese nombre.");
    }
}
