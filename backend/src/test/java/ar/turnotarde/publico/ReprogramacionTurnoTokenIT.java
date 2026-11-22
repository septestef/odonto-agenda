package ar.turnotarde.publico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

import ar.turnotarde.soporte.ConfiguracionRelojDePrueba;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import ar.turnotarde.soporte.RelojDePrueba;
import ar.turnotarde.agenda.Intervalo;
import com.zaxxer.hikari.HikariDataSource;
import ar.turnotarde.turno.TokenTurno;
import ar.turnotarde.turno.TurnoRepository;
import ar.turnotarde.turno.eventos.TurnoReprogramado;
import ar.turnotarde.turno.eventos.TurnoCancelado;
import ar.turnotarde.turno.eventos.TurnoReservado;
import com.jayway.jsonpath.JsonPath;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({ConfiguracionRelojDePrueba.class, ReprogramacionTurnoTokenIT.Captura.class})
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {"MAIL_HOST=smtp.example.test", "MAIL_PORT=587",
        "MAIL_USERNAME=origen@example.test", "MAIL_PASSWORD=prueba-sintetica", "MAIL_FROM_NAME=Prueba"})
class ReprogramacionTurnoTokenIT extends IntegracionTest {
    private static final Instant AHORA = Instant.parse("2026-11-25T12:00:00Z");
    private static final Instant INICIO = Instant.parse("2026-11-27T12:00:00Z");
    private static final Instant DESTINO = Instant.parse("2026-11-27T13:00:00Z");
    private static final Instant OTRO_DESTINO = DESTINO.plusSeconds(3600);
    private static final String TOKEN = "F".repeat(42) + "A";
    private static final String OTRO_TOKEN = "G".repeat(42) + "A";
    private static final String CORREO = "snapshot@example.test";
    private static final String DNI = "12345678";
    private static final String API = "/api/public/turnos/";
    private static final Pattern HASH_SHA256 = Pattern.compile("\\b[0-9a-f]{64}\\b");

    @LocalServerPort int puerto;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired RelojDePrueba reloj;
    @Autowired Escucha escucha;
    @Autowired @Qualifier("ejecutorNotificaciones") Executor ejecutorNotificaciones;
    @MockitoSpyBean TurnoRepository turnos;

    private FixturesJdbc fx;
    private long od;
    private long prestacion;
    private long paciente;

    @BeforeEach
    void preparar() {
        drenarNotificaciones();
        LimpiezaBase.limpiar(jdbc);
        reloj.fijar(AHORA);
        escucha.confirmados.set(0);
        escucha.reservados.set(0);
        escucha.cancelados.set(0);
        escucha.errorSql.set(null);
        fx = new FixturesJdbc(jdbc);
        od = fx.odontologo("demo");
        prestacion = fx.prestacion(od, "Consulta", 30);
        paciente = fx.paciente(od, DNI);
        for (int dia = 1; dia <= 7; dia++) {
            fx.horario(od, dia, "09:00", "13:00");
        }
    }

    @AfterEach
    void limpiar() {
        drenarNotificaciones();
        reloj.fijar(AHORA);
        LimpiezaBase.limpiar(jdbc);
    }

    @ParameterizedTest
    @CsvSource(value = {
        "application/json|{}|400|VALIDACION", "application/json|{\"inicio\":null}|400|VALIDACION",
        "application/json|{|400|SOLICITUD_INVALIDA",
        "application/json|{\"inicio\":\"2026-11-27T10:00:00\"}|400|SOLICITUD_INVALIDA",
        "application/json|{\"inicio\":123}|400|SOLICITUD_INVALIDA",
        "application/json|{\"inicio\":\"2026-11-27T10:00:00-03:00\",\"email\":\"otro@example.test\"}|400|SOLICITUD_INVALIDA",
        "text/plain|{}|415|SOLICITUD_INVALIDA"
    }, delimiter = '|')
    void contratoHttpPrecedeToken(String mime, String body, int estado, String codigo) throws Exception {
        var res = post(API + TOKEN + "/reprogramar", mime, body);
        error(res, estado, codigo);
        if (codigo.equals("VALIDACION")) {
            assertThat((String) JsonPath.read(res.body(), "$.detalles[0].campo")).isEqualTo("inicio");
        }
    }

