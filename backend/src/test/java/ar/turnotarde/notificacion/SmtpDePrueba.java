package ar.turnotarde.notificacion;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
class SmtpDePrueba extends JavaMailSenderImpl {
    private final AtomicReference<MimeMessage> ultimo = new AtomicReference<>();
    private final AtomicInteger intentos = new AtomicInteger();
    private volatile Runnable accion = () -> {};

    MimeMessage ultimo() {
        return ultimo.get();
    }

    void accion(Runnable accion) {
        this.accion = accion;
    }

    int intentos() {
        return intentos.get();
    }

    void limpiar() {
        ultimo.set(null);
        intentos.set(0);
        accion = () -> {};
    }

    @Override
    protected void doSend(MimeMessage[] mensajes, Object[] originales) {
        for (MimeMessage mensaje : mensajes) {
            try {
                mensaje.saveChanges();
            } catch (MessagingException ex) {
                throw new MailPreparationException("Mensaje de prueba inválido");
            }
            ultimo.set(mensaje);
            intentos.incrementAndGet();
            accion.run();
        }
    }
}
