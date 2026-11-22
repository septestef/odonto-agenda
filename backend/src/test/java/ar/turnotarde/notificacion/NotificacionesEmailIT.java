package ar.turnotarde.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnotarde.soporte.ConfiguracionRelojDePrueba;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import ar.turnotarde.soporte.RelojDePrueba;
import ar.turnotarde.turno.CanceladoPor;
import ar.turnotarde.turno.OrigenTurno;
import ar.turnotarde.turno.TokenTurno;
import ar.turnotarde.turno.eventos.ResumenTurno;
import ar.turnotarde.turno.eventos.TurnoCancelado;
import ar.turnotarde.turno.eventos.TurnoReprogramado;
import ar.turnotarde.turno.eventos.TurnoReservado;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@AutoConfigureMockMvc
@Import({ConfiguracionRelojDePrueba.class, NotificacionesEmailIT.ObservacionReprogramacion.class})
@ExtendWith(OutputCaptureExtension.class)
@DirtiesContext
@TestPropertySource(properties = {"MAIL_HOST=smtp.example.test", "MAIL_PORT=587",
        "MAIL_USERNAME=origen@example.test", "MAIL_PASSWORD=clave-centinela", "MAIL_FROM_NAME=OdontoAgenda Prueba",
        "app.base-url=https://example.test/sede/"})
class NotificacionesEmailIT extends IntegracionTest {
    private static final Instant AHORA = Instant.parse("2026-11-25T12:00:00Z");
    private static final Instant INICIO = Instant.parse("2026-11-27T12:00:00Z");
    private static final String EMAIL = "original@example.test";
    private static final String DNI = "13572468";
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired SmtpDePrueba smtp;
    @Autowired RelojDePrueba reloj;
    @Autowired ApplicationContext contexto;
    @Autowired PlatformTransactionManager gestor;
    @Autowired EventosReprogramacion eventos;
    @MockitoSpyBean RegistroNotificacion registro;
    private static final String TOKEN_CANCELACION = "E".repeat(42) + "A";
    private FixturesJdbc fixtures;
    private long profesional;
    private long prestacion;

    @BeforeEach
    void preparar() {
        vaciarEjecutor();
        LimpiezaBase.limpiar(jdbc);
        smtp.limpiar();
        eventos.reiniciar();
        reloj.fijar(AHORA);
        fixtures = new FixturesJdbc(jdbc);
        profesional = fixtures.odontologo("avisos");
        jdbc.update("UPDATE odontologo SET direccion_consultorio=? WHERE id=?", "Calle de prueba", profesional);
        prestacion = fixtures.prestacion(profesional, "Consulta", 30);
        fixtures.horario(profesional, 5, "09:00", "13:00");
    }

    @AfterEach
    void limpiar() {
        vaciarEjecutor();
        LimpiezaBase.limpiar(jdbc);
    }

    @Test
    void commitVisibleY201CompletoAntesDeLiberarSmtp() throws Exception {
        var llego = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var visible = new AtomicBoolean();
        var sinTransaccion = new AtomicBoolean();
        smtp.accion(() -> {
            sinTransaccion.set(!TransactionSynchronizationManager.isActualTransactionActive());
            visible.set("RESERVADO".equals(jdbc.queryForObject("SELECT estado FROM turno", String.class)));
            llego.countDown();
            esperar(liberar);
        });
        try (var solicitudes = Executors.newSingleThreadExecutor()) {
            try {
                var respuesta = solicitudes.submit(() -> reservar(DNI, EMAIL));
                esperar(llego);
                comprobar201(respuesta.get(5, TimeUnit.SECONDS));
                assertThat(visible).isTrue();
                assertThat(sinTransaccion).isTrue();
                assertThat(smtp.ultimo().getAllRecipients()).hasSize(1);
                assertThat(smtp.ultimo().getAllRecipients()[0].toString()).isEqualTo(EMAIL);
                assertThat(smtp.ultimo().getContent().toString()).contains("27/11/2026", "09:00", "Consulta",
                        "Calle de prueba", "https://example.test/sede/turno/");
            } finally {
                liberar.countDown();
            }
        }
        vaciarEjecutor();
        assertThat(smtp.intentos()).isEqualTo(1);
        comprobarFila("CONFIRMACION", "ENVIADO", null);
    }

