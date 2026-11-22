package ar.turnotarde.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import ar.turnotarde.turno.eventos.ResumenTurno;
import ar.turnotarde.turno.OrigenTurno;
import ar.turnotarde.turno.eventos.TurnoReservado;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mail.MailSendException;

@ExtendWith(OutputCaptureExtension.class)
class ProcesarAvisoTest {
    private static final Instant AHORA = Instant.parse("2026-11-25T12:01:00Z");
    private static final String TOKEN = "token-centinela";
    private static final String HTML = "<p>cuerpo-centinela</p>";
    private static final String EMAIL = "paciente-centinela@example.test";
    private static final String PRIVADO = "87654321 nombre-centinela clave-centinela +54900000 "
            + "direccion-centinela " + EMAIL + " https://example.test/turno/" + TOKEN + " " + HTML;
    private static final CableadoSmtp CONFIG =
            new CableadoSmtp("smtp.example.test", "587", "origen@example.test", "clave-centinela", "Clínica");
    private final PlantillasCorreo plantillas = mock(PlantillasCorreo.class);
    private final TransporteCorreo transporte = mock(TransporteCorreo.class);
    private final RegistroNotificacion registro = mock(RegistroNotificacion.class);

    @Test
    void clasificaSoloLasCategoriasCerradasYCantidadUnica(CapturedOutput salida) {
        assertThat(ErrorNotificacion.values()).containsExactly(ErrorNotificacion.CONFIG_SMTP,
                ErrorNotificacion.URL_INVALIDA, ErrorNotificacion.RENDER, ErrorNotificacion.SMTP_FALLO);
        var turno = turno(EMAIL);
        procesar(new CableadoSmtp(null, null, null, null, null), turno);
        verify(registro).registrar(19, TipoNotificacion.CONFIRMACION, EMAIL, AHORA, ErrorNotificacion.CONFIG_SMTP);
        verifyNoInteractions(plantillas, transporte);

        when(plantillas.renderizar(TipoNotificacion.CONFIRMACION, turno, TOKEN))
                .thenThrow(new PlantillasCorreo.UrlCorreoInvalida())
                .thenThrow(new IllegalStateException(PRIVADO))
                .thenReturn(HTML);
        procesar(CONFIG, turno);
        procesar(CONFIG, turno);
        verify(registro).registrar(19, TipoNotificacion.CONFIRMACION, EMAIL, AHORA, ErrorNotificacion.URL_INVALIDA);
        verify(registro).registrar(19, TipoNotificacion.CONFIRMACION, EMAIL, AHORA, ErrorNotificacion.RENDER);
        verifyNoInteractions(transporte);

        doThrow(new MailSendException(PRIVADO)).when(transporte).enviar(any(), any());
        procesar(CONFIG, turno);
        verify(registro).registrar(19, TipoNotificacion.CONFIRMACION, EMAIL, AHORA, ErrorNotificacion.SMTP_FALLO);
        verify(transporte).enviar(new AvisoCorreo(TipoNotificacion.CONFIRMACION, turno, TOKEN), HTML);
        verifyNoMoreInteractions(registro, transporte);
        sinDatos(salida);
    }

    @Test
    void destinatarioAusenteOInvalidoSoloEmiteDiagnostico(CapturedOutput salida) {
        procesar(CONFIG, turno(null));
        procesar(CONFIG, turno(EMAIL + "\r\nBcc: tercero@example.test"));
        verifyNoInteractions(plantillas, transporte, registro);
        assertThat(salida.getAll()).contains("DESTINATARIO_INVALIDO").doesNotContain("tercero@example.test", "Bcc:");
        sinDatos(salida);
    }

    @Test
    void aceptadoConEscrituraFallidaNoReintentaNiReenvia(CapturedOutput salida) {
        var turno = turno(EMAIL);
        when(plantillas.renderizar(TipoNotificacion.CONFIRMACION, turno, TOKEN)).thenReturn(HTML);
        doThrow(new IllegalStateException(PRIVADO)).when(registro).registrar(anyLong(), any(), any(), any(), any());
        procesar(CONFIG, turno);
        verify(registro).registrar(19, TipoNotificacion.CONFIRMACION, EMAIL, AHORA, null);
        verify(transporte).enviar(new AvisoCorreo(TipoNotificacion.CONFIRMACION, turno, TOKEN), HTML);
        verifyNoMoreInteractions(registro, transporte);
        assertThat(salida.getAll()).containsOnlyOnce("REGISTRO_FALLIDO");
        sinDatos(salida);
    }

    private void procesar(CableadoSmtp config, ResumenTurno turno) {
        new ProcesarAviso(Clock.fixed(AHORA, ZoneOffset.UTC), config, plantillas, transporte, registro)
                .procesar(() -> AvisoCorreo.desde(new TurnoReservado(turno, OrigenTurno.ONLINE, TOKEN)));
    }

    private static ResumenTurno turno(String email) {
        return new ResumenTurno(19, 31, AHORA, AHORA.plusSeconds(1800), 2, "prestacion-centinela", 30,
                "nombre-centinela", "apellido-centinela", email, "+54900000", "dentista-centinela", "apellido-centinela",
                "direccion-centinela");
    }

    private static void sinDatos(CapturedOutput salida) {
        assertThat(salida.getAll()).doesNotContain("87654321", "nombre-centinela", "apellido-centinela", EMAIL,
                "clave-centinela", "+54900000", "direccion-centinela", "prestacion-centinela", "dentista-centinela",
                TOKEN, "https://example.test", HTML, "MailSendException", "IllegalStateException");
    }
}
