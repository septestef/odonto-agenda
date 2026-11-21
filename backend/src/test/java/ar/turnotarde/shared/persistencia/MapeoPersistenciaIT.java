package ar.turnotarde.shared.persistencia;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnotarde.agenda.Bloqueo;
import ar.turnotarde.agenda.BloqueoRepository;
import ar.turnotarde.agenda.HorarioAtencion;
import ar.turnotarde.agenda.HorarioAtencionRepository;
import ar.turnotarde.notificacion.EstadoNotificacion;
import ar.turnotarde.notificacion.Notificacion;
import ar.turnotarde.notificacion.NotificacionRepository;
import ar.turnotarde.notificacion.TipoNotificacion;
import ar.turnotarde.odontologo.Odontologo;
import ar.turnotarde.odontologo.OdontologoRepository;
import ar.turnotarde.paciente.Paciente;
import ar.turnotarde.paciente.PacienteRepository;
import ar.turnotarde.prestacion.Prestacion;
import ar.turnotarde.prestacion.PrestacionRepository;
import ar.turnotarde.soporte.ConfiguracionRelojDePrueba;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import ar.turnotarde.soporte.RelojDePrueba;
import ar.turnotarde.turno.CanceladoPor;
import ar.turnotarde.turno.EstadoTurno;
import ar.turnotarde.turno.OrigenTurno;
import ar.turnotarde.turno.Turno;
import ar.turnotarde.turno.TurnoRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.TimeZone;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** Mapeo entidad-tabla (ida y vuelta) y auditoría con el reloj inyectado. */
@Import(ConfiguracionRelojDePrueba.class)
class MapeoPersistenciaIT extends IntegracionTest {

    private static final Instant ALTA = Instant.parse("2026-11-25T12:00:00Z");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private RelojDePrueba reloj;

    @Autowired
    private TransactionTemplate transacciones;

    @Autowired
    private PrestacionRepository prestaciones;

    @Autowired
    private OdontologoRepository odontologos;

    @Autowired
    private HorarioAtencionRepository horarios;

    @Autowired
    private BloqueoRepository bloqueos;

    @Autowired
    private PacienteRepository pacientes;

    @Autowired
    private TurnoRepository turnos;

    @Autowired
    private NotificacionRepository notificaciones;

    private final TimeZone zonaOriginal = TimeZone.getDefault();
    private JdbcTemplate jdbc;
    private FixturesJdbc fx;

    @BeforeEach
    void preparar() {
        jdbc = new JdbcTemplate(dataSource);
        fx = new FixturesJdbc(jdbc);
        LimpiezaBase.limpiar(jdbc);
        reloj.fijar(ALTA);
    }

    @AfterEach
    void restaurarZona() {
        TimeZone.setDefault(zonaOriginal);
    }

    private Instant columna(String tabla, String columna, long id) {
        return jdbc.queryForObject("select " + columna + " from " + tabla + " where id = ?",
                (rs, n) -> rs.getObject(1, OffsetDateTime.class).toInstant(), id);
    }

    // ---- auditoría (D5) ----

    @Test
    void elAltaCompletaLaAuditoriaConElRelojInyectado() {
        long odontologo = fx.odontologo("od-audit");

        Prestacion guardada = prestaciones.saveAndFlush(new Prestacion(odontologo, "Consulta", (short) 30));

        assertThat(guardada.getCreadoEn()).isEqualTo(ALTA);
        assertThat(guardada.getActualizadoEn()).isEqualTo(ALTA);
        assertThat(columna("prestacion", "creado_en", guardada.getId())).isEqualTo(ALTA);
        assertThat(columna("prestacion", "actualizado_en", guardada.getId())).isEqualTo(ALTA);
    }

    @Test
    void laModificacionActualizaSoloActualizadoEn() {
        long odontologo = fx.odontologo("od-audit2");
        Long id = prestaciones.saveAndFlush(new Prestacion(odontologo, "Consulta", (short) 30)).getId();

        reloj.avanzar(Duration.ofHours(1));
        transacciones.executeWithoutResult(estado -> prestaciones.findById(id).orElseThrow().desactivar());

        assertThat(columna("prestacion", "creado_en", id)).isEqualTo(ALTA);
        assertThat(columna("prestacion", "actualizado_en", id)).isEqualTo(Instant.parse("2026-11-25T13:00:00Z"));
        assertThat(prestaciones.findById(id).orElseThrow().isActiva()).isFalse();
    }

