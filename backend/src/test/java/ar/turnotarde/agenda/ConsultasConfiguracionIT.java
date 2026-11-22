package ar.turnotarde.agenda;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnotarde.prestacion.PrestacionRepository;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import ar.turnotarde.turno.EstadoTurno;
import ar.turnotarde.turno.TurnoRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Los métodos nuevos de repositorio filtran por dueño y no rompen las consultas que usa C-07. */
class ConsultasConfiguracionIT extends IntegracionTest {

    private static final Instant DESDE = Instant.parse("2026-11-27T13:00:00Z");
    private static final Instant HASTA = Instant.parse("2026-11-27T14:30:00Z");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private HorarioAtencionRepository horarios;
    @Autowired
    private BloqueoRepository bloqueos;
    @Autowired
    private PrestacionRepository prestaciones;
    @Autowired
    private TurnoRepository turnos;
    @Autowired
    private PlatformTransactionManager transacciones;

    private long idA;
    private long idB;
    private long reservado;
    private long atendido;
    private long ausente;

    @BeforeEach
    void preparar() {
        LimpiezaBase.limpiar(jdbc);
        FixturesJdbc fixtures = new FixturesJdbc(jdbc);
        idA = fixtures.odontologo("cfg-a-" + UUID.randomUUID());
        idB = fixtures.odontologo("cfg-b-" + UUID.randomUUID());

        fixtures.horario(idA, 5, "16:00", "20:00");
        fixtures.horario(idA, 1, "09:00", "13:00");
        fixtures.horario(idA, 1, "16:00", "20:00");
        fixtures.horario(idB, 1, "09:00", "10:00");

        long zafiro = fixtures.prestacion(idA, "Zafiro", 30);
        long limpieza = fixtures.prestacion(idA, "Limpieza", 45);
        jdbc.update("UPDATE prestacion SET activa = false WHERE id = ?", zafiro);
        fixtures.prestacion(idB, "Limpieza", 30);

        fixtures.bloqueo(idA, DESDE, HASTA);
        fixtures.bloqueo(idB, DESDE, HASTA);

        long paciente = fixtures.paciente(idA, "12345678");
        long prestacion = fixtures.prestacion(idA, "Consulta", 30);
        reservado = fixtures.turno(idA, paciente, prestacion, DESDE, Instant.parse("2026-11-27T13:30:00Z"),
                EstadoTurno.RESERVADO.name());
        atendido = fixtures.turno(idA, paciente, prestacion, Instant.parse("2026-11-27T13:30:00Z"),
                Instant.parse("2026-11-27T14:00:00Z"), EstadoTurno.ATENDIDO.name());
        ausente = fixtures.turno(idA, paciente, prestacion, Instant.parse("2026-11-27T14:00:00Z"), HASTA,
                EstadoTurno.AUSENTE.name());
        fixtures.turno(idA, paciente, prestacion, Instant.parse("2026-11-27T13:15:00Z"),
                Instant.parse("2026-11-27T13:45:00Z"), EstadoTurno.CANCELADO.name());
        fixtures.turno(idA, paciente, prestacion, HASTA, Instant.parse("2026-11-27T15:00:00Z"),
                EstadoTurno.RESERVADO.name());
        long pacienteB = fixtures.paciente(idB, "87654321");
        long prestacionB = fixtures.prestacion(idB, "Consulta", 30);
        fixtures.turno(idB, pacienteB, prestacionB, DESDE, HASTA, EstadoTurno.RESERVADO.name());

        assertThat(limpieza).isPositive();
        assertThat(zafiro).isPositive();
    }

    @AfterEach
    void dejarLaBaseVacia() {
        LimpiezaBase.limpiar(jdbc);
    }

    @Test
    void horariosNuevosDevuelvenSoloLosPropiosOrdenadosPorDiaYHora() {
        assertThat(horarios.findByOdontologoIdOrderByDiaSemanaHoraInicio(idA))
                .extracting(HorarioAtencion::getDiaSemana, HorarioAtencion::getHoraInicio)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple((short) 1, java.time.LocalTime.of(9, 0)),
                        org.assertj.core.groups.Tuple.tuple((short) 1, java.time.LocalTime.of(16, 0)),
                        org.assertj.core.groups.Tuple.tuple((short) 5, java.time.LocalTime.of(16, 0)));
        assertThat(horarios.findByOdontologoIdOrderByDiaSemanaHoraInicio(idB)).hasSize(1);
    }

    @Test
    void borradoPorOdontologoNoTocaLosDemas() {
        // El borrado derivado exige transacción real; se simuló el @Transactional del servicio.
        new TransactionTemplate(transacciones).executeWithoutResult(estado -> {
            horarios.deleteByOdontologoId(idA);
            horarios.flush();
        });

        assertThat(horarios.findByOdontologoIdOrderByDiaSemanaHoraInicio(idA)).isEmpty();
        assertThat(horarios.findByOdontologoIdOrderByDiaSemanaHoraInicio(idB)).hasSize(1);
    }

    @Test
    void prestacionesNuevasIncluyenInactivasYRespetanElDueno() {
        assertThat(prestaciones.findByOdontologoIdOrderByNombre(idA))
                .extracting(ar.turnotarde.prestacion.Prestacion::getNombre)
                .containsExactly("Consulta", "Limpieza", "Zafiro");
        assertThat(prestaciones.findByOdontologoIdOrderByNombre(idB)).hasSize(2);
        assertThat(prestaciones.findByIdAndOdontologoId(123L, idB)).isEmpty();
        assertThat(prestaciones.existsByOdontologoIdAndNombre(idA, "Limpieza")).isTrue();
        assertThat(prestaciones.existsByOdontologoIdAndNombre(idB, "Zafiro")).isFalse();
    }

    @Test
    void bloqueosNuevosFiltranPorDueno() {
        List<Bloqueo> propios = bloqueos.findByOdontologoIdOrderByInicio(idA);
        assertThat(propios).hasSize(1);
        assertThat(bloqueos.findByIdAndOdontologoId(propios.get(0).getId(), idB)).isEmpty();
    }

    @Test
    void turnosAfectadosExcluyeCanceladosContiguosYAjenosYOrdenaPorInicio() {
        List<TurnoAfectadoProyeccion> afectados = turnos.turnosAfectados(idA, DESDE, HASTA);

        assertThat(afectados).extracting(TurnoAfectadoProyeccion::id)
                .containsExactly(reservado, atendido, ausente);
        assertThat(afectados).extracting(TurnoAfectadoProyeccion::estado)
                .containsExactly(EstadoTurno.RESERVADO, EstadoTurno.ATENDIDO, EstadoTurno.AUSENTE);
        assertThat(afectados).allSatisfy(t -> {
            assertThat(t.pacienteNombre()).isEqualTo("Paciente");
            assertThat(t.prestacionNombre()).isEqualTo("Consulta");
        });
    }

    @Test
    void consultasQueConsumeC07SiguenDisponibles() {
        assertThat(horarios.findByOdontologoIdAndDiaSemanaOrderByHoraInicio(idA, (short) 1)).hasSize(2);
        assertThat(prestaciones.findByOdontologoIdAndActivaTrueOrderByNombre(idA))
                .extracting(ar.turnotarde.prestacion.Prestacion::getNombre)
                .containsExactly("Consulta", "Limpieza");
        assertThat(bloqueos.intervalosQueCruzan(idA, DESDE, HASTA)).hasSize(1);
        assertThat(turnos.intervalosActivosQueCruzan(idA, DESDE, HASTA)).hasSize(3);
    }
}
