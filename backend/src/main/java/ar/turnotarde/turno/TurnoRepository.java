package ar.turnotarde.turno;

import ar.turnotarde.agenda.Intervalo;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TurnoRepository extends JpaRepository<Turno, Long> {

    @Query("""
            select new ar.turnotarde.turno.TurnoAgendaProyeccion(t.id, t.inicio, t.fin, t.estado, t.origen,
                    p.id, p.dni, p.nombre, p.apellido, p.telefono, p.email,
                    pr.id, pr.nombre, t.canceladoPor, t.canceladoEn)
            from Turno t join t.paciente p join t.prestacion pr
            where t.odontologoId = :odontologoId and t.inicio < :hasta and t.fin > :desde
            order by t.inicio, t.id""")
    List<TurnoAgendaProyeccion> agenda(@Param("odontologoId") Long odontologoId,
            @Param("desde") Instant desde, @Param("hasta") Instant hasta);

    Optional<Turno> findByTokenHash(String tokenHash);

    @Query(value = "SELECT * FROM turno WHERE token_hash = :hash FOR UPDATE", nativeQuery = true)
    Optional<Turno> bloquearPorHash(@Param("hash") String hash);

    @Query(value = "SELECT * FROM turno WHERE id = :id AND odontologo_id = :dueno FOR UPDATE", nativeQuery = true)
    Optional<Turno> bloquearPropio(@Param("id") Long id, @Param("dueno") Long dueno);

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

    /**
     * C-05: turnos afectados por un bloqueo. Mismo predicado semiabierto que
     * {@link #intervalosActivosQueCruzan(Long, Instant, Instant)}, pero trayendo solo los datos
     * que la UI necesita para decidir si cancela (nunca DNI, email, teléfono ni token).
     */
    @Query("""
            select new ar.turnotarde.agenda.TurnoAfectadoProyeccion(t.id, t.inicio, t.fin, t.estado,
                    p.nombre, p.apellido, pr.nombre) from Turno t
            join t.paciente p join t.prestacion pr
            where t.odontologoId = :odontologoId
              and t.estado in (ar.turnotarde.turno.EstadoTurno.RESERVADO,
                               ar.turnotarde.turno.EstadoTurno.ATENDIDO,
                               ar.turnotarde.turno.EstadoTurno.AUSENTE)
              and t.inicio < :fin and t.fin > :inicio
            order by t.inicio""")
    List<ar.turnotarde.agenda.TurnoAfectadoProyeccion> turnosAfectados(
            @Param("odontologoId") Long odontologoId, @Param("inicio") Instant inicio, @Param("fin") Instant fin);
}
