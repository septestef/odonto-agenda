package ar.turnotarde.agenda;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HorarioAtencionRepository extends JpaRepository<HorarioAtencion, Long> {

    /** Franjas de un día de la semana ISO (1 = lunes), por hora de inicio. */
    List<HorarioAtencion> findByOdontologoIdAndDiaSemanaOrderByHoraInicio(Long odontologoId, short diaSemana);

    // C-05: el dueño siempre sale de la sesión; estos métodos nunca mezclan odontólogos.

    /** Horario semanal completo, ordenado por día y hora. JPQL explícita: `diaSemana` es un short. */
    @Query("select h from HorarioAtencion h where h.odontologoId = :odontologoId order by h.diaSemana, h.horaInicio")
    List<HorarioAtencion> findByOdontologoIdOrderByDiaSemanaHoraInicio(
            @Param("odontologoId") Long odontologoId);

    /** Borra todas las franjas del odontólogo antes de reemplazar el horario. */
    void deleteByOdontologoId(Long odontologoId);
}
