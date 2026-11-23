package ar.turnotarde.turno;

import ar.turnotarde.turno.dto.WhatsAppRespuesta;
import jakarta.validation.constraints.Positive;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/turnos")
class ControladorWhatsApp {
    private final ConsultaWhatsAppService whatsapp;

    ControladorWhatsApp(ConsultaWhatsAppService whatsapp) {
        this.whatsapp = whatsapp;
    }

    @GetMapping("/{id}/whatsapp")
    public ResponseEntity<WhatsAppRespuesta> consultar(
            @PathVariable @Positive(message = "Ingresá un identificador mayor a 0.") long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(whatsapp.consultar(id));
    }
}