    @Test
    void sinCambiosNoSeTocaLaAuditoria() {
        long odontologo = fx.odontologo("od-audit3");
        Long id = prestaciones.saveAndFlush(new Prestacion(odontologo, "Consulta", (short) 30)).getId();

        reloj.avanzar(Duration.ofHours(2));
        transacciones.executeWithoutResult(estado -> prestaciones.findById(id).orElseThrow());

        assertThat(columna("prestacion", "actualizado_en", id)).isEqualTo(ALTA);
    }

    // ---- ida y vuelta de cada entidad (D3, D4) ----

    private Odontologo nuevoOdontologo(String slug) {
        return odontologos.saveAndFlush(
                new Odontologo("Ana", "Pérez", slug + "@example.com", "!sin-hash", slug));
    }

    @Test
    void odontologoIdaYVueltaYBusquedaPorSlug() {
        Odontologo guardado = nuevoOdontologo("dra-perez");

        Odontologo leido = odontologos.findBySlug("dra-perez").orElseThrow();

        assertThat(leido.getId()).isEqualTo(guardado.getId());
        assertThat(leido.getNombre()).isEqualTo("Ana");
        assertThat(leido.getApellido()).isEqualTo("Pérez");
        assertThat(leido.getEmail()).isEqualTo("dra-perez@example.com");
        assertThat(leido.isActivo()).isTrue();
        assertThat(odontologos.findBySlug("no-existe")).isEmpty();
    }

    @Test
    void odontologoCambiaSuPasswordHash() {
        Odontologo guardado = nuevoOdontologo("cambio-hash");

        transacciones.executeWithoutResult(
                estado -> odontologos.findById(guardado.getId()).orElseThrow().cambiarPasswordHash("otro-hash"));

        assertThat(odontologos.findById(guardado.getId()).orElseThrow().getPasswordHash()).isEqualTo("otro-hash");
    }

    @Test
    void horarioAtencionConservaLaHoraLocal() {
        Odontologo od = nuevoOdontologo("od-horario");

        HorarioAtencion guardado = horarios.saveAndFlush(
                new HorarioAtencion(od.getId(), (short) 3, LocalTime.of(16, 0), LocalTime.of(20, 30)));

        HorarioAtencion leido = horarios.findById(guardado.getId()).orElseThrow();
        assertThat(leido.getDiaSemana()).isEqualTo((short) 3);
        assertThat(leido.getHoraInicio()).isEqualTo(LocalTime.of(16, 0));
        assertThat(leido.getHoraFin()).isEqualTo(LocalTime.of(20, 30));
    }

    @Test
    void bloqueoIdaYVuelta() {
        Odontologo od = nuevoOdontologo("od-bloqueo");
        Instant inicio = Instant.parse("2026-11-26T12:00:00Z");
        Instant fin = Instant.parse("2026-11-26T15:00:00Z");

        Bloqueo guardado = bloqueos.saveAndFlush(new Bloqueo(od.getId(), inicio, fin, "Congreso"));

        Bloqueo leido = bloqueos.findById(guardado.getId()).orElseThrow();
        assertThat(leido.getInicio()).isEqualTo(inicio);
        assertThat(leido.getFin()).isEqualTo(fin);
        assertThat(leido.getMotivo()).isEqualTo("Congreso");
    }

    @Test
    void pacienteIdaYVuelta() {
        Odontologo od = nuevoOdontologo("od-paciente");

        Paciente guardado = pacientes.saveAndFlush(new Paciente(od.getId(), "12345678", "Luis", "Gómez",
                "+5493511234567", "luis@example.com"));

        Paciente leido = pacientes.findById(guardado.getId()).orElseThrow();
        assertThat(leido.getDni()).isEqualTo("12345678");
        assertThat(leido.getTelefono()).isEqualTo("+5493511234567");
        assertThat(leido.getEmail()).isEqualTo("luis@example.com");
    }

    private Turno nuevoTurno(Odontologo od, String dni, Instant inicio, String tokenHash) {
        Paciente paciente = pacientes.saveAndFlush(
                new Paciente(od.getId(), dni, "Luis", "Gómez", "+5493511234567", dni + "@example.com"));
        Prestacion prestacion = prestaciones.saveAndFlush(new Prestacion(od.getId(), "P" + dni, (short) 30));
        return turnos.saveAndFlush(new Turno(od.getId(), paciente, prestacion, inicio, inicio.plusSeconds(1800),
                OrigenTurno.ONLINE, tokenHash));
    }

