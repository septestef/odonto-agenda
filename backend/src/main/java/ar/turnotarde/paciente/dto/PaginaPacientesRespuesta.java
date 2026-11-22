package ar.turnotarde.paciente.dto;

import java.util.List;

public record PaginaPacientesRespuesta(List<PacienteRespuesta> contenido, int page, int size,
        long totalElementos, long totalPaginas) {
}
