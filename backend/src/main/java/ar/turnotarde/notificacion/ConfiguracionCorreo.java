package ar.turnotarde.notificacion;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.javamail.JavaMailSenderImpl;

@Configuration(proxyBeanMethods = false)
class ConfiguracionCorreo {
    @Bean
    CableadoSmtp cableadoSmtp(Environment entorno) {
        var smtp = new CableadoSmtp(entorno.getProperty("MAIL_HOST"), entorno.getProperty("MAIL_PORT"),
                entorno.getProperty("MAIL_USERNAME"), entorno.getProperty("MAIL_PASSWORD"),
                entorno.getProperty("MAIL_FROM_NAME"));
        String error = smtp.variableInvalida();
        if (error != null && entorno.acceptsProfiles(Profiles.of("demo"))) {
            throw new IllegalStateException("Configuración SMTP inválida: " + error);
        }
        return smtp;
    }

    @Bean
    @Profile("!test")
    JavaMailSenderImpl javaMailSender(CableadoSmtp smtp) {
        var sender = new JavaMailSenderImpl();
        if (smtp.valida()) {
            sender.setHost(smtp.host());
            sender.setPort(smtp.numeroPuerto());
            sender.setUsername(smtp.usuario());
            sender.setPassword(smtp.clave());
        }
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        sender.getJavaMailProperties().putAll(Map.of(
                "mail.smtp.auth", "true",
                "mail.smtp.starttls.enable", "true",
                "mail.smtp.starttls.required", "true",
                "mail.smtp.ssl.checkserveridentity", "true",
                "mail.smtp.connectiontimeout", "5000",
                "mail.smtp.timeout", "5000",
                "mail.smtp.writetimeout", "5000",
                "mail.debug", "false"));
        return sender;
    }
}
