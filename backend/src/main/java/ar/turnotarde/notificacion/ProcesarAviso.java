package ar.turnotarde.notificacion;

import java.time.Clock;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
class ProcesarAviso {
    private static final Logger LOG = LoggerFactory.getLogger(ProcesarAviso.class);
    private final Clock reloj;
    private final CableadoSmtp smtp;
    private final PlantillasCorreo plantillas;
    private final TransporteCorreo transporte;
    private final RegistroNotificacion registro;

    ProcesarAviso(Clock reloj, CableadoSmtp smtp, PlantillasCorreo plantillas, TransporteCorreo transporte,
            RegistroNotificacion registro) {
        this.reloj = reloj;
        this.smtp = smtp;
        this.plantillas = plantillas;
        this.transporte = transporte;
        this.registro = registro;
    }

    void procesar(Supplier<Optional<AvisoCorreo>> candidato) {
        var instante = reloj.instant();
        var posible = candidato.get();
        if (posible.isEmpty()) {
            LOG.warn("DESTINATARIO_INVALIDO");
            return;
        }
        var aviso = posible.orElseThrow();
        var error = intentar(aviso);
        try {
            registro.registrar(aviso.turno().turnoId(), aviso.tipo(), aviso.destinatario(), instante, error);
        } catch (RuntimeException ex) {
            LOG.warn("REGISTRO_FALLIDO");
        }
    }

    private ErrorNotificacion intentar(AvisoCorreo aviso) {
        if (!smtp.valida()) {
            return ErrorNotificacion.CONFIG_SMTP;
        }
        String html;
        try {
            html = plantillas.renderizar(aviso.tipo(), aviso.turno(), aviso.token());
        } catch (PlantillasCorreo.UrlCorreoInvalida ex) {
            return ErrorNotificacion.URL_INVALIDA;
        } catch (RuntimeException ex) {
            return ErrorNotificacion.RENDER;
        }
        try {
            transporte.enviar(aviso, html);
            return null;
        } catch (TransporteCorreo.MensajeInvalido ex) {
            return ErrorNotificacion.RENDER;
        } catch (RuntimeException ex) {
            // Un timeout no permite saber si el proveedor aceptó. No reenviar.
            return ErrorNotificacion.SMTP_FALLO;
        }
    }
}
