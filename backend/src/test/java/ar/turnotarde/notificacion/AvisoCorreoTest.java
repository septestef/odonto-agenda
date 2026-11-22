package ar.turnotarde.notificacion;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnotarde.turno.CanceladoPor;
import ar.turnotarde.turno.OrigenTurno;
import ar.turnotarde.turno.eventos.ResumenTurno;
import ar.turnotarde.turno.eventos.TurnoCancelado;
import ar.turnotarde.turno.eventos.TurnoReprogramado;
import ar.turnotarde.turno.eventos.TurnoReservado;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AvisoCorreoTest {
    private static final Instant INICIO = Instant.parse("2026-11-27T12:00:00Z");
    private static final ResumenTurno ANTERIOR = turno(1, "anterior@example.test", INICIO);
    private static final ResumenTurno NUEVO = turno(2, "nuevo+turno@example.test", INICIO.plusSeconds(1800));

    @Test
    void confirmacionYCancelacionUsanSuSnapshotConAsuntoFijo() {
        var reserva = AvisoCorreo.desde(new TurnoReservado(ANTERIOR, OrigenTurno.ONLINE, "token-viejo")).orElseThrow();
        assertThat(reserva.turno()).isEqualTo(ANTERIOR);
        assertThat(reserva.asunto()).isEqualTo("Confirmaste tu turno");
        assertThat(reserva.destinatario()).isEqualTo(ANTERIOR.pacienteEmail());
        assertThat(reserva.token()).isEqualTo("token-viejo");

        var cancelacion = AvisoCorreo.desde(new TurnoCancelado(ANTERIOR, CanceladoPor.PACIENTE, INICIO)).orElseThrow();
        assertThat(cancelacion.turno()).isEqualTo(ANTERIOR);
        assertThat(cancelacion.asunto()).isEqualTo("Cancelamos tu turno");
        assertThat(cancelacion.destinatario()).isEqualTo(ANTERIOR.pacienteEmail());
        assertThat(cancelacion.token()).isNull();
    }

    @Test
    void reprogramacionSoloUsaElSnapshotNuevoYSuToken() {
        var aviso = AvisoCorreo.desde(new TurnoReprogramado(ANTERIOR, NUEVO, "token-nuevo")).orElseThrow();
        assertThat(aviso.turno()).isEqualTo(NUEVO);
        assertThat(aviso.asunto()).isEqualTo("Reprogramamos tu turno");
        assertThat(aviso.destinatario()).isEqualTo(NUEVO.pacienteEmail());
        assertThat(aviso.token()).isEqualTo("token-nuevo");
        assertThat(aviso.toString()).doesNotContain(ANTERIOR.pacienteEmail(), NUEVO.pacienteEmail(), "token-nuevo");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "sin-arroba", "@example.test", "a..b@example.test",
            "Nombre <uno@example.test>", "uno@example.test,dos@example.test", "uno@example.test;dos@example.test",
            "uno@example.test\r\nBcc: tercero@example.test", "uno@example.test\n", " uno@example.test"})
    void noAdmiteEmailInvalidoNiUsaContactoAlternativo(String email) {
        ResumenTurno sinEmail = turno(3, email, INICIO);
        assertThat(AvisoCorreo.desde(new TurnoReservado(sinEmail, OrigenTurno.ONLINE, "token"))).isEmpty();
        assertThat(AvisoCorreo.desde(new TurnoCancelado(sinEmail, CanceladoPor.PACIENTE, INICIO))).isEmpty();
        assertThat(AvisoCorreo.desde(new TurnoReprogramado(ANTERIOR, sinEmail, "token"))).isEmpty();
    }

    private static ResumenTurno turno(long id, String email, Instant inicio) {
        return new ResumenTurno(id, 1, inicio, inicio.plusSeconds(1800), id, "Prestación " + id, 30,
                "Paciente", "Sintético", email, "000", "Dental", "Prueba", "Dirección " + id);
    }
}
