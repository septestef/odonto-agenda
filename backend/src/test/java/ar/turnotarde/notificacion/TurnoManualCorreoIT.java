package ar.turnotarde.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnotarde.soporte.ConfiguracionRelojDePrueba;
import ar.turnotarde.soporte.CuentaDePrueba;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import ar.turnotarde.turno.OrigenTurno;
import ar.turnotarde.turno.TokenTurno;
import ar.turnotarde.turno.eventos.TurnoReservado;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * C-15: el alta manual publica el mismo evento de confirmación que la reserva online, envía el link de gestión
 * y conserva el origen cuando el paciente reprograma desde ese link.
 */
@AutoConfigureMockMvc
@Import({ConfiguracionRelojDePrueba.class, TurnoManualCorreoIT.Escucha.class})
@TestPropertySource(properties = {"MAIL_HOST=smtp.example.test", "MAIL_PORT=587",
        "MAIL_USERNAME=origen@example.test", "MAIL_PASSWORD=clave-centinela", "MAIL_FROM_NAME=OdontoAgenda Prueba",
        "app.base-url=https://example.test/sede/"})
class TurnoManualCorreoIT extends IntegracionTest {

    private static final String CABECERA_CSRF = "X-XSRF-TOKEN";
    private static final String HOY_0915 = "2026-11-25T09:15:00-03:00";
    private static final String VIERNES_0915 = "2026-11-27T09:15:00-03:00";

    /** Escucha AFTER_COMMIT como lo hace C-08: solo ve turnos ya confirmados. */
    static class RegistroDeEventos {
        final List<TurnoReservado> reservados = new CopyOnWriteArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void alConfirmarse(TurnoReservado evento) {
            reservados.add(evento);
        }

        void reiniciar() {
            reservados.clear();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Escucha {
        @Bean
        RegistroDeEventos registroDeEventos() {
            return new RegistroDeEventos();
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SmtpDePrueba smtp;
    @Autowired
    private ApplicationContext contexto;
    @Autowired
    private RegistroDeEventos registro;

    private CuentaDePrueba.Cuenta cuenta;
    private FixturesJdbc fixtures;
    private long consulta;

    @BeforeEach
    void preparar() throws Exception {
        vaciarEjecutor();
        LimpiezaBase.limpiar(jdbc);
        smtp.limpiar();
        registro.reiniciar();
        cuenta = CuentaDePrueba.crear(mvc, jdbc, "manualcorreo");
        fixtures = new FixturesJdbc(jdbc);
        consulta = fixtures.prestacion(cuenta.id(), "Consulta", 30);
        fixtures.horario(cuenta.id(), 3, "09:00", "13:00");
        fixtures.horario(cuenta.id(), 5, "09:00", "13:00");
    }

    @AfterEach
    void limpiar() {
        vaciarEjecutor();
        LimpiezaBase.limpiar(jdbc);
    }

    private ResultActions crear(String inicio, String email) throws Exception {
        return mvc.perform(post("/api/turnos")
                .session(cuenta.sesion().sesion())
                .cookie(cuenta.sesion().csrfCookie())
                .header(CABECERA_CSRF, cuenta.sesion().csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"prestacionId": %d, "inicio": "%s", "dni": "12345678", "nombre": "Luis",
                         "apellido": "Gómez", "telefono": "+5493511234567", "email": "%s"}"""
                        .formatted(consulta, inicio, email)));
    }

    @Test
    void confirmacionConTokenEventoManualYLinkGestionable() throws Exception {
        String respuesta = crear(HOY_0915, "luis@example.test")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        vaciarEjecutor();

        assertThat(registro.reservados).hasSize(1);
        TurnoReservado evento = registro.reservados.get(0);
        assertThat(evento.origen()).isEqualTo(OrigenTurno.MANUAL);
        assertThat(evento.token()).matches(TokenTurno.FORMATO);
        String hash = jdbc.queryForObject("SELECT token_hash FROM turno WHERE id = ?", String.class,
                evento.turno().turnoId());
        assertThat(hash).isEqualTo(TokenTurno.hashear(evento.token()));

        assertThat(smtp.intentos()).isEqualTo(1);
        assertThat(smtp.ultimo().getAllRecipients()[0].toString()).isEqualTo("luis@example.test");
        String contenido = smtp.ultimo().getContent().toString();
        assertThat(contenido).contains("25/11/2026", "09:15", "Consulta", "/turno/");
        var enlace = Pattern.compile("/turno/(" + TokenTurno.FORMATO + ")").matcher(contenido);
        assertThat(enlace.find()).as("el correo incluye el link de gestión").isTrue();
        String tokenDelCorreo = enlace.group(1);
        assertThat(tokenDelCorreo).isEqualTo(evento.token());
        assertThat(respuesta).doesNotContain(tokenDelCorreo, hash);

        mvc.perform(get("/api/public/turnos/{token}", tokenDelCorreo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RESERVADO"))
                .andExpect(jsonPath("$.prestacion.nombre").value("Consulta"));

        var fila = jdbc.queryForMap("SELECT turno_id, tipo, estado FROM notificacion");
        assertThat(fila).containsEntry("turno_id", evento.turno().turnoId())
                .containsEntry("tipo", "CONFIRMACION").containsEntry("estado", "ENVIADO");
    }

    @Test
    void unRechazoNoPublicaEventoNiEnviaCorreo() throws Exception {
        long paciente = fixtures.paciente(cuenta.id(), "11111111");
        fixtures.turno(cuenta.id(), paciente, consulta, Instant.parse("2026-11-25T12:00:00Z"),
                Instant.parse("2026-11-25T12:30:00Z"), "RESERVADO", OrigenTurno.MANUAL);

        crear(HOY_0915, "luis@example.test")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("RN-TU-01"));
        vaciarEjecutor();

        assertThat(registro.reservados).isEmpty();
        assertThat(smtp.intentos()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion", Integer.class)).isZero();
    }

    @Test
    void reprogramarDesdeElLinkConservaElOrigenManual() throws Exception {
        crear(VIERNES_0915, "luis@example.test").andExpect(status().isCreated());
        vaciarEjecutor();
        TurnoReservado evento = registro.reservados.get(0);
        long original = evento.turno().turnoId();

        mvc.perform(post("/api/public/turnos/{token}/reprogramar", evento.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"inicio\":\"2026-11-27T10:15:00-03:00\"}"))
                .andExpect(status().isNoContent());

        var sucesor = jdbc.queryForMap("SELECT * FROM turno WHERE reprogramado_desde_id = ?", original);
        assertThat(sucesor).containsEntry("origen", "MANUAL").containsEntry("estado", "RESERVADO")
                .containsEntry("reprogramado_desde_id", original);
        assertThat(((java.sql.Timestamp) sucesor.get("inicio")).toInstant())
                .isEqualTo(Instant.parse("2026-11-27T13:15:00Z"));
        assertThat(jdbc.queryForObject("SELECT origen FROM turno WHERE id = ?", String.class, original))
                .isEqualTo("MANUAL");
    }

    private void vaciarEjecutor() {
        var ejecutor = contexto.getBean("ejecutorNotificaciones", Executor.class);
        var barrera = new CountDownLatch(2);
        Runnable fin = () -> {
            barrera.countDown();
            try {
                assertThat(barrera.await(8, TimeUnit.SECONDS)).as("barrera de prueba").isTrue();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError(ex);
            }
        };
        ejecutor.execute(fin);
        ejecutor.execute(fin);
        try {
            assertThat(barrera.await(8, TimeUnit.SECONDS)).as("drenaje del ejecutor").isTrue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }
}