    @Test
    void precedenciasProtegenOriginalLegadoYDesconocidoSinEscribir() throws Exception {
        error(mover(TOKEN, DESTINO), 404, "NO_ENCONTRADO");
        long original = turno(paciente, INICIO, TOKEN, null);
        jdbc.update("UPDATE prestacion SET activa=false WHERE id=?", prestacion);
        error(mover(TOKEN, AHORA), 404, "NO_ENCONTRADO");
        jdbc.update("UPDATE prestacion SET activa=true WHERE id=?", prestacion);
        jdbc.update("UPDATE odontologo SET activo=false WHERE id=?", od);
        error(mover(TOKEN, AHORA), 404, "NO_ENCONTRADO");
        jdbc.update("UPDATE odontologo SET activo=true WHERE id=?", od);
        error(mover(TOKEN, AHORA), 409, "RN-SE-04");
        jdbc.update("UPDATE turno SET email_gestion='ana@ejemplo_com.test' WHERE id=?", original);
        error(mover(TOKEN, AHORA), 409, "RN-SE-04");
        jdbc.update("UPDATE turno SET estado='CANCELADO' WHERE id=?", original);
        var antes = jdbc.queryForMap("SELECT * FROM turno WHERE id=?", original);
        error(mover(TOKEN, AHORA), 409, "RN-SE-02");
        reloj.fijar(INICIO);
        error(mover(TOKEN, AHORA), 409, "RN-TU-07");
        assertThat(jdbc.queryForMap("SELECT * FROM turno WHERE id=?", original)).isEqualTo(antes);
        assertThat(sucesores(original)).isZero();
        sinAvisoReprogramacion();
    }

    @Test
    void rechazosDeAgendaIncluyenOriginalSinAlterarEstadoNiAvisar() throws Exception {
        long id = turno(paciente, INICIO, TOKEN, CORREO);
        var antes = jdbc.queryForMap("SELECT * FROM turno WHERE id=?", id);
        error(mover(TOKEN, AHORA.plusSeconds(86399)), 422, "RN-TU-03");
        error(mover(TOKEN, Instant.parse("2027-01-25T12:00:00Z")), 422, "RN-TU-11");
        error(mover(TOKEN, DESTINO.plusSeconds(60)), 422, "RN-AG-05");
        error(mover(TOKEN, Instant.parse("2026-11-27T15:45:00Z")), 422, "RN-AG-05");
        error(mover(TOKEN, Instant.parse("2026-11-27T11:45:00Z")), 422, "RN-AG-05");
        error(moverTexto(TOKEN, "2026-11-27T14:00:00+02:00"), 409, "RN-TU-01");
        error(mover(TOKEN, INICIO.plusSeconds(900)), 409, "RN-TU-01");
        fx.bloqueo(od, DESTINO.plusSeconds(900), DESTINO.plusSeconds(1800));
        error(mover(TOKEN, DESTINO), 422, "RN-AG-05");
        assertThat(jdbc.queryForMap("SELECT * FROM turno WHERE id=?", id)).isEqualTo(antes);
        assertThat(sucesores(id)).isZero();
        sinAvisoReprogramacion();
    }

