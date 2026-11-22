package ar.turnotarde.paciente;

import ar.turnotarde.paciente.dto.PacienteRespuesta;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PacienteRepository extends JpaRepository<Paciente, Long> {
    long countByOdontologoId(Long odontologoId);

    @Query("""
            select new ar.turnotarde.paciente.dto.PacienteRespuesta(
                    p.id, p.dni, p.nombre, p.apellido, p.telefono, p.email)
            from Paciente p where p.odontologoId = :odontologoId
            order by p.apellido, p.nombre, p.id""")
    List<PacienteRespuesta> paginaPropia(@Param("odontologoId") Long odontologoId, Pageable pagina);
}
