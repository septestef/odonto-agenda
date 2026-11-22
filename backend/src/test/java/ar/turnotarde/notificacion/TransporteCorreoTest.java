package ar.turnotarde.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnotarde.turno.OrigenTurno;
import ar.turnotarde.turno.eventos.ResumenTurno;
import ar.turnotarde.turno.eventos.TurnoReservado;
import jakarta.mail.Message;
import jakarta.mail.internet.InternetAddress;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TransporteCorreoTest {
    private final SmtpDePrueba falso = new SmtpDePrueba();
    private final TransporteCorreo transporte = new TransporteCorreo(
            new CableadoSmtp("smtp.example.test", "587", "origen@example.test", "centinela", "Clínica Prueba"), falso);

    @Test
    void armaHtmlUtf8ConRemitenteExactoUnSoloDestinatarioYSinCopias() throws Exception {
        enviar();
        var mensaje = falso.ultimo();
        assertThat(mensaje.getContent()).isEqualTo("<p>Confirmación</p>");
        assertThat(mensaje.getContentType()).containsIgnoringCase("text/html").containsIgnoringCase("utf-8");
        assertThat(mensaje.getSubject()).isEqualTo("Confirmaste tu turno");
        assertThat(mensaje.getFrom()).hasSize(1);
        var remitente = (InternetAddress) mensaje.getFrom()[0];
        assertThat(remitente.getAddress()).isEqualTo("origen@example.test");
        assertThat(remitente.getPersonal()).isEqualTo("Clínica Prueba");
        assertThat(mensaje.getAllRecipients()).containsExactly(new InternetAddress("paciente@example.test"));
        assertThat(mensaje.getRecipients(Message.RecipientType.CC)).isNull();
        assertThat(mensaje.getRecipients(Message.RecipientType.BCC)).isNull();
    }

    @Test
    void doblePuedeSimularFallaSinSalidaDeRed() {
        falso.accion(() -> {
            throw new IllegalStateException("SMTP centinela");
        });
        assertThatThrownBy(this::enviar).isInstanceOf(IllegalStateException.class).hasMessage("SMTP centinela");
    }

    private void enviar() {
        Instant inicio = Instant.parse("2026-11-27T12:00:00Z");
        var turno = new ResumenTurno(1, 1, inicio, inicio.plusSeconds(1800), 1, "Limpieza", 30,
                "Paciente", "Prueba", "paciente@example.test", "000", "Dentista", "Prueba", null);
        var aviso = AvisoCorreo.desde(new TurnoReservado(turno, OrigenTurno.ONLINE, "token")).orElseThrow();
        transporte.enviar(aviso, "<p>Confirmación</p>");
    }
}
