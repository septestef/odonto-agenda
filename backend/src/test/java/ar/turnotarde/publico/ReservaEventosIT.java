package ar.turnotarde.publico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnotarde.soporte.ConfiguracionRelojDePrueba;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import ar.turnotarde.soporte.RelojDePrueba;
import ar.turnotarde.turno.OrigenTurno;
import ar.turnotarde.turno.TokenTurno;
import ar.turnotarde.turno.eventos.TurnoReservado;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Contrato del evento {@code TurnoReservado} (specs/eventos-turno) con un consumidor de test que escucha después
 * del commit, como lo hará C-08. Reloj fijo en 2026-11-25T12:00:00Z.
 */
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Import({ConfiguracionRelojDePrueba.class, ReservaEventosIT.EscuchaDePrueba.class})
class ReservaEventosIT extends IntegracionTest {

    /** Recibe el evento tras el commit y comprueba, desde otra conexión, que el turno ya es visible. */
    static class RegistroDeEventos {
        final List<TurnoReservado> eventos = new CopyOnWriteArrayList<>();
        final List<Boolean> turnoVisibleAlRecibir = new CopyOnWriteArrayList<>();
        private final DataSource dataSource;

        RegistroDeEventos(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        void alConfirmarse(TurnoReservado evento) throws SQLException {
            try (Connection otraConexion = dataSource.getConnection();
                    PreparedStatement ps = otraConexion.prepareStatement("SELECT count(*) FROM turno WHERE id = ?")) {
                ps.setLong(1, evento.turno().turnoId());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    turnoVisibleAlRecibir.add(rs.getInt(1) == 1);
                }
            }
            eventos.add(evento);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class EscuchaDePrueba {
        @Bean
        RegistroDeEventos registroDeEventos(DataSource dataSource) {
            return new RegistroDeEventos(dataSource);
        }
    }

    private static final String VIERNES_0900 = "2026-11-27T09:00:00-03:00";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RelojDePrueba reloj;
    @Autowired
    private RegistroDeEventos registro;

    private FixturesJdbc fixtures;
    private long demo;
    private long consulta;

    @AfterEach
    void dejarLaBaseVacia() {
        LimpiezaBase.limpiar(jdbc);
    }

    @BeforeEach
    void preparar() {
        reloj.fijar(Instant.parse("2026-11-25T12:00:00Z"));
        registro.eventos.clear();
        registro.turnoVisibleAlRecibir.clear();
        LimpiezaBase.limpiar(jdbc);
        fixtures = new FixturesJdbc(jdbc);
        demo = fixtures.odontologo("demo");
        consulta = fixtures.prestacion(demo, "Consulta", 30);
        fixtures.horario(demo, 5, "09:00", "13:00");
    }

    private org.springframework.test.web.servlet.ResultActions reservar(String inicio, String dni, String email)
            throws Exception {
        return mvc.perform(post("/api/public/demo/turnos").contentType(MediaType.APPLICATION_JSON).content("""
                {"prestacionId": %d, "inicio": "%s", "dni": "%s", "nombre": "Luis", "apellido": "Gómez",
                 "telefono": "+5493511234567", "email": "%s"}""".formatted(consulta, inicio, dni, email)));
    }

    @Test
    void unaReservaConfirmadaEntregaUnSoloEventoConLosDatosYElTokenQueCoincideConElHash(CapturedOutput salida)
            throws Exception {
        reservar(VIERNES_0900, "12345678", "luis@example.com").andExpect(status().isCreated());

        assertThat(registro.eventos).hasSize(1);
        TurnoReservado evento = registro.eventos.get(0);
        Long turnoId = jdbc.queryForObject("SELECT id FROM turno", Long.class);
        String hashGuardado = jdbc.queryForObject("SELECT token_hash FROM turno", String.class);
        assertThat(evento.turno().turnoId()).isEqualTo(turnoId);
        assertThat(evento.turno().inicio()).isEqualTo(Instant.parse("2026-11-27T12:00:00Z"));
        assertThat(evento.turno().fin()).isEqualTo(Instant.parse("2026-11-27T12:30:00Z"));
        assertThat(evento.turno().odontologoId()).isEqualTo(demo);
        assertThat(evento.turno().prestacionNombre()).isEqualTo("Consulta");
        assertThat(evento.turno().duracionMinutos()).isEqualTo(30);
        assertThat(evento.turno().pacienteNombre()).isEqualTo("Luis");
        assertThat(evento.turno().pacienteEmail()).isEqualTo("luis@example.com");
        assertThat(evento.turno().pacienteTelefono()).isEqualTo("+5493511234567");
        assertThat(evento.origen()).isEqualTo(OrigenTurno.ONLINE);
        assertThat(evento.token()).hasSize(43);
        assertThat(TokenTurno.hashear(evento.token())).isEqualTo(hashGuardado);

        assertThat(registro.turnoVisibleAlRecibir).as("el turno ya era visible desde otra conexión").containsExactly(true);
        assertThat(salida.getAll()).doesNotContain(evento.token());
        assertThat(evento.toString()).doesNotContain(evento.token());
    }

    @Test
    void cadaReservaConfirmadaEntregaSuPropioEventoConSuPropioToken() throws Exception {
        reservar(VIERNES_0900, "12345678", "luis@example.com").andExpect(status().isCreated());
        reservar("2026-11-27T10:00:00-03:00", "87654321", "ana@example.com").andExpect(status().isCreated());

        assertThat(registro.eventos).hasSize(2);
        assertThat(registro.eventos.get(0).token()).isNotEqualTo(registro.eventos.get(1).token());
        assertThat(registro.eventos).extracting(e -> e.turno().pacienteEmail())
                .containsExactly("luis@example.com", "ana@example.com");
    }

    @Test
    void unConflictoDeHorarioNoEntregaEvento() throws Exception {
        long otroPaciente = fixtures.paciente(demo, "99999999");
        fixtures.turno(demo, otroPaciente, consulta, Instant.parse("2026-11-27T12:00:00Z"),
                Instant.parse("2026-11-27T12:30:00Z"), "RESERVADO");

        reservar(VIERNES_0900, "12345678", "luis@example.com").andExpect(status().isConflict());

        assertThat(registro.eventos).isEmpty();
    }

    @Test
    void superarElLimiteDeTurnosNoEntregaEvento() throws Exception {
        long paciente = fixtures.paciente(demo, "12345678");
        fixtures.turno(demo, paciente, consulta, Instant.parse("2026-12-01T12:00:00Z"),
                Instant.parse("2026-12-01T12:30:00Z"), "RESERVADO");
        fixtures.turno(demo, paciente, consulta, Instant.parse("2026-12-01T13:00:00Z"),
                Instant.parse("2026-12-01T13:30:00Z"), "RESERVADO");

        reservar(VIERNES_0900, "12345678", "luis@example.com").andExpect(status().isConflict());

        assertThat(registro.eventos).isEmpty();
    }

    @Test
    void unaReservaInvalidaNoEntregaEvento() throws Exception {
        reservar("2026-11-27T09:10:00-03:00", "12345678", "luis@example.com")
                .andExpect(status().isUnprocessableEntity());

        assertThat(registro.eventos).isEmpty();
    }
}
