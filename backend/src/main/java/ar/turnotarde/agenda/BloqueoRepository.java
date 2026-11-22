package ar.turnotarde.agenda;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BloqueoRepository extends JpaRepository<Bloqueo, Long> {

    /** Bloqueos que cruzan {@code [desde, hasta)}, proyectados a intervalos (sin el motivo, RN-SE-03). */
    @Query("""
            select new ar.turnotarde.agenda.Intervalo(b.inicio, b.fin) from Bloqueo b
            where b.odontologoId = :odontologoId and b.inicio < :hasta and b.fin > :desde""")
    List<Intervalo> intervalosQueCruzan(@Param("odontologoId") Long odontologoId, @Param("desde") Instant desde,
            @Param("hasta") Instant hasta);

    // C-05: métodos nuevos, sin alterar la firma que consume C-07.

    List<Bloqueo> findByOdontologoIdOrderByInicio(Long odontologoId);

    Optional<Bloqueo> findByIdAndOdontologoId(Long id, Long odontologoId);
}
