package ar.turnotarde.agenda;

import ar.turnotarde.agenda.dto.FranjaDto;
import ar.turnotarde.agenda.dto.HorarioDto;
import ar.turnotarde.agenda.dto.PeticionHorario;
import ar.turnotarde.shared.seguridad.OdontologoActual;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reemplazo semanal atómico: valida antes de borrar y siempre usa el id de la sesión (D4). */
@Service
public class ServicioHorarios {

    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final HorarioAtencionRepository horarios;
    private final OdontologoActual actual;

    public ServicioHorarios(HorarioAtencionRepository horarios, OdontologoActual actual) {
        this.horarios = horarios;
        this.actual = actual;
    }

    @Transactional
    public HorarioDto reemplazar(PeticionHorario peticion) {
        long odontologoId = actual.id();
        ValidadorFranjas.validar(peticion.franjas());

        horarios.deleteByOdontologoId(odontologoId);
        List<HorarioAtencion> entidades = peticion.franjas().stream()
                .map(franja -> new HorarioAtencion(odontologoId, franja.diaSemana(),
                        LocalTime.parse(franja.inicio(), HORA), LocalTime.parse(franja.fin(), HORA)))
                .toList();
        horarios.saveAll(entidades);
        horarios.flush();

        List<FranjaDto> ordenadas = peticion.franjas().stream()
                .sorted(Comparator.comparingInt((FranjaDto f) -> f.diaSemana()).thenComparing(FranjaDto::inicio))
                .toList();
        return new HorarioDto(ordenadas);
    }

    @Transactional(readOnly = true)
    public HorarioDto listar() {
        List<FranjaDto> franjas = horarios.findByOdontologoIdOrderByDiaSemanaHoraInicio(actual.id()).stream()
                .map(horario -> new FranjaDto(horario.getDiaSemana(), horario.getHoraInicio().format(HORA),
                        horario.getHoraFin().format(HORA)))
                .toList();
        return new HorarioDto(franjas);
    }
}
