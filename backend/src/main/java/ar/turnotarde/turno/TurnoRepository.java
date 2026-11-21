package ar.turnotarde.turno;

import ar.turnotarde.agenda.Intervalo;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TurnoRepository extends JpaRepository<Turno, Long> {

    /**
     * Turnos que ocupan lugar ({@code RESERVADO}, {@code ATENDIDO}, {@code AUSENTE}) y cruzan {@code [desde, hasta)},
     * proyectados a intervalos: nunca se cargan datos del paciente (RN-SE-03).
     */
    @Query("""
            select new ar.turnotarde.agenda.Intervalo(t.inicio, t.fin) from Turno t
            where t.odontologoId = :odontologoId and t.estado <> ar.turnotarde.turno.EstadoTurno.CANCELADO
              and t.inicio < :hasta and t.fin > :desde""")
    List<Intervalo> intervalosActivosQueCruzan(@Param("odontologoId") Long odontologoId,
            @Param("desde") Instant desde, @Param("hasta") Instant hasta);

    /** Turnos {@code RESERVADO} futuros del paciente con ese odontólogo (RN-PA-03). */
    @Query("""
            select count(t) from Turno t
            where t.odontologoId = :odontologoId and t.paciente.id = :pacienteId
              and t.estado = ar.turnotarde.turno.EstadoTurno.RESERVADO and t.inicio > :ahora""")
    long contarReservadosFuturos(@Param("odontologoId") Long odontologoId, @Param("pacienteId") Long pacienteId,
            @Param("ahora") Instant ahora);
}
