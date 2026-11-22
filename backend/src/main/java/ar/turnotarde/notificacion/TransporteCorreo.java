package ar.turnotarde.notificacion;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
class TransporteCorreo {
    private final CableadoSmtp smtp;
    private final JavaMailSender sender;

    TransporteCorreo(CableadoSmtp smtp, JavaMailSender sender) {
        this.smtp = smtp;
        this.sender = sender;
    }

    void enviar(AvisoCorreo aviso, String html) {
        if (!smtp.valida()) {
            throw new IllegalStateException("CONFIG_SMTP");
        }
        MimeMessage mensaje;
        try {
            mensaje = sender.createMimeMessage();
            var mime = new MimeMessageHelper(mensaje, false, StandardCharsets.UTF_8.name());
            mime.setFrom(smtp.usuario(), smtp.nombre());
            mime.setTo(aviso.destinatario());
            mime.setSubject(aviso.asunto());
            mime.setText(html, true);
        } catch (MessagingException | UnsupportedEncodingException | RuntimeException ex) {
            throw new MensajeInvalido();
        }
        sender.send(mensaje);
    }

    static final class MensajeInvalido extends RuntimeException {
    }
}
