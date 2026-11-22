package ar.turnotarde.auth;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnotarde.auth.ControlIntentosLogin.EstadoBloqueo;
import ar.turnotarde.soporte.RelojDePrueba;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** D7 y D12: el contador de intentos fallidos es por email, atómico y usa el reloj inyectado. */
class ControlIntentosLoginTest {

    private static final String EMAIL = "demo@example.com";
    private static final Instant INICIO = Instant.parse("2026-11-21T12:00:00Z");

    private RelojDePrueba reloj;
    private ControlIntentosLogin control;

    @BeforeEach
    void preparar() {
        reloj = new RelojDePrueba(INICIO);
        control = new ControlIntentosLogin(reloj);
    }

    @Test
    void unEmailNuevoNoEstaBloqueado() {
        assertThat(control.estado(EMAIL).bloqueado()).isFalse();
    }

    @Test
    void losCuatroPrimerosFallosNoBloquean() {
        for (int intento = 1; intento <= 4; intento++) {
            EstadoBloqueo estado = control.registrarFallo(EMAIL);
            assertThat(estado.bloqueado()).as("fallo %d", intento).isFalse();
        }
        assertThat(control.estado(EMAIL).bloqueado()).isFalse();
    }

    @Test
    void elQuintoFalloBloqueaQuinceMinutos() {
        for (int intento = 1; intento <= 4; intento++) {
            control.registrarFallo(EMAIL);
        }

        EstadoBloqueo estado = control.registrarFallo(EMAIL);

        assertThat(estado.bloqueado()).isTrue();
        assertThat(estado.restante()).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    void unNuevoFalloDuranteElBloqueoNoReiniciaElTiempo() {
        for (int intento = 1; intento <= 5; intento++) {
            control.registrarFallo(EMAIL);
        }
        reloj.avanzar(Duration.ofMinutes(2));

        EstadoBloqueo estado = control.registrarFallo(EMAIL);

        assertThat(estado.restante()).isEqualTo(Duration.ofMinutes(13));
    }

    @Test
    void justoAntesDelVencimientoSigueBloqueadoYJustoDespuesNo() {
        for (int intento = 1; intento <= 5; intento++) {
            control.registrarFallo(EMAIL);
        }

        reloj.fijar(INICIO.plus(Duration.ofMinutes(15)).minusNanos(1));
        assertThat(control.estado(EMAIL).bloqueado()).isTrue();

        reloj.fijar(INICIO.plus(Duration.ofMinutes(15)));
        assertThat(control.estado(EMAIL).bloqueado()).isFalse();
        assertThat(control.registrarFallo(EMAIL).bloqueado()).isFalse();
    }

    @Test
    void unLoginExitosoReiniciaElContador() {
        control.registrarFallo(EMAIL);
        control.registrarFallo(EMAIL);
        control.registrarFallo(EMAIL);
        control.registrarExito(EMAIL);

        assertThat(control.estado(EMAIL).bloqueado()).isFalse();
        control.registrarFallo(EMAIL);
        control.registrarFallo(EMAIL);
        control.registrarFallo(EMAIL);
        assertThat(control.registrarFallo(EMAIL).bloqueado()).isFalse();
        assertThat(control.registrarFallo(EMAIL).bloqueado()).isTrue();
    }

    @Test
    void emailsDistintosNoCompartenElBloqueo() {
        for (int intento = 1; intento <= 5; intento++) {
            control.registrarFallo(EMAIL);
        }

        assertThat(control.estado("otra@example.com").bloqueado()).isFalse();
        assertThat(control.registrarFallo("otra@example.com").bloqueado()).isFalse();
    }

    @Test
    void mayusculasYEspaciosNoCreanEntradasDistintas() {
        control.registrarFallo("  Demo@Example.COM  ");

        assertThat(control.estado(EMAIL).bloqueado()).isFalse();
        control.registrarFallo(EMAIL);
        control.registrarFallo("DEMO@EXAMPLE.COM");
        control.registrarFallo(EMAIL);
        assertThat(control.registrarFallo(" demo@example.com ").bloqueado()).isTrue();
    }

    @Test
    void unEmailQueNoExisteTambienSeBloquea() {
        for (int intento = 1; intento <= 4; intento++) {
            control.registrarFallo("nadie@example.com");
        }

        assertThat(control.registrarFallo("nadie@example.com").bloqueado()).isTrue();
    }

    @Test
    void lasEntradasVencidasSeDescartanAlConsultarlas() {
        for (int intento = 1; intento <= 5; intento++) {
            control.registrarFallo(EMAIL);
        }
        reloj.avanzar(Duration.ofMinutes(16));

        assertThat(control.estado(EMAIL).bloqueado()).isFalse();
        // Después de vencer, un solo fallo debe contar como el primero.
        assertThat(control.registrarFallo(EMAIL).bloqueado()).isFalse();
        for (int intento = 1; intento <= 3; intento++) {
            control.registrarFallo(EMAIL);
        }
        assertThat(control.registrarFallo(EMAIL).bloqueado()).isTrue();
    }
}
