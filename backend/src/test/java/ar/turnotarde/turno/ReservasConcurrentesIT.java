package ar.turnotarde.turno;

import static org.assertj.core.api.Assertions.assertThat;

import ar.turnotarde.odontologo.Odontologo;
import ar.turnotarde.odontologo.OdontologoRepository;
import ar.turnotarde.paciente.Paciente;
import ar.turnotarde.paciente.PacienteRepository;
import ar.turnotarde.prestacion.Prestacion;
import ar.turnotarde.prestacion.PrestacionRepository;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Test obligatorio de RN-TU-01 (DD-03): dos transacciones concurrentes reservan el mismo horario
 * por el camino JPA y la base deja pasar exactamente una.
 */
class ReservasConcurrentesIT extends IntegracionTest {

    private static final Instant INICIO = Instant.parse("2026-11-25T13:00:00Z");
    private static final Instant FIN = Instant.parse("2026-11-25T13:30:00Z");

    @Autowired
    private DataSource dataSource;

    @Autowired
    private TransactionTemplate transacciones;

    @Autowired
    private OdontologoRepository odontologos;

    @Autowired
    private PacienteRepository pacientes;

    @Autowired
    private PrestacionRepository prestaciones;

    @Autowired
    private TurnoRepository turnos;

    private JdbcTemplate jdbc;
    private Odontologo odontologo;
    private Paciente paciente;
    private Prestacion prestacion;

    @BeforeEach
    void preparar() {
        jdbc = new JdbcTemplate(dataSource);
        LimpiezaBase.limpiar(jdbc);
        odontologo = odontologos.saveAndFlush(new Odontologo("Ana", "Perez", "ana@example.com", "!sin-hash", "ana"));
        paciente = pacientes.saveAndFlush(new Paciente(odontologo.getId(), "12345678", "Luis", "Gomez",
                "+5493511234567", "luis@example.com"));
        prestacion = prestaciones.saveAndFlush(new Prestacion(odontologo.getId(), "Consulta", (short) 30));
    }

    @RepeatedTest(5)
    void dosReservasSimultaneasDelMismoHorarioSoloConfirmaUna() throws Exception {
        CountDownLatch largada = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Throwable>> resultados = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                resultados.add(pool.submit(() -> reservar(largada)));
            }
            largada.countDown();

            List<Throwable> fallas = new ArrayList<>();
            for (Future<Throwable> resultado : resultados) {
                Throwable falla = resultado.get(30, TimeUnit.SECONDS);
                if (falla != null) {
                    fallas.add(falla);
                }
            }

            assertThat(fallas).as("exactamente una transacción falla").hasSize(1);
            Throwable falla = fallas.get(0);
            assertThat(falla).isInstanceOf(DataIntegrityViolationException.class);
            Throwable causa = ((DataIntegrityViolationException) falla).getRootCause();
            assertThat(causa).isInstanceOf(SQLException.class);
            assertThat(((SQLException) causa).getSQLState()).isEqualTo("23P01");
            assertThat(causa.getMessage()).contains("turno_sin_solapamiento");

            Integer activos = jdbc.queryForObject(
                    "select count(*) from turno where estado = 'RESERVADO' and odontologo_id = ?",
                    Integer.class, odontologo.getId());
            assertThat(activos).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    /** Devuelve null si la transacción confirmó, o la excepción con la que falló. */
    private Throwable reservar(CountDownLatch largada) {
        try {
            largada.await();
            transacciones.executeWithoutResult(estado -> turnos.saveAndFlush(new Turno(odontologo.getId(), paciente,
                    prestacion, INICIO, FIN, OrigenTurno.ONLINE, FixturesJdbc.tokenHashAleatorio())));
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return e;
        } catch (RuntimeException e) {
            return e;
        }
    }
}