    @Test
    void usaElSnapshotAunqueCambieElContactoAntesDeArmarElMensaje() throws Exception {
        var ocupados = new CountDownLatch(2);
        var liberar = new CountDownLatch(1);
        try {
            ocuparWorkers(ocupados, liberar);
            comprobar201(reservar(DNI, EMAIL));
            jdbc.update("UPDATE paciente SET email = ?", "contacto-editado@example.test");
            assertThat(smtp.intentos()).isZero();
        } finally {
            liberar.countDown();
        }
        vaciarEjecutor();
        assertThat(smtp.intentos()).isEqualTo(1);
        assertThat(smtp.ultimo().getAllRecipients()[0].toString()).isEqualTo(EMAIL);
        comprobarFila("CONFIRMACION", "ENVIADO", null);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void smtpFallidoOTimeoutAmbiguoConservaTurnoYAuditaUnaVez(boolean ambiguo, CapturedOutput salida)
            throws Exception {
        smtp.accion(() -> {
            reloj.fijar(AHORA.plusSeconds(5));
            throw new MailSendException("credencial-centinela https://example.test/turno/token-centinela " + EMAIL,
                    ambiguo ? new SocketTimeoutException("timeout tras DATA") : new IllegalStateException("rechazo"));
        });
        comprobar201(reservar(DNI, EMAIL));
        vaciarEjecutor();
        assertThat(smtp.intentos()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT estado FROM turno", String.class)).isEqualTo("RESERVADO");
        comprobarFila("CONFIRMACION", "ERROR", "SMTP_FALLO");
        assertThat(salida.getAll()).doesNotContain(EMAIL, "credencial-centinela", "token-centinela", "MailSendException");
    }

    @Test
    void rollbackDespuesDePublicarYSinTransaccionNoDespachan() {
        var tx = new TransactionTemplate(gestor);
        long antes = tareasPresentadas();
        tx.executeWithoutResult(status -> {
            contexto.publishEvent(new TurnoReservado(fixtureTurno(), OrigenTurno.ONLINE, "no-enviar"));
            status.setRollbackOnly();
        });
        assertThat(tareasPresentadas()).isEqualTo(antes);
        vaciarEjecutor();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno", Long.class)).isZero();
        var turno = fixtureTurno();
        antes = tareasPresentadas();
        contexto.publishEvent(new TurnoReservado(turno, OrigenTurno.ONLINE, "no-enviar"));
        contexto.publishEvent(new TurnoCancelado(turno, CanceladoPor.PACIENTE, AHORA));
        contexto.publishEvent(new TurnoReprogramado(turno, turno, "no-enviar"));
        assertThat(tareasPresentadas()).isEqualTo(antes);
        vaciarEjecutor();
        sinNotificacion();
    }

    @Test
    void conflictosNoNotifican() throws Exception {
        fixtureTurno();
        long antes = tareasPresentadas();
        reservar(DNI, EMAIL).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("RN-TU-01"));
        assertThat(tareasPresentadas()).isEqualTo(antes);
        vaciarEjecutor();
        sinNotificacion();
        jdbc.update("DELETE FROM turno");
        long paciente = fixtures.paciente(profesional, DNI);
        fixtures.turno(profesional, paciente, prestacion, INICIO.plusSeconds(86400),
                INICIO.plusSeconds(88200), "RESERVADO");
        fixtures.turno(profesional, paciente, prestacion, INICIO.plusSeconds(90000),
                INICIO.plusSeconds(91800), "RESERVADO");
        antes = tareasPresentadas();
        reservar(DNI, EMAIL).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("RN-PA-03"));
        assertThat(tareasPresentadas()).isEqualTo(antes);
        vaciarEjecutor();
        sinNotificacion();
    }