    @Test
    void turnoIdaYVueltaConEnumsYTokenHashDe64() {
        Odontologo od = nuevoOdontologo("od-turno");
        String hash = FixturesJdbc.tokenHashAleatorio();
        Instant inicio = Instant.parse("2026-11-25T12:00:00Z");

        Turno guardado = nuevoTurno(od, "30111222", inicio, hash);

        transacciones.executeWithoutResult(estado -> {
            Turno leido = turnos.findById(guardado.getId()).orElseThrow();
            assertThat(leido.getInicio()).isEqualTo(inicio);
            assertThat(leido.getFin()).isEqualTo(inicio.plusSeconds(1800));
            assertThat(leido.getEstado()).isEqualTo(EstadoTurno.RESERVADO);
            assertThat(leido.getOrigen()).isEqualTo(OrigenTurno.ONLINE);
            assertThat(leido.getCanceladoPor()).isNull();
            assertThat(leido.getTokenHash()).isEqualTo(hash).hasSize(64);
            assertThat(leido.getPaciente().getDni()).isEqualTo("30111222");
            assertThat(leido.getPrestacion().getDuracionMinutos()).isEqualTo((short) 30);
        });
        assertThat(jdbc.queryForObject("select estado from turno where id = ?", String.class, guardado.getId()))
                .isEqualTo("RESERVADO");
    }

    @Test
    void turnoCanceladoSeLeeConQuienCanceloYCuando() {
        long od = fx.odontologo("od-turno-cancel");
        long turno = fx.turno(od, fx.paciente(od, "30111223"), fx.prestacion(od, "Consulta", 30),
                Instant.parse("2026-11-25T13:00:00Z"), Instant.parse("2026-11-25T13:30:00Z"), "CANCELADO");
        jdbc.update("update turno set cancelado_por = 'ODONTOLOGO', cancelado_en = ?::timestamptz where id = ?",
                "2026-11-24T09:00:00Z", turno);

        transacciones.executeWithoutResult(estado -> {
            Turno leido = turnos.findById(turno).orElseThrow();
            assertThat(leido.getEstado()).isEqualTo(EstadoTurno.CANCELADO);
            assertThat(leido.getCanceladoPor()).isEqualTo(CanceladoPor.ODONTOLOGO);
            assertThat(leido.getCanceladoEn()).isEqualTo(Instant.parse("2026-11-24T09:00:00Z"));
        });
    }

    @Test
    void elInstanteSeMantieneEstableConOtraZonaPorDefectoEnLaJvm() {
        Odontologo od = nuevoOdontologo("od-zona");
        TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));
        Instant inicio = Instant.parse("2026-11-25T12:00:00Z");

        Turno guardado = nuevoTurno(od, "30111224", inicio, FixturesJdbc.tokenHashAleatorio());

        assertThat(turnos.findById(guardado.getId()).orElseThrow().getInicio()).isEqualTo(inicio);
        assertThat(columna("turno", "inicio", guardado.getId())).isEqualTo(inicio);
    }

    @Test
    void notificacionIdaYVuelta() {
        Odontologo od = nuevoOdontologo("od-notif");
        Turno turno = nuevoTurno(od, "30111225", Instant.parse("2026-11-25T14:00:00Z"),
                FixturesJdbc.tokenHashAleatorio());

        Notificacion guardada = notificaciones.saveAndFlush(new Notificacion(turno, TipoNotificacion.CONFIRMACION,
                "luis@example.com", ALTA, EstadoNotificacion.ERROR, "SMTP caído"));

        transacciones.executeWithoutResult(estado -> {
            Notificacion leida = notificaciones.findById(guardada.getId()).orElseThrow();
            assertThat(leida.getTipo()).isEqualTo(TipoNotificacion.CONFIRMACION);
            assertThat(leida.getEstado()).isEqualTo(EstadoNotificacion.ERROR);
            assertThat(leida.getEnviadoEn()).isEqualTo(ALTA);
            assertThat(leida.getError()).isEqualTo("SMTP caído");
            assertThat(leida.getTurno().getId()).isEqualTo(turno.getId());
        });
    }
}
