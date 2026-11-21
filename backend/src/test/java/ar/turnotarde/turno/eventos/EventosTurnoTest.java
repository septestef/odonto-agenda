package ar.turnotarde.turno.eventos;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnotarde.turno.CanceladoPor;
import ar.turnotarde.turno.OrigenTurno;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EventosTurnoTest {

    private static final String TOKEN = "tokenSecretoEnClaroDe43CaracteresXXXXXXXXXXXX";

    private static ResumenTurno resumen(long id) {
        return new ResumenTurno(id, 1L, Instant.parse("2026-11-27T12:00:00Z"), Instant.parse("2026-11-27T12:30:00Z"),
                3L, "Consulta", 30, "Luis", "Gómez", "luis@example.com", "+5493511234567",
                "Ana", "Pérez", "Av. Colón 1234, Córdoba");
    }

    @Test
    void turnoReservadoNoMuestraElTokenEnSuTexto() {
        TurnoReservado evento = new TurnoReservado(resumen(10), OrigenTurno.ONLINE, TOKEN);

        assertThat(evento.toString()).doesNotContain(TOKEN).contains("ONLINE").contains("turnoId=10");
        assertThat(evento.token()).isEqualTo(TOKEN);
    }

    @Test
    void turnoReprogramadoNoMuestraElTokenNuevoEnSuTexto() {
        TurnoReprogramado evento = new TurnoReprogramado(resumen(10), resumen(11), TOKEN);

        assertThat(evento.toString()).doesNotContain(TOKEN).contains("turnoId=10").contains("turnoId=11");
        assertThat(evento.token()).isEqualTo(TOKEN);
    }

    @Test
    void turnoCanceladoConservaQuienYCuandoCancelo() {
        Instant cuando = Instant.parse("2026-11-26T10:00:00Z");
        TurnoCancelado evento = new TurnoCancelado(resumen(10), CanceladoPor.PACIENTE, cuando);

        assertThat(evento.canceladoPor()).isEqualTo(CanceladoPor.PACIENTE);
        assertThat(evento.canceladoEn()).isEqualTo(cuando);
        assertThat(evento.turno().pacienteEmail()).isEqualTo("luis@example.com");
    }
}
