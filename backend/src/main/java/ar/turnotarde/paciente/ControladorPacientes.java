package ar.turnotarde.paciente;

import ar.turnotarde.paciente.dto.PaginaPacientesRespuesta;
import ar.turnotarde.shared.error.CodigosError;
import ar.turnotarde.shared.error.ReglaNegocioException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pacientes")
public class ControladorPacientes {
    private final ConsultaPacientesService pacientes;

    public ControladorPacientes(ConsultaPacientesService pacientes) {
        this.pacientes = pacientes;
    }

    @GetMapping
    public PaginaPacientesRespuesta consultar(@RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        return pacientes.consultar(entero(page, 0), entero(size, 20));
    }

    private static int entero(String valor, int porAusencia) {
        if (valor == null) {
            return porAusencia;
        }
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            throw new ReglaNegocioException(CodigosError.SOLICITUD_INVALIDA,
                    "No pudimos interpretar la solicitud.", HttpStatus.BAD_REQUEST);
        }
    }
}
