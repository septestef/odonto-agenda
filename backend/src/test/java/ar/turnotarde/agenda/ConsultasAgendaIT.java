package ar.turnotarde.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnotarde.odontologo.OdontologoRepository;
import ar.turnotarde.prestacion.Prestacion;
import ar.turnotarde.prestacion.PrestacionRepository;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import ar.turnotarde.turno.TurnoRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Consultas de D1 contra PostgreSQL real: cruce de días, estados que ocupan lugar y aislamiento por odontólogo. */
class ConsultasAgendaIT extends IntegracionTest {

    private static final Instant DIA_DESDE = Instant.parse("2026-11-27T03:00:00Z");
    private static final Instant DIA_HASTA = Instant.parse("2026-11-28T03:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private OdontologoRepository odontologos;
    @Autowired
    private PrestacionRepository prestaciones;
    @Autowired
    private HorarioAtencionRepository horarios;
    @Autowired
    private BloqueoRepository bloqueos;
    @Autowired
    private TurnoRepository turnos;

    private FixturesJdbc fixtures;
    private long ana;
    private long otro;
    private long prestacionAna;
    private long pacienteAna;

    @AfterEach
    void dejarLaBaseVacia() {
        LimpiezaBase.limpiar(jdbc);
    }

    @BeforeEach
    void preparar() {
        LimpiezaBase.limpiar(jdbc);
        fixtures = new FixturesJdbc(jdbc);
        ana = fixtures.odontologo("ana");
        otro = fixtures.odontologo("otro");
        prestacionAna = fixtures.prestacion(ana, "Consulta", 30);
        pacienteAna = fixtures.paciente(ana, "12345678");
    }

    // ---- Odontólogo y prestaciones

    @Test
    void buscaOdontologoActivoPorSlugYIgnoraInactivosYDesconocidos() {
        jdbc.update("UPDATE odontologo SET activo = false WHERE id = ?", otro);

        assertThat(odontologos.findBySlugAndActivoTrue("ana")).isPresent();
        assertThat(odontologos.findBySlugAndActivoTrue("otro")).isEmpty();
        assertThat(odontologos.findBySlugAndActivoTrue("no-existe")).isEmpty();
    }

    @Test
    void prestacionActivaDelMismoOdontologoSeEncuentraYLasAjenasODeBajaNo() {
        long deBaja = fixtures.prestacion(ana, "Vieja", 30);
        jdbc.update("UPDATE prestacion SET activa = false WHERE id = ?", deBaja);
        long deOtro = fixtures.prestacion(otro, "Ajena", 30);

        assertThat(prestaciones.findByIdAndOdontologoIdAndActivaTrue(prestacionAna, ana)).isPresent();
        assertThat(prestaciones.findByIdAndOdontologoIdAndActivaTrue(deBaja, ana)).isEmpty();
        assertThat(prestaciones.findByIdAndOdontologoIdAndActivaTrue(deOtro, ana)).isEmpty();
    }

    @Test
    void listaSoloLasPrestacionesActivasDelOdontologoOrdenadasPorNombre() {
        fixtures.prestacion(ana, "Limpieza", 45);
        long baja = fixtures.prestacion(ana, "Blanqueamiento", 60);
        jdbc.update("UPDATE prestacion SET activa = false WHERE id = ?", baja);
        fixtures.prestacion(otro, "Ortodoncia", 30);

        List<Prestacion> lista = prestaciones.findByOdontologoIdAndActivaTrueOrderByNombre(ana);

        assertThat(lista).extracting(Prestacion::getNombre).containsExactly("Consulta", "Limpieza");
    }

    // ---- Horarios

    @Test
    void horariosDelDiaSemanaOrdenadosPorHoraYSoloDelOdontologo() {
        fixtures.horario(ana, 5, "16:00", "17:00");
        fixtures.horario(ana, 5, "09:00", "10:00");
        fixtures.horario(ana, 4, "09:00", "10:00");
        fixtures.horario(otro, 5, "08:00", "09:00");

        List<HorarioAtencion> viernes = horarios.findByOdontologoIdAndDiaSemanaOrderByHoraInicio(ana, (short) 5);

        assertThat(viernes).extracting(h -> h.getHoraInicio().toString()).containsExactly("09:00", "16:00");
    }

    // ---- Bloqueos

    @Test
    void bloqueosQueCruzanElDiaSeProyectanComoIntervalosIncluidoElQueEmpiezaElDiaAnterior() {
        fixtures.bloqueo(ana, Instant.parse("2026-11-26T21:00:00Z"), Instant.parse("2026-11-27T06:00:00Z"));
        fixtures.bloqueo(ana, Instant.parse("2026-11-27T13:00:00Z"), Instant.parse("2026-11-27T14:00:00Z"));
        fixtures.bloqueo(ana, Instant.parse("2026-11-28T02:00:00Z"), Instant.parse("2026-11-29T12:00:00Z"));

        List<Intervalo> encontrados = bloqueos.intervalosQueCruzan(ana, DIA_DESDE, DIA_HASTA);

        assertThat(encontrados).hasSize(3);
    }

    @Test
    void bloqueosContiguosAlDiaOAjenosNoSeDevuelven() {
        fixtures.bloqueo(ana, Instant.parse("2026-11-26T20:00:00Z"), DIA_DESDE);
        fixtures.bloqueo(ana, DIA_HASTA, Instant.parse("2026-11-28T10:00:00Z"));
        fixtures.bloqueo(otro, Instant.parse("2026-11-27T13:00:00Z"), Instant.parse("2026-11-27T14:00:00Z"));

        assertThat(bloqueos.intervalosQueCruzan(ana, DIA_DESDE, DIA_HASTA)).isEmpty();
    }

    // ---- Turnos

    @Test
    void turnosActivosOcupanLugarYLosCanceladosNo() {
        fixtures.turno(ana, pacienteAna, prestacionAna, Instant.parse("2026-11-27T12:00:00Z"),
                Instant.parse("2026-11-27T12:30:00Z"), "RESERVADO");
        fixtures.turno(ana, pacienteAna, prestacionAna, Instant.parse("2026-11-27T13:00:00Z"),
                Instant.parse("2026-11-27T13:30:00Z"), "ATENDIDO");
        fixtures.turno(ana, pacienteAna, prestacionAna, Instant.parse("2026-11-27T14:00:00Z"),
                Instant.parse("2026-11-27T14:30:00Z"), "AUSENTE");
        jdbc.update("""
                INSERT INTO turno (odontologo_id, paciente_id, prestacion_id, inicio, fin, estado, origen,
                                   cancelado_por, token_hash, creado_en, actualizado_en)
                VALUES (?, ?, ?, '2026-11-27T12:00:00Z', '2026-11-27T12:30:00Z', 'CANCELADO', 'ONLINE', 'PACIENTE',
                        ?, now(), now())""", ana, pacienteAna, prestacionAna, FixturesJdbc.tokenHashAleatorio());

        List<Intervalo> ocupados = turnos.intervalosActivosQueCruzan(ana, DIA_DESDE, DIA_HASTA);

        assertThat(ocupados).containsExactlyInAnyOrder(
                new Intervalo(Instant.parse("2026-11-27T12:00:00Z"), Instant.parse("2026-11-27T12:30:00Z")),
                new Intervalo(Instant.parse("2026-11-27T13:00:00Z"), Instant.parse("2026-11-27T13:30:00Z")),
                new Intervalo(Instant.parse("2026-11-27T14:00:00Z"), Instant.parse("2026-11-27T14:30:00Z")));
    }

    @Test
    void turnoQueCruzaElLimiteDelDiaSeIncluye() {
        fixtures.turno(ana, pacienteAna, prestacionAna, Instant.parse("2026-11-27T02:45:00Z"),
                Instant.parse("2026-11-27T03:15:00Z"), "RESERVADO");
        fixtures.turno(ana, pacienteAna, prestacionAna, Instant.parse("2026-11-28T02:45:00Z"),
                Instant.parse("2026-11-28T03:15:00Z"), "RESERVADO");

        assertThat(turnos.intervalosActivosQueCruzan(ana, DIA_DESDE, DIA_HASTA)).containsExactlyInAnyOrder(
                new Intervalo(Instant.parse("2026-11-27T02:45:00Z"), Instant.parse("2026-11-27T03:15:00Z")),
                new Intervalo(Instant.parse("2026-11-28T02:45:00Z"), Instant.parse("2026-11-28T03:15:00Z")));
    }

    @Test
    void turnosContiguosAlDiaPorAmbosLadosNoSeDevuelven() {
        fixtures.turno(ana, pacienteAna, prestacionAna, Instant.parse("2026-11-27T02:00:00Z"), DIA_DESDE,
                "RESERVADO");
        fixtures.turno(ana, pacienteAna, prestacionAna, DIA_HASTA, Instant.parse("2026-11-28T03:30:00Z"),
                "RESERVADO");

        assertThat(turnos.intervalosActivosQueCruzan(ana, DIA_DESDE, DIA_HASTA)).isEmpty();
    }

    @Test
    void turnosDeOtroOdontologoNoOcupanLugar() {
        long pacienteOtro = fixtures.paciente(otro, "12345678");
        long prestacionOtro = fixtures.prestacion(otro, "Consulta", 30);
        fixtures.turno(otro, pacienteOtro, prestacionOtro, Instant.parse("2026-11-27T12:00:00Z"),
                Instant.parse("2026-11-27T12:30:00Z"), "RESERVADO");

        assertThat(turnos.intervalosActivosQueCruzan(ana, DIA_DESDE, DIA_HASTA)).isEmpty();
    }

    @Test
    void cuentaSoloReservadosFuturosDelPacienteConEseOdontologo() {
        Instant ahora = Instant.parse("2026-11-25T12:00:00Z");
        fixtures.turno(ana, pacienteAna, prestacionAna, Instant.parse("2026-11-27T12:00:00Z"),
                Instant.parse("2026-11-27T12:30:00Z"), "RESERVADO");
        fixtures.turno(ana, pacienteAna, prestacionAna, Instant.parse("2026-11-27T13:00:00Z"),
                Instant.parse("2026-11-27T13:30:00Z"), "ATENDIDO");
        fixtures.turno(ana, pacienteAna, prestacionAna, Instant.parse("2026-11-20T12:00:00Z"),
                Instant.parse("2026-11-20T12:30:00Z"), "RESERVADO");
        long otroPaciente = fixtures.paciente(ana, "87654321");
        fixtures.turno(ana, otroPaciente, prestacionAna, Instant.parse("2026-11-28T12:00:00Z"),
                Instant.parse("2026-11-28T12:30:00Z"), "RESERVADO");

        assertThat(turnos.contarReservadosFuturos(ana, pacienteAna, ahora)).isEqualTo(1);
        assertThat(turnos.contarReservadosFuturos(ana, otroPaciente, ahora)).isEqualTo(1);
        assertThat(turnos.contarReservadosFuturos(otro, pacienteAna, ahora)).isZero();
    }

    @Test
    void unTurnoQueEmpiezaExactamenteAhoraNoEsFuturo() {
        Instant ahora = Instant.parse("2026-11-27T12:00:00Z");
        fixtures.turno(ana, pacienteAna, prestacionAna, ahora, Instant.parse("2026-11-27T12:30:00Z"), "RESERVADO");

        assertThat(turnos.contarReservadosFuturos(ana, pacienteAna, ahora)).isZero();
    }
}
