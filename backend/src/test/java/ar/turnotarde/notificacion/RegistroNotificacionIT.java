package ar.turnotarde.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import ar.turnotarde.turno.OrigenTurno;
import ar.turnotarde.turno.eventos.ResumenTurno;
import ar.turnotarde.turno.eventos.TurnoReservado;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(OutputCaptureExtension.class)
class RegistroNotificacionIT extends IntegracionTest {
    private static final Instant INICIO = Instant.parse("2026-11-27T12:00:00Z");
    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-25T12:03:00Z"), ZoneOffset.UTC);
    private static final String DESTINO = "auditoria@example.test";
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transacciones;
    @Autowired RegistroNotificacion registro;
    private long turno;

    @BeforeEach
    void preparar() {
        LimpiezaBase.limpiar(jdbc);
        var datos = new FixturesJdbc(jdbc);
        long profesional = datos.odontologo("auditoria");
        turno = datos.turno(profesional, datos.paciente(profesional, "88888888"),
                datos.prestacion(profesional, "Consulta", 30), INICIO, INICIO.plusSeconds(1800), "RESERVADO");
    }

    @AfterEach
    void limpiar() {
        LimpiezaBase.limpiar(jdbc);
    }

    @Test
    void registraLosResultadosEnUtcYAunSiSeRevierteLaTransaccionExterior() {
        new TransactionTemplate(transacciones).executeWithoutResult(exterior -> {
            registrar(TipoNotificacion.CONFIRMACION, null);
            registrar(TipoNotificacion.CANCELACION, ErrorNotificacion.SMTP_FALLO);
            registrar(TipoNotificacion.REPROGRAMACION, ErrorNotificacion.URL_INVALIDA);
            exterior.setRollbackOnly();
        });

        assertThat(jdbc.query("""
                SELECT turno_id, tipo, destinatario, enviado_en, estado, error FROM notificacion ORDER BY id
                """, (rs, n) -> new Registro(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getObject(4, OffsetDateTime.class), rs.getString(5), rs.getString(6))))
                .containsExactly(new Registro(turno, "CONFIRMACION", DESTINO, RELOJ.instant().atOffset(ZoneOffset.UTC),
                                "ENVIADO", null),
                        new Registro(turno, "CANCELACION", DESTINO, RELOJ.instant().atOffset(ZoneOffset.UTC),
                                "ERROR", "SMTP_FALLO"),
                        new Registro(turno, "REPROGRAMACION", DESTINO, RELOJ.instant().atOffset(ZoneOffset.UTC),
                                "ERROR", "URL_INVALIDA"));
        assertThat(jdbc.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'notificacion'
                """, String.class)).containsExactlyInAnyOrderElementsOf(List.of("id", "turno_id", "tipo",
                "destinatario", "enviado_en", "estado", "error", "creado_en", "actualizado_en"));
        assertThat(jdbc.queryForObject("""
                SELECT data_type || ':' || is_nullable FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = 'notificacion' AND column_name = 'enviado_en'
                """, String.class)).isEqualTo("timestamp with time zone:NO");
    }

    private void registrar(TipoNotificacion tipo, ErrorNotificacion categoria) {
        registro.registrar(turno, tipo, DESTINO, RELOJ.instant(), categoria);
    }

    @Test
    void unaEscrituraRechazadaPorPostgresqlNoLogueaLaFila(CapturedOutput salida) {
        jdbc.execute("ALTER TABLE notificacion ADD CONSTRAINT rechazo_prueba CHECK (false)");
        try {
            var evento = new TurnoReservado(new ResumenTurno(turno, 1, INICIO, INICIO.plusSeconds(1800),
                    1, "Consulta", 30, "Paciente", "Sintético", DESTINO, "000", "Profesional", "Prueba", null),
                    OrigenTurno.ONLINE, "token-centinela");
            var plantillas = mock(PlantillasCorreo.class);
            when(plantillas.renderizar(any(), any(), any())).thenReturn("<p>Sintético</p>");
            new ProcesarAviso(RELOJ, new CableadoSmtp("smtp.example.test", "587", "origen@example.test", "sintético",
                    "Sintético"), plantillas, mock(TransporteCorreo.class), registro)
                    .procesar(() -> AvisoCorreo.desde(evento));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion", Long.class)).isZero();
            assertThat(salida.getAll().contains("REGISTRO_FALLIDO")).as("diagnóstico cerrado presente").isTrue();
            assertThat(salida.getAll().contains(DESTINO)).as("el log no revela el destinatario").isFalse();
        } finally {
            jdbc.execute("ALTER TABLE notificacion DROP CONSTRAINT rechazo_prueba");
        }
    }

    private record Registro(long turno, String tipo, String destino, OffsetDateTime intento, String estado,
                            String categoria) {
    }
}
