package ar.turnotarde.prestacion;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrestacionRepository extends JpaRepository<Prestacion, Long> {

    Optional<Prestacion> findByIdAndOdontologoIdAndActivaTrue(Long id, Long odontologoId);

    List<Prestacion> findByOdontologoIdAndActivaTrueOrderByNombre(Long odontologoId);

    // C-05: métodos nuevos, sin alterar las firmas que consume C-07.

    /** Busca por id dentro del odontólogo de la sesión, activa o dada de baja. */
    Optional<Prestacion> findByIdAndOdontologoId(Long id, Long odontologoId);

    /** Incluye inactivas para que la UI pueda mostrar el estado (RN-AG-04). */
    List<Prestacion> findByOdontologoIdOrderByNombre(Long odontologoId);

    boolean existsByOdontologoIdAndNombre(Long odontologoId, String nombre);
}
