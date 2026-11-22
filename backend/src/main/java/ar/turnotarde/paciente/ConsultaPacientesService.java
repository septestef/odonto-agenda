package ar.turnotarde.paciente;

import ar.turnotarde.paciente.dto.PaginaPacientesRespuesta;
import ar.turnotarde.paciente.dto.PacienteRespuesta;
import ar.turnotarde.shared.seguridad.OdontologoActual;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsultaPacientesService {
    private final PacienteRepository pacientes;
    private final OdontologoActual odontologo;

    public ConsultaPacientesService(PacienteRepository pacientes, OdontologoActual odontologo) {
        this.pacientes = pacientes;
        this.odontologo = odontologo;
    }

    @Transactional(readOnly = true)
    public PaginaPacientesRespuesta consultar(int page, int size) {
        var paginacion = new PaginacionPacientes(page, size);
        long dueno = odontologo.id();
        long total = pacientes.countByOdontologoId(dueno);
        List<PacienteRespuesta> contenido = paginacion.offset() >= total ? List.of()
                : pacientes.paginaPropia(dueno, PageRequest.of(paginacion.page(), paginacion.size()));
        return new PaginaPacientesRespuesta(contenido, page, size, total, Math.ceilDiv(total, size));
    }
}