    @Test
    void cancelacionYReprogramacionSoloPublicanAlConfirmar() throws Exception {
        var anterior = fixtureTurno();
        long nuevoId = fixtures.turno(profesional, fixtures.paciente(profesional, "13572467"), prestacion,
                INICIO.plusSeconds(3600), INICIO.plusSeconds(5400), "RESERVADO");
        var nuevo = new ResumenTurno(nuevoId, profesional, INICIO.plusSeconds(3600), INICIO.plusSeconds(5400),
                prestacion, "Prestación nueva", 30, "Paciente", "Prueba", "nuevo@example.test", "000",
                "Profesional", "Prueba", null);
        var cancelado = new TurnoCancelado(anterior, CanceladoPor.PACIENTE, AHORA);
        var reprogramado = new TurnoReprogramado(anterior, nuevo, "token-nuevo");
        var tx = new TransactionTemplate(gestor);
        tx.executeWithoutResult(t -> {
            contexto.publishEvent(cancelado);
            contexto.publishEvent(reprogramado);
            t.setRollbackOnly();
        });
        vaciarEjecutor();
        sinNotificacion();

        tx.executeWithoutResult(t -> contexto.publishEvent(cancelado));
        vaciarEjecutor();
        assertThat(smtp.intentos()).isEqualTo(1);
        assertThat(smtp.ultimo().getSubject()).isEqualTo("Cancelamos tu turno");
        assertThat(smtp.ultimo().getContent().toString()).contains("09:00").doesNotContain("href=");
        tx.executeWithoutResult(t -> contexto.publishEvent(reprogramado));
        vaciarEjecutor();
        assertThat(smtp.intentos()).isEqualTo(2);
        assertThat(smtp.ultimo().getAllRecipients()[0].toString()).isEqualTo("nuevo@example.test");
        assertThat(smtp.ultimo().getContent().toString()).contains("10:00", "Prestación nueva")
                .containsOnlyOnce("href=\"https://example.test/sede/turno/token-nuevo\"").doesNotContain("09:00");
        assertThat(jdbc.queryForList("SELECT turno_id FROM notificacion ORDER BY id", Long.class))
                .containsExactly(anterior.turnoId(), nuevoId);
        assertThat(jdbc.queryForList("SELECT tipo FROM notificacion ORDER BY id", String.class))
                .containsExactly("CANCELACION", "REPROGRAMACION");
        assertThat(jdbc.queryForList("SELECT estado FROM turno ORDER BY id", String.class))
                .containsExactly("RESERVADO", "RESERVADO");
    }

    @Test
    void cancelacionRealConfirmaAntesDeSmtpYNotificaUnaSolaVezAlSnapshot() throws Exception {
        long original = turnoParaCancelar(EMAIL);
        jdbc.update("UPDATE paciente SET email = 'contacto-editado@example.test'");
        var llego = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var visible = new AtomicBoolean();
        smtp.accion(() -> {
            visible.set(!TransactionSynchronizationManager.isActualTransactionActive()
                    && "CANCELADO".equals(jdbc.queryForObject("SELECT estado FROM turno WHERE id = ?",
                            String.class, original)));
            llego.countDown();
            esperar(liberar);
        });
        long antes = tareasPresentadas();
        try (var solicitudes = Executors.newSingleThreadExecutor()) {
            try {
                var respuesta = solicitudes.submit(this::cancelar);
                respuesta.get(5, TimeUnit.SECONDS).andExpect(status().isNoContent()).andExpect(content().string(""));
                assertThat(tareasPresentadas()).as("la cancelación despacha un único aviso al confirmar")
                        .isEqualTo(antes + 1);
                esperar(llego);
                assertThat(visible).isTrue();
                assertThat(smtp.ultimo().getAllRecipients()[0].toString()).isEqualTo(EMAIL);
                assertThat(smtp.ultimo().getContent().toString()).doesNotContain("href=", TOKEN_CANCELACION);
            } finally {
                liberar.countDown();
            }
        }
        vaciarEjecutor();
        comprobarFila("CANCELACION", "ENVIADO", null);
        cancelar().andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("RN-SE-02"));
        vaciarEjecutor();
        assertThat(smtp.intentos()).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT turno_id FROM notificacion", Long.class)).containsExactly(original);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.NullAndEmptySource
    @ValueSource(strings = {"ana@ejemplo_com.test"})
    void cancelacionRealSinBuzonAdmisibleNoIntentaSmtpNiAuditoria(String email) throws Exception {
        long original = turnoParaCancelar(email);
        org.mockito.Mockito.clearInvocations(registro);
        long antes = tareasPresentadas();

        cancelar().andExpect(status().isNoContent());
        assertThat(tareasPresentadas()).isEqualTo(antes + 1);
        vaciarEjecutor();
        assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE id = ?", String.class, original))
                .isEqualTo("CANCELADO");
        sinNotificacion();
        org.mockito.Mockito.verifyNoInteractions(registro);
    }