    @Test
    void reemplazaEnElCupoConSnapshotHashYDuracionVigenteSinModificarContacto() throws Exception {
        long original = turno(paciente, INICIO, TOKEN, CORREO);
        turno(paciente, INICIO.plusSeconds(7200), OTRO_TOKEN, CORREO);
        jdbc.update("UPDATE paciente SET email='vigente@example.test' WHERE id=?", paciente);
        jdbc.update("UPDATE prestacion SET duracion_minutos=45 WHERE id=?", prestacion);
        var contacto = jdbc.queryForMap("SELECT * FROM paciente WHERE id=?", paciente);
        Instant contiguo = INICIO.plusSeconds(1800);

        vacia(mover(TOKEN, contiguo), 204);

        var anterior = jdbc.queryForMap("SELECT * FROM turno WHERE id=?", original);
        var nuevo = jdbc.queryForMap("SELECT * FROM turno WHERE reprogramado_desde_id=?", original);
        assertThat(anterior).containsEntry("estado", "CANCELADO").containsEntry("cancelado_por", "PACIENTE")
                .containsEntry("cancelado_en", Timestamp.from(AHORA)).containsEntry("fin", Timestamp.from(contiguo));
        assertThat(nuevo).containsEntry("estado", "RESERVADO").containsEntry("origen", "ONLINE")
                .containsEntry("odontologo_id", od).containsEntry("paciente_id", paciente)
                .containsEntry("prestacion_id", prestacion).containsEntry("email_gestion", CORREO)
                .containsEntry("inicio", Timestamp.from(contiguo))
                .containsEntry("fin", Timestamp.from(contiguo.plusSeconds(2700)))
                .containsEntry("cancelado_en", null).containsEntry("cancelado_por", null)
                .containsEntry("recordatorio_enviado_en", null);
        assertThat((String) nuevo.get("token_hash")).matches("[0-9a-f]{64}").isNotEqualTo(TokenTurno.hashear(TOKEN));
        assertThat(jdbc.queryForMap("SELECT * FROM paciente WHERE id=?", paciente)).isEqualTo(contacto);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno WHERE paciente_id=? AND estado='RESERVADO'",
                Long.class, paciente)).isEqualTo(2L);
        var lectura = get(API + TOKEN);
        assertThat((String) JsonPath.read(lectura.body(), "$.estado")).isEqualTo("CANCELADO");
        assertThat((Boolean) JsonPath.read(lectura.body(), "$.puedeReprogramar")).isFalse();
    }

    @Test
    void rollbackDeLaExclusionRealDespuesDeActualizarOriginal(CapturedOutput salida) throws Exception {
        long original = turno(paciente, INICIO, TOKEN, CORREO);
        long ocupado = turno(fx.paciente(od, "87654321"), DESTINO, OTRO_TOKEN, CORREO);
        var antes = jdbc.queryForMap("SELECT * FROM turno WHERE id=?", original);
        doReturn(List.of()).when(turnos).intervalosActivosQueCruzan(eq(od), any(), any());
        jdbc.execute("CREATE SEQUENCE prueba_c09_update");
        try {
            // La secuencia no retrocede en rollback: prueba que el UPDATE de la fila se ejecutó.
            jdbc.execute("""
                    CREATE FUNCTION prueba_c09_observar_update() RETURNS trigger LANGUAGE plpgsql AS $$
                    BEGIN PERFORM nextval('prueba_c09_update'); RETURN NEW; END; $$""");
            jdbc.execute("""
                    CREATE TRIGGER prueba_c09_observar_update AFTER UPDATE ON turno FOR EACH ROW
                    WHEN (OLD.estado='RESERVADO' AND NEW.estado='CANCELADO' AND OLD.id=%d)
                    EXECUTE FUNCTION prueba_c09_observar_update()""".formatted(original));
            error(mover(TOKEN, DESTINO), 409, "RN-TU-01");
            assertThat(salida.getAll().contains("23P01") && salida.getAll().contains("turno_sin_solapamiento"))
                    .as("el ORM recibió el SQLState de la exclusión real").isTrue();
            try (var independiente = dataSource.getConnection();
                    var sql = independiente.prepareStatement("""
                            SELECT (SELECT is_called FROM prueba_c09_update), estado, cancelado_por, cancelado_en,
                                (SELECT count(*) FROM turno WHERE reprogramado_desde_id=?)
                            FROM turno WHERE id=?""")) {
                sql.setLong(1, original);
                sql.setLong(2, original);
                try (var fila = sql.executeQuery()) {
                    assertThat(fila.next()).isTrue();
                    assertThat(fila.getBoolean(1)).as("el UPDATE real llegó a ejecutar su trigger").isTrue();
                    assertThat(fila.getString(2)).isEqualTo("RESERVADO");
                    assertThat(fila.getObject(3)).isNull();
                    assertThat(fila.getObject(4)).isNull();
                    assertThat(fila.getLong(5)).isZero();
                }
            }
            assertThat(jdbc.queryForMap("SELECT * FROM turno WHERE id=?", original)).isEqualTo(antes);
            assertThat(jdbc.queryForObject("SELECT id FROM turno WHERE inicio=? AND estado='RESERVADO'",
                    Long.class, Timestamp.from(DESTINO))).isEqualTo(ocupado);
            sinAvisoReprogramacion();
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS prueba_c09_observar_update ON turno");
            jdbc.execute("DROP FUNCTION IF EXISTS prueba_c09_observar_update()");
            jdbc.execute("DROP SEQUENCE prueba_c09_update");
        }
    }

    @ParameterizedTest
    @CsvSource({"40P01,409,RN-TU-01", "55P03,409,RN-TU-01", "23505,500,ERROR_INTERNO"})
    void traduceSoloLosConflictosDeEscrituraEnLaFrontera(String sqlState, int estado, String codigo)
            throws Exception {
        long id = turno(paciente, INICIO, TOKEN, CORREO);
        var antes = jdbc.queryForMap("SELECT * FROM turno WHERE id=?", id);
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("fallo sintético de escritura",
                new SQLException("diagnóstico sintético", sqlState)))
                .when(turnos).bloquearPorHash(TokenTurno.hashear(TOKEN));

        var res = mover(TOKEN, DESTINO);
        error(res, estado, codigo);
        if (estado == 409) {
            assertThat(res.body()).isEqualTo("""
                    {"codigo":"RN-TU-01","mensaje":"Ese horario acaba de ser tomado, elegí otro"}""");
        }
        assertThat(jdbc.queryForMap("SELECT * FROM turno WHERE id=?", id)).isEqualTo(antes);
        sinAvisoReprogramacion();
    }

    @Test
    void traduceElLockQueSpringYaClasifico() throws Exception {
        turno(paciente, INICIO, TOKEN, CORREO);
        org.mockito.Mockito.doThrow(new CannotAcquireLockException("fallo sintético de lock"))
                .when(turnos).bloquearPorHash(TokenTurno.hashear(TOKEN));
        var res = mover(TOKEN, DESTINO);
        error(res, 409, "RN-TU-01");
        assertThat(res.body()).isEqualTo("""
                {"codigo":"RN-TU-01","mensaje":"Ese horario acaba de ser tomado, elegí otro"}""");
    }

    @Test
    void hikariConfiguraCadaConexionFisicaSinDetalleDelServidor() throws Exception {
        var hikari = dataSource.unwrap(HikariDataSource.class);
        assertThat(String.valueOf(hikari.getDataSourceProperties().get("logServerErrorDetail"))).isEqualTo("false");
        try (var conexion = hikari.getConnection()) {
            // El driver es runtimeOnly, también para este test.
            Class<?> tipo = Class.forName("org.postgresql.core.BaseConnection");
            assertThat(tipo.getMethod("getLogServerErrorDetail").invoke(conexion.unwrap(tipo))).isEqualTo(false);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void insertRechazadoPorPostgresqlNoExponeCentinelasEnDriverNiLogs(boolean sucesor, CapturedOutput salida)
            throws Exception {
        String email = "centinela-insert-c09@example.test";
        long original = sucesor ? turno(paciente, INICIO, TOKEN, email) : 0;
        jdbc.execute("""
                ALTER TABLE turno ADD CONSTRAINT prueba_c09_rechazo
                CHECK (estado='CANCELADO' OR email_gestion IS DISTINCT FROM 'centinela-insert-c09@example.test')
                NOT VALID
                """);
        try {
            var res = sucesor ? mover(TOKEN, DESTINO) : reservar(DNI, email);
            assertThat(res.statusCode()).isEqualTo(500);
            assertThat((String) JsonPath.read(res.body(), "$.codigo")).isEqualTo("ERROR_INTERNO");
            var ex = escucha.errorSql.get();
            assertThat(ex != null).as("la excepción trae el SQLState de PostgreSQL").isTrue();
            assertThat(ex.getSQLState()).isEqualTo("23514");
            var detalle = ex.getClass().getMethod("getServerErrorMessage").invoke(ex);
            var texto = (String) detalle.getClass().getMethod("getDetail").invoke(detalle);
            assertThat(texto != null && texto.contains(email)).as("el servidor incluyó la fila sintética").isTrue();
            var hash = HASH_SHA256.matcher(texto);
            assertThat(hash.find()).as("el detalle incluye el hash sintético del INSERT").isTrue();
            var centinelas = List.of(email, TOKEN, TokenTurno.hashear(TOKEN), hash.group());
            assertThat(centinelas.stream().anyMatch(ex.getMessage()::contains))
                    .as("el mensaje efectivo del driver no incluye datos de la fila rechazada").isFalse();
            assertThat(salida.getAll().contains("prueba_c09_rechazo")).as("se observó el log del ORM").isTrue();
            assertThat(centinelas.stream().anyMatch(salida.getAll()::contains))
                    .as("app y ORM no exponen destinatario, token o hash sintéticos").isFalse();
            assertThat(centinelas.stream().anyMatch(res.body()::contains)).isFalse();
            if (sucesor) {
                assertThat(sucesores(original)).isZero();
                assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE id=?", String.class, original))
                        .isEqualTo("RESERVADO");
            } else {
                assertThat(jdbc.queryForObject("SELECT count(*) FROM turno", Long.class)).isZero();
            }
        } finally {
            jdbc.execute("ALTER TABLE turno DROP CONSTRAINT prueba_c09_rechazo");
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mismoOriginalUnGanadorYComoMaximoUnSucesor(boolean contraCancelar) throws Exception {
        long original = turno(paciente, INICIO, TOKEN, CORREO);
        var resultados = paralelas(() -> mover(TOKEN, DESTINO),
                () -> contraCancelar ? post(API + TOKEN + "/cancelar", null, null) : mover(TOKEN, OTRO_DESTINO));
        unGanador(resultados, "RN-SE-02", 204);
        boolean ganoMover = resultados.getFirst().statusCode() == 204 || !contraCancelar;
        assertThat(sucesores(original)).isEqualTo(ganoMover ? 1 : 0);
        assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE id=?", String.class, original))
                .isEqualTo("CANCELADO");
        comprobarAvisoGanador(ganoMover ? "REPROGRAMACION" : "CANCELACION",
                ganoMover ? idSucesor(original) : original);
    }

    @Test
    void carreraMismoOriginalEnElLimiteDelCupoConservaDosReservasActivas() throws Exception {
        long original = turno(paciente, INICIO, TOKEN, CORREO);
        long otro = turno(paciente, INICIO.plusSeconds(1800), OTRO_TOKEN, CORREO);
        var antes = jdbc.queryForMap("SELECT * FROM turno WHERE id=?", otro);
        var resultados = paralelas(() -> mover(TOKEN, DESTINO), () -> mover(TOKEN, OTRO_DESTINO));
        unGanador(resultados, "RN-SE-02", 204);
        assertThat(sucesores(original)).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT * FROM turno WHERE id=?", otro)).isEqualTo(antes);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM turno WHERE paciente_id=? AND estado='RESERVADO' AND inicio>?
                """, Long.class, paciente, Timestamp.from(AHORA))).isEqualTo(2L);
        comprobarAvisoGanador("REPROGRAMACION", idSucesor(original));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void originalesAlMismoDestinoInclusoConMismoPaciente(boolean mismoPaciente) throws Exception {
        long uno = turno(paciente, INICIO, TOKEN, CORREO);
        long dos = turno(mismoPaciente ? paciente : fx.paciente(od, "87654321"),
                INICIO.plusSeconds(1800), OTRO_TOKEN, CORREO);
        var antesUno = jdbc.queryForMap("SELECT * FROM turno WHERE id=?", uno);
        var antesDos = jdbc.queryForMap("SELECT * FROM turno WHERE id=?", dos);
        var resultados = paralelas(() -> mover(TOKEN, DESTINO), () -> mover(OTRO_TOKEN, DESTINO));
        unGanador(resultados, "RN-TU-01", 204);
        long ganador = resultados.getFirst().statusCode() == 204 ? uno : dos;
        long perdedor = ganador == uno ? dos : uno;
        assertThat(sucesores(ganador)).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT * FROM turno WHERE id=?", perdedor))
                .isEqualTo(perdedor == uno ? antesUno : antesDos);
        unActivoEnDestino();
        comprobarAvisoGanador("REPROGRAMACION", idSucesor(ganador));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reprogramacionContraReservaC07InclusoMismoPaciente(boolean mismoPaciente) throws Exception {
        long id = turno(paciente, INICIO, TOKEN, CORREO);
        var antes = jdbc.queryForMap("SELECT * FROM turno WHERE id=?", id);
        var respuestas = paralelas(() -> mover(TOKEN, DESTINO), () -> reservar(mismoPaciente ? DNI : "87654321"));
        unGanador(respuestas, "RN-TU-01", 204, 201);
        unActivoEnDestino();
        assertThat(sucesores(id)).isEqualTo(respuestas.getFirst().statusCode() == 204 ? 1 : 0);
        if (respuestas.getFirst().statusCode() == 409) {
            assertThat(jdbc.queryForMap("SELECT * FROM turno WHERE id=?", id)).isEqualTo(antes);
        }
        long destino = jdbc.queryForObject("SELECT id FROM turno WHERE inicio=? AND estado='RESERVADO'",
                Long.class, Timestamp.from(DESTINO));
        comprobarAvisoGanador(respuestas.getFirst().statusCode() == 204 ? "REPROGRAMACION" : "CONFIRMACION", destino);
    }

    @Test
    void esperaLockDeFilaYLuegoVuelveALeerRelojEnElInicioExacto() throws Exception {
        long original = turno(paciente, INICIO, TOKEN, CORREO);
        try (var conexion = dataSource.getConnection(); var ejecutor = Executors.newSingleThreadExecutor()) {
            conexion.setAutoCommit(false);
            int pid;
            try (var stmt = conexion.createStatement(); var fila = stmt.executeQuery("SELECT pg_backend_pid()")) {
                fila.next();
                pid = fila.getInt(1);
            }
            try (var sql = conexion.prepareStatement("SELECT id FROM turno WHERE id=? FOR UPDATE")) {
                sql.setLong(1, original);
                try (var fila = sql.executeQuery()) { assertThat(fila.next()).isTrue(); }
            }
            var respuesta = ejecutor.submit(() -> mover(TOKEN, DESTINO));
            try {
                esperarBloqueadoPor(pid);
                assertThat(advisoryDisponible()).as("ya tomó el advisory antes de esperar al original").isFalse();
                reloj.fijar(INICIO);
            } finally {
                conexion.rollback();
            }
            error(respuesta.get(5, TimeUnit.SECONDS), 409, "RN-TU-07");
        }
        assertThat(sucesores(original)).isZero();
        assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE id=?", String.class, original))
                .isEqualTo("RESERVADO");
    }

    @Test
    void esperaPrimeroAlPacienteSinTomarTodaviaElAdvisoryNiLaFilaOriginal() throws Exception {
        long original = turno(paciente, INICIO, TOKEN, CORREO);
        try (var conexion = dataSource.getConnection(); var ejecutor = Executors.newSingleThreadExecutor()) {
            conexion.setAutoCommit(false);
            int pid;
            try (var stmt = conexion.createStatement(); var fila = stmt.executeQuery("SELECT pg_backend_pid()")) {
                assertThat(fila.next()).isTrue();
                pid = fila.getInt(1);
            }
            try (var sql = conexion.prepareStatement(
                    "SELECT id FROM paciente WHERE id=? AND odontologo_id=? FOR NO KEY UPDATE")) {
                sql.setLong(1, paciente);
                sql.setLong(2, od);
                try (var fila = sql.executeQuery()) { assertThat(fila.next()).isTrue(); }
            }
            var respuesta = ejecutor.submit(() -> mover(TOKEN, DESTINO));
            try {
                esperarBloqueadoPor(pid);
                assertThat(advisoryDisponible()).as("el advisory aún no fue tomado").isTrue();
                assertThat(jdbc.queryForObject("SELECT id FROM turno WHERE id=? FOR UPDATE NOWAIT", Long.class, original))
                        .as("el original aún no fue bloqueado").isEqualTo(original);
            } finally {
                conexion.rollback();
            }
            vacia(respuesta.get(5, TimeUnit.SECONDS), 204);
        }
    }

    private void esperarBloqueadoPor(int pid) {
        await().atMost(Duration.ofSeconds(4)).until(() -> jdbc.queryForObject("""
                SELECT EXISTS (SELECT FROM pg_stat_activity
                WHERE datname=current_database() AND ? = ANY(pg_blocking_pids(pid)))
                """, Boolean.class, pid));
    }

    private boolean advisoryDisponible() {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT pg_try_advisory_xact_lock(hashtext('turno_sin_solapamiento'), hashtext(CAST(? AS text)))
                """, Boolean.class, od));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void capturaUnaVezAntesDeLeerAgendaYRespetaAnticipacionYHorizonte(boolean horizonte) throws Exception {
        Instant capturado = horizonte ? Instant.parse("2026-11-26T02:59:59Z") : AHORA;
        reloj.fijar(capturado);
        long id = turno(paciente, INICIO, TOKEN, CORREO);
        doAnswer(invocacion -> {
            Instant desde = invocacion.getArgument(1);
            Instant hasta = invocacion.getArgument(2);
            reloj.fijar(capturado.plusSeconds(2));
            return jdbc.query("""
                    SELECT inicio, fin FROM turno WHERE odontologo_id=?
                    AND estado<>'CANCELADO' AND inicio<? AND fin>?
                    """, (fila, n) -> new Intervalo(fila.getObject(1, OffsetDateTime.class).toInstant(),
                            fila.getObject(2, OffsetDateTime.class).toInstant()), od,
                    Timestamp.from(hasta), Timestamp.from(desde));
        }).when(turnos).intervalosActivosQueCruzan(eq(od), any(), any());
        if (horizonte) {
            error(mover(TOKEN, Instant.parse("2027-01-25T12:00:00Z")), 422, "RN-TU-11");
            reloj.fijar(capturado);
        }
        vacia(mover(TOKEN, horizonte ? Instant.parse("2027-01-24T12:00:00Z") : AHORA.plusSeconds(86400)), 204);
        Instant marca = jdbc.queryForObject("SELECT cancelado_en FROM turno WHERE id=?",
                (rs, n) -> rs.getObject(1, OffsetDateTime.class).toInstant(), id);
        assertThat(marca).isEqualTo(capturado);
    }

    private long turno(long quien, Instant inicio, String token, String email) {
        long id = fx.turno(od, quien, prestacion, inicio, inicio.plusSeconds(1800), "RESERVADO",
                TokenTurno.hashear(token));
        jdbc.update("UPDATE turno SET email_gestion=? WHERE id=?", email, id);
        return id;
    }

    private long sucesores(long original) {
        return jdbc.queryForObject("SELECT count(*) FROM turno WHERE reprogramado_desde_id=?", Long.class, original);
    }

    private void sinAvisoReprogramacion() {
        drenarNotificaciones();
        assertThat(escucha.confirmados.get()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion WHERE tipo='REPROGRAMACION'",
                Long.class)).isZero();
    }

    private long idSucesor(long original) {
        return jdbc.queryForObject("SELECT id FROM turno WHERE reprogramado_desde_id=?", Long.class, original);
    }

    private void comprobarAvisoGanador(String tipo, long turno) {
        drenarNotificaciones();
        assertThat(escucha.confirmados.get()).isEqualTo(tipo.equals("REPROGRAMACION") ? 1 : 0);
        assertThat(escucha.reservados.get()).isEqualTo(tipo.equals("CONFIRMACION") ? 1 : 0);
        assertThat(escucha.cancelados.get()).isEqualTo(tipo.equals("CANCELACION") ? 1 : 0);
        assertThat(jdbc.queryForMap("SELECT tipo, turno_id, estado FROM notificacion"))
                .containsEntry("tipo", tipo).containsEntry("turno_id", turno).containsEntry("estado", "ENVIADO");
    }

    private void drenarNotificaciones() {
        var barrera = new CountDownLatch(2);
        Runnable fin = () -> {
            barrera.countDown();
            esperarDrenaje(barrera);
        };
        ejecutorNotificaciones.execute(fin);
        ejecutorNotificaciones.execute(fin);
        esperarDrenaje(barrera);
    }

    private static void esperarDrenaje(CountDownLatch barrera) {
        try {
            assertThat(barrera.await(8, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }

    private void unActivoEnDestino() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno WHERE inicio=? AND estado='RESERVADO'",
                Long.class, Timestamp.from(DESTINO))).isEqualTo(1L);
    }

    private static void error(HttpResponse<String> res, int codigoHttp, String codigo) {
        assertThat(res.statusCode()).isEqualTo(codigoHttp);
        assertThat((String) JsonPath.read(res.body(), "$.codigo")).isEqualTo(codigo);
        if (codigoHttp == 409) {
            assertThat((java.util.Map<String, ?>) JsonPath.read(res.body(), "$")).containsOnlyKeys("codigo", "mensaje");
        }
        headersDeGestion(res);
    }

    private static void vacia(HttpResponse<String> res, int estado) {
        assertThat(res.statusCode()).isEqualTo(estado);
        assertThat(res.body()).isEmpty();
        assertThat(res.headers().firstValue("Location")).isEmpty();
        headersDeGestion(res);
    }

    private static void headersDeGestion(HttpResponse<String> res) {
        assertThat(res.headers().firstValue("Cache-Control")).contains("no-store");
        assertThat(res.headers().firstValue("Referrer-Policy")).contains("no-referrer");
    }

    private static void unGanador(List<HttpResponse<String>> resultados, String codigoPerdedor, int... exitos) {
        var conflicto = resultados.stream().filter(res -> res.statusCode() == 409).toList();
        assertThat(conflicto).as("estados concurrentes: %s",
                resultados.stream().map(HttpResponse::statusCode).toList()).hasSize(1);
        var exito = resultados.stream().filter(res -> java.util.Arrays.stream(exitos)
                .anyMatch(estado -> estado == res.statusCode())).toList();
        assertThat(exito).hasSize(1);
        if (exito.getFirst().statusCode() == 204) {
            vacia(exito.getFirst(), 204);
        }
        // La respuesta 409 de reserva C-07 no necesita headers de C-09.
        assertThat((String) JsonPath.read(conflicto.getFirst().body(), "$.codigo")).isEqualTo(codigoPerdedor);
    }

    private HttpResponse<String> reservar(String dni) throws Exception {
        return reservar(dni, "reserva@example.test");
    }

    private HttpResponse<String> reservar(String dni, String email) throws Exception {
        return post("/api/public/demo/turnos", "application/json", """
                {"prestacionId":%d,"inicio":"%s","dni":"%s","email":"%s",
                 "nombre":"Ana","apellido":"Prueba","telefono":"+5493511234567"}"""
                .formatted(prestacion, DESTINO, dni, email));
    }

    private HttpResponse<String> mover(String token, Instant destino) throws Exception {
        return moverTexto(token, destino.toString());
    }

    private HttpResponse<String> moverTexto(String token, String inicio) throws Exception {
        return post(API + token + "/reprogramar", "application/json", "{\"inicio\":\"" + inicio + "\"}");
    }

    private HttpResponse<String> get(String ruta) throws Exception {
        return enviar(HttpRequest.newBuilder(uri(ruta)).GET());
    }

    private HttpResponse<String> post(String ruta, String mime, String body) throws Exception {
        var builder = HttpRequest.newBuilder(uri(ruta));
        if (mime != null) { builder.header("Content-Type", mime); }
        return enviar(builder.POST(body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)));
    }

    private URI uri(String ruta) {
        return URI.create("http://localhost:" + puerto + ruta);
    }

    private HttpResponse<String> enviar(HttpRequest.Builder solicitud) throws Exception {
        try (var cliente = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            return cliente.send(solicitud.timeout(Duration.ofSeconds(12)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private static List<HttpResponse<String>> paralelas(Callable<HttpResponse<String>> a,
            Callable<HttpResponse<String>> b) throws Exception {
        var largada = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var una = pool.submit(() -> { largada.await(5, TimeUnit.SECONDS); return a.call(); });
            var otra = pool.submit(() -> { largada.await(5, TimeUnit.SECONDS); return b.call(); });
            return List.of(una.get(15, TimeUnit.SECONDS), otra.get(15, TimeUnit.SECONDS));
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Captura {
        @Bean Escucha escucha() { return new Escucha(); }

        @Bean WebMvcConfigurer observarExcepcion(Escucha escucha) {
            return new WebMvcConfigurer() {
                @Override
                public void extendHandlerExceptionResolvers(List<HandlerExceptionResolver> resolvers) {
                    resolvers.addFirst((req, res, handler, ex) -> {
                        for (Throwable causa = ex; causa != null; causa = causa.getCause()) {
                            if (causa instanceof SQLException sql) {
                                escucha.errorSql.set(sql);
                                break;
                            }
                        }
                        return null; // El advice real sigue encargado de responder.
                    });
                }
            };
        }
    }

    static class Escucha {
        private final AtomicInteger confirmados = new AtomicInteger();
        private final AtomicInteger reservados = new AtomicInteger();
        private final AtomicInteger cancelados = new AtomicInteger();
        private final AtomicReference<SQLException> errorSql = new AtomicReference<>();

        @TransactionalEventListener
        void confirmado(TurnoReprogramado evento) { confirmados.incrementAndGet(); }

        @TransactionalEventListener
        void reservado(TurnoReservado evento) { reservados.incrementAndGet(); }

        @TransactionalEventListener
        void cancelado(TurnoCancelado evento) { cancelados.incrementAndGet(); }
    }
}
