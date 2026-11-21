package ar.turnotarde.agenda;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HorarioAtencionRepository extends JpaRepository<HorarioAtencion, Long> {

    /** Franjas de un día de la semana ISO (1 = lunes), por hora de inicio. */
    List<HorarioAtencion> findByOdontologoIdAndDiaSemanaOrderByHoraInicio(Long odontologoId, short diaSemana);
}