    private long turnoParaCancelar(String email) {
        long paciente = fixtures.paciente(profesional, DNI);
        long turno = fixtures.turno(profesional, paciente, prestacion, INICIO, INICIO.plusSeconds(1800),
                "RESERVADO", TokenTurno.hashear(TOKEN_CANCELACION));
        jdbc.update("UPDATE turno SET email_gestion = ? WHERE id = ?", email, turno);
        return turno;
    }

    private ResultActions cancelar() throws Exception {
        return mvc.perform(post("/api/public/turnos/{token}/cancelar", TOKEN_CANCELACION));
    }

    @Test
    void reprogramacionRealNotificaSoloElSucesorYElLinkResuelveSuHash(CapturedOutput salida) throws Exception {
        long original = turnoParaCancelar(EMAIL);
        jdbc.update("UPDATE paciente SET email = 'contacto-editado@example.test'");
        var visible = new AtomicBoolean();
        smtp.accion(() -> visible.set(!TransactionSynchronizationManager.isActualTransactionActive()
                && "CANCELADO".equals(jdbc.queryForObject("SELECT estado FROM turno WHERE id=?", String.class, original))
                && "RESERVADO".equals(jdbc.queryForObject("SELECT estado FROM turno WHERE reprogramado_desde_id=?",
                        String.class, original))));

        reprogramar("10:00").andExpect(status().isNoContent()).andExpect(content().string(""))
                .andExpect(header().doesNotExist("Location"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(r -> assertThat(r.getRequest().getSession(false)).isNull());
        vaciarEjecutor();

        assertThat(eventos.publicados.get()).isEqualTo(1);
        assertThat(eventos.confirmados.get()).isEqualTo(1);
        assertThat(eventos.otros.get()).isZero();
        assertThat(visible).isTrue();
        assertThat(smtp.intentos()).isEqualTo(1);
        assertThat(smtp.ultimo().getAllRecipients()[0].toString()).isEqualTo(EMAIL);
        var link = Pattern.compile("href=\"https://example\\.test/sede/turno/([A-Za-z0-9_-]{43})\"")
                .matcher(smtp.ultimo().getContent().toString());
        assertThat(link.find()).as("se envió el link nuevo").isTrue();
        String token = link.group(1);
        assertThat(link.find()).as("no hay un segundo link").isFalse();
        assertThat(token.equals(TOKEN_CANCELACION)).isFalse();
        var sucesor = jdbc.queryForMap("SELECT id, token_hash FROM turno WHERE reprogramado_desde_id=?", original);
        String hash = TokenTurno.hashear(token);
        assertThat(hash.equals(sucesor.get("token_hash"))).isTrue();
        assertThat(eventos.ultimo.get().token().equals(token)).isTrue();
        assertThat(eventos.ultimo.get().toString().contains(token)).isFalse();
        assertThat(List.of(token, hash, TOKEN_CANCELACION, TokenTurno.hashear(TOKEN_CANCELACION), EMAIL)
                .stream().anyMatch(salida.getAll()::contains)).isFalse();
        assertThat(jdbc.queryForMap("SELECT turno_id, tipo, estado, destinatario FROM notificacion"))
                .containsEntry("turno_id", sucesor.get("id")).containsEntry("tipo", "REPROGRAMACION")
                .containsEntry("estado", "ENVIADO").containsEntry("destinatario", EMAIL);
        mvc.perform(get("/api/public/turnos/{token}", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.inicio").value("2026-11-27T10:00:00-03:00"))
                .andExpect(jsonPath("$.estado").value("RESERVADO"));
        mvc.perform(get("/api/public/turnos/{token}", TOKEN_CANCELACION)).andExpect(status().isOk())
                .andExpect(jsonPath("$.inicio").value("2026-11-27T09:00:00-03:00"))
                .andExpect(jsonPath("$.estado").value("CANCELADO"))
                .andExpect(jsonPath("$.puedeReprogramar").value(false));
    }

    @Test
    void reprogramacionRealRevierteDespuesDePublicarSinEntregarAviso(CapturedOutput salida) throws Exception {
        long original = turnoParaCancelar(EMAIL);
        var antes = jdbc.queryForMap("SELECT * FROM turno WHERE id=?", original);
        eventos.revertir.set(true);
        long despachos = tareasPresentadas();

        reprogramar("10:00").andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().doesNotExist("Location"));
        assertThat(eventos.publicados.get()).isEqualTo(1);
        assertThat(eventos.escriturasVisiblesAntesDelRollback.get()).isTrue();
        assertThat(eventos.confirmados.get()).isZero();
        assertThat(eventos.otros.get()).isZero();
        assertThat(tareasPresentadas()).isEqualTo(despachos);
        vaciarEjecutor();
        sinNotificacion();
        assertThat(jdbc.queryForMap("SELECT * FROM turno")).isEqualTo(antes);
        var token = eventos.ultimo.get().token();
        assertThat(List.of(token, TokenTurno.hashear(token), TOKEN_CANCELACION, EMAIL)
                .stream().anyMatch(salida.getAll()::contains)).isFalse();
    }

    @Test
    void reprogramacionRealConSmtpFallidoNoRevierteNiReintenta() throws Exception {
        long original = turnoParaCancelar(EMAIL);
        smtp.accion(() -> { throw new MailSendException("rechazo de prueba"); });
        reprogramar("10:00").andExpect(status().isNoContent());
        vaciarEjecutor();
        assertThat(eventos.confirmados.get()).isEqualTo(1);
        assertThat(smtp.intentos()).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT tipo, estado, error, turno_id FROM notificacion"))
                .containsEntry("tipo", "REPROGRAMACION").containsEntry("estado", "ERROR")
                .containsEntry("error", "SMTP_FALLO").containsEntry("turno_id", sucesorReservado(original));
    }

    @Test
    void reprogramacionRealConColaLlenaNoRevierteNiReintenta(CapturedOutput salida) throws Exception {
        long original = turnoParaCancelar(EMAIL);
        var ejecutor = contexto.getBean("ejecutorNotificaciones", Executor.class);
        var ocupados = new CountDownLatch(2);
        var liberar = new CountDownLatch(1);
        var drenaje = new CountDownLatch(32);
        try (var solicitudes = Executors.newSingleThreadExecutor()) {
            try {
                ocuparWorkers(ocupados, liberar);
                for (int i = 0; i < 32; i++) {
                    ejecutor.execute(drenaje::countDown);
                }
                solicitudes.submit(() -> reprogramar("10:00")).get(5, TimeUnit.SECONDS)
                        .andExpect(status().isNoContent());
                assertThat(eventos.confirmados.get()).isEqualTo(1);
                sucesorReservado(original);
                assertThat(salida.getAll()).containsOnlyOnce("DESPACHO_RECHAZADO");
            } finally {
                liberar.countDown();
            }
        }
        esperar(drenaje);
        vaciarEjecutor();
        sinNotificacion();
    }

    @Test
    void carreraRealDeReprogramacionesSoloNotificaAlGanador() throws Exception {
        long original = turnoParaCancelar(EMAIL);
        var empezar = new CountDownLatch(1);
        try (var solicitudes = Executors.newFixedThreadPool(2)) {
            var a = solicitudes.submit(() -> { esperar(empezar); return reprogramar("10:00").andReturn(); });
            var b = solicitudes.submit(() -> { esperar(empezar); return reprogramar("11:00").andReturn(); });
            empezar.countDown();
            var primera = a.get(8, TimeUnit.SECONDS).getResponse();
            var segunda = b.get(8, TimeUnit.SECONDS).getResponse();
            assertThat(new int[]{primera.getStatus(), segunda.getStatus()}).containsExactlyInAnyOrder(204, 409);
            assertThat((primera.getStatus() == 409 ? primera : segunda).getContentAsString()).contains("RN-SE-02");
        }
        vaciarEjecutor();
        long sucesor = sucesorReservado(original);
        assertThat(eventos.publicados.get()).isEqualTo(1);
        assertThat(eventos.confirmados.get()).isEqualTo(1);
        assertThat(eventos.otros.get()).isZero();
        assertThat(smtp.intentos()).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT turno_id FROM notificacion", Long.class)).containsExactly(sucesor);
    }

    private long sucesorReservado(long original) {
        assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE id=?", String.class, original))
                .isEqualTo("CANCELADO");
        return jdbc.queryForObject("SELECT id FROM turno WHERE reprogramado_desde_id=? AND estado='RESERVADO'",
                Long.class, original);
    }

    private ResultActions reprogramar(String hora) throws Exception {
        return mvc.perform(post("/api/public/turnos/{token}/reprogramar", TOKEN_CANCELACION)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"inicio":"2026-11-27T%s:00-03:00"}""".formatted(hora)));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ObservacionReprogramacion {
        @Bean EventosReprogramacion observarEventos(JdbcTemplate jdbc) {
            return new EventosReprogramacion(jdbc);
        }
    }

    static class EventosReprogramacion {
        private final JdbcTemplate jdbc;
        private final AtomicReference<TurnoReprogramado> ultimo = new AtomicReference<>();
        private final AtomicInteger publicados = new AtomicInteger();
        private final AtomicInteger confirmados = new AtomicInteger();
        private final AtomicInteger otros = new AtomicInteger();
        private final AtomicBoolean revertir = new AtomicBoolean();
        private final AtomicBoolean escriturasVisiblesAntesDelRollback = new AtomicBoolean();

        EventosReprogramacion(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        void reiniciar() {
            ultimo.set(null);
            publicados.set(0);
            confirmados.set(0);
            otros.set(0);
            revertir.set(false);
            escriturasVisiblesAntesDelRollback.set(false);
        }

        @EventListener
        void publicado(TurnoReprogramado evento) {
            ultimo.set(evento);
            publicados.incrementAndGet();
        }

        @EventListener({TurnoReservado.class, TurnoCancelado.class})
        void otroEvento() {
            otros.incrementAndGet();
        }

        @TransactionalEventListener
        void confirmado(TurnoReprogramado evento) {
            confirmados.incrementAndGet();
        }

        @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
        void antesDelCommit(TurnoReprogramado evento) {
            if (revertir.get()) {
                escriturasVisiblesAntesDelRollback.set(TransactionSynchronizationManager.isActualTransactionActive()
                        && "CANCELADO".equals(jdbc.queryForObject("SELECT estado FROM turno WHERE id=?",
                                String.class, evento.anterior().turnoId()))
                        && "RESERVADO".equals(jdbc.queryForObject("SELECT estado FROM turno WHERE id=?",
                                String.class, evento.nuevo().turnoId())));
                throw new IllegalStateException("rollback sintético tras publicación");
            }
        }
    }

    @Test
    void saturacionNoBloqueaNiEjecutaElAviso(CapturedOutput salida) throws Exception {
        var ejecutor = contexto.getBean("ejecutorNotificaciones", Executor.class);
        var ocupados = new CountDownLatch(2);
        var liberar = new CountDownLatch(1);
        var colaDrenada = new CountDownLatch(32);
        try (var solicitudes = Executors.newSingleThreadExecutor()) {
            try {
                ocuparWorkers(ocupados, liberar);
                for (int i = 0; i < 32; i++) {
                    ejecutor.execute(colaDrenada::countDown);
                }
                comprobar201(solicitudes.submit(() -> reservar(DNI, EMAIL)).get(5, TimeUnit.SECONDS));
                sinNotificacion();
                assertThat(salida.getAll()).containsOnlyOnce("DESPACHO_RECHAZADO").doesNotContain(EMAIL, DNI);
            } finally {
                liberar.countDown();
            }
        }
        esperar(colaDrenada);
        vaciarEjecutor();
        sinNotificacion();
    }

    @Test
    void dosReservasConcurrentesSoloGeneranUnAviso() throws Exception {
        var empezar = new CountDownLatch(1);
        try (var solicitudes = Executors.newFixedThreadPool(2)) {
            var primera = solicitudes.submit(() -> { esperar(empezar); return reservar(DNI, EMAIL).andReturn(); });
            var segunda = solicitudes.submit(() -> {
                esperar(empezar);
                return reservar("13572467", "segundo@example.test").andReturn();
            });
            empezar.countDown();
            var a = primera.get(8, TimeUnit.SECONDS).getResponse();
            var b = segunda.get(8, TimeUnit.SECONDS).getResponse();
            assertThat(new int[]{a.getStatus(), b.getStatus()}).containsExactlyInAnyOrder(201, 409);
            assertThat((a.getStatus() == 409 ? a : b).getContentAsString()).contains("RN-TU-01");
        }
        vaciarEjecutor();
        assertThat(smtp.intentos()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno", Long.class)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT n.turno_id FROM notificacion n", Long.class))
                .containsExactly(jdbc.queryForObject("SELECT id FROM turno", Long.class));
    }

    private ResumenTurno fixtureTurno() {
        long id = fixtures.turno(profesional, fixtures.paciente(profesional, "13572469"), prestacion, INICIO,
                INICIO.plusSeconds(1800), "RESERVADO");
        return new ResumenTurno(id, profesional, INICIO, INICIO.plusSeconds(1800), prestacion, "Consulta", 30,
                "Paciente", "Prueba", EMAIL, "000", "Profesional", "Prueba", null);
    }

    private ResultActions reservar(String dni, String email) throws Exception {
        return mvc.perform(post("/api/public/avisos/turnos").contentType(MediaType.APPLICATION_JSON).content("""
                {"prestacionId":%d,"inicio":"2026-11-27T09:00:00-03:00","dni":"%s","email":"%s",
                 "nombre":"Paciente","apellido":"Prueba","telefono":"+5493510000000"}
                """.formatted(prestacion, dni, email)));
    }

    private void comprobar201(ResultActions respuesta) throws Exception {
        respuesta.andExpect(status().isCreated()).andExpect(content().json("""
                {"inicio":"2026-11-27T09:00:00-03:00","fin":"2026-11-27T09:30:00-03:00",
                 "prestacion":{"id":%d,"nombre":"Consulta","duracionMinutos":30},
                 "odontologo":{"nombre":"Nombre","apellido":"Apellido","direccionConsultorio":"Calle de prueba"},
                 "email":"%s"}
                """.formatted(prestacion, EMAIL), JsonCompareMode.STRICT));
    }

    private void comprobarFila(String tipo, String estado, String error) {
        var fila = jdbc.queryForMap("SELECT turno_id, tipo, estado, destinatario, error, enviado_en FROM notificacion");
        assertThat(fila).containsEntry("tipo", tipo).containsEntry("estado", estado).containsEntry("error", error)
                .containsEntry("destinatario", EMAIL)
                .containsEntry("turno_id", jdbc.queryForObject("SELECT id FROM turno", Long.class));
        Instant intento = jdbc.queryForObject("SELECT enviado_en FROM notificacion",
                (rs, n) -> rs.getObject(1, OffsetDateTime.class).toInstant());
        assertThat(intento).isEqualTo(AHORA);
    }

    private void sinNotificacion() {
        assertThat(smtp.intentos()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion", Long.class)).isZero();
    }

    private void vaciarEjecutor() {
        var ejecutor = contexto.getBean("ejecutorNotificaciones", Executor.class);
        var barrera = new CountDownLatch(2);
        Runnable fin = () -> { barrera.countDown(); esperar(barrera); };
        ejecutor.execute(fin);
        ejecutor.execute(fin);
        esperar(barrera);
    }

    private void ocuparWorkers(CountDownLatch ocupados, CountDownLatch liberar) {
        var ejecutor = contexto.getBean("ejecutorNotificaciones", Executor.class);
        Runnable bloquear = () -> { ocupados.countDown(); esperar(liberar); };
        ejecutor.execute(bloquear);
        ejecutor.execute(bloquear);
        esperar(ocupados);
    }

    private long tareasPresentadas() {
        return contexto.getBean("ejecutorNotificaciones", ThreadPoolTaskExecutor.class)
                .getThreadPoolExecutor().getTaskCount();
    }

    private static void esperar(CountDownLatch latch) {
        try {
            assertThat(latch.await(8, TimeUnit.SECONDS)).as("barrera de prueba").isTrue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }
}
