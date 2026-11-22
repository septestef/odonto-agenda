package ar.turnotarde.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnotarde.soporte.ConfiguracionRelojDePrueba;
import ar.turnotarde.soporte.CuentaDePrueba;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import ar.turnotarde.soporte.RelojDePrueba;
import ar.turnotarde.turno.CancelacionOdontologoService;
import ar.turnotarde.turno.CanceladoPor;
import ar.turnotarde.turno.TokenTurno;
import ar.turnotarde.turno.TurnoRepository;
import ar.turnotarde.turno.eventos.ResumenTurno;
import ar.turnotarde.turno.eventos.TurnoCancelado;
import ar.turnotarde.turno.eventos.TurnoReprogramado;
import java.sql.Timestamp;
import java.sql.Connection;
import java.time.Instant;
import java.time.Duration;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.event.EventListener;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import javax.sql.DataSource;
import ar.turnotarde.shared.tiempo.ZonaNegocio;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
@Import({ConfiguracionRelojDePrueba.class, CancelacionOdontologoIT.Captura.class})
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {"MAIL_HOST=smtp.example.test", "MAIL_PORT=587",
        "MAIL_USERNAME=origen@example.test", "MAIL_PASSWORD=prueba-sintetica", "MAIL_FROM_NAME=Prueba"})
class CancelacionOdontologoIT extends IntegracionTest {
    static final Instant AHORA = Instant.parse("2026-11-25T12:00:00Z");
    static final Instant INICIO = Instant.parse("2026-11-27T12:00:00Z");
    static final String TOKEN = "H".repeat(42) + "A";
    static final String EMAIL = "gestion-privada@example.test";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired RelojDePrueba reloj;
    @Autowired SmtpDePrueba smtp;
    @Autowired Observacion observacion;
    @Autowired CancelacionOdontologoService cancelacion;
    @Autowired PlatformTransactionManager gestor;
    @Autowired DataSource dataSource;
    @Autowired @Qualifier("ejecutorNotificaciones") Executor ejecutor;
    @MockitoSpyBean TurnoRepository turnos;
    @MockitoSpyBean ZonaNegocio zona;
    final ObjectMapper json = new ObjectMapper();
    CuentaDePrueba.Cuenta cuenta;
    FixturesJdbc fx;
    long paciente;
    long prestacion;

    @BeforeEach
    void preparar() throws Exception {
        drenar();
        LimpiezaBase.limpiar(jdbc);
        reloj.fijar(AHORA);
        smtp.limpiar();
        observacion.eventos.clear();
        observacion.confirmados.set(0);
        observacion.reprogramados.set(0);
        observacion.reprogramadosConfirmados.set(0);
        observacion.accion = () -> {};
        fx = new FixturesJdbc(jdbc);
        cuenta = CuentaDePrueba.crear(mvc, jdbc, "cancelar");
        paciente = fx.paciente(cuenta.id(), "12345678");
        prestacion = fx.prestacion(cuenta.id(), "Consulta", 30);
        for (int dia = 1; dia <= 7; dia++) { fx.horario(cuenta.id(), dia, "09:00", "13:00"); }
    }

    @AfterEach
    void limpiar() {
        observacion.accion = () -> {};
        drenar();
        reloj.fijar(AHORA);
        LimpiezaBase.limpiar(jdbc);
    }

    long turno(String estado, String email) {
        long id = fx.turno(cuenta.id(), paciente, prestacion, INICIO, INICIO.plusSeconds(1800), estado,
                TokenTurno.hashear(TOKEN));
        jdbc.update("UPDATE turno SET email_gestion=? WHERE id=?", email, id);
        return id;
    }

    MockHttpServletRequestBuilder solicitud(Object id) {
        return post("/api/turnos/{id}/cancelar", id).session(cuenta.sesion().sesion())
                .cookie(cuenta.sesion().csrfCookie()).header("X-XSRF-TOKEN", cuenta.sesion().csrf());
    }

    MvcResult cancelar(Object id, int estado) throws Exception {
        var resultado = mvc.perform(solicitud(id)).andExpect(status().is(estado)).andReturn();
        assertThat(resultado.getResponse().getHeader("Location")).isNull();
        return resultado;
    }

    void cuerpo(MvcResult resultado, String esperado) throws Exception {
        assertThat(resultado.getResponse().getContentType()).startsWith("application/json");
        assertThat(json.readTree(resultado.getResponse().getContentAsString())).isEqualTo(json.readTree(esperado));
    }

    void sinAviso() {
        drenar();
        assertThat(smtp.intentos()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion", Long.class)).isZero();
    }

    @Test
    void reservadoCercanoCancelaConSnapshotVigenteFlushAntesDeEventoYRepiteSinCambios() throws Exception {
        long id = turno("RESERVADO", EMAIL);
        reloj.fijar(INICIO.minusSeconds(1));
        jdbc.update("UPDATE paciente SET nombre='Ana', apellido='Pérez', email='mutable@example.test' WHERE id=?", paciente);
        jdbc.update("UPDATE prestacion SET nombre='Vigente', duracion_minutos=45, activa=false WHERE id=?", prestacion);
        observacion.accion = () -> assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE id=?",
                String.class, id)).isEqualTo("CANCELADO");
        assertThat(cancelar(id, 204).getResponse().getContentAsString()).isEmpty();
        drenar();
        assertThat(jdbc.queryForMap("SELECT estado,cancelado_por,cancelado_en FROM turno WHERE id=?", id))
                .containsEntry("estado", "CANCELADO").containsEntry("cancelado_por", "ODONTOLOGO")
                .containsEntry("cancelado_en", Timestamp.from(INICIO.minusSeconds(1)));
        assertThat(observacion.eventos).hasSize(1);
        var evento = observacion.eventos.getFirst();
        assertThat(evento).isEqualTo(new TurnoCancelado(new ResumenTurno(id, cuenta.id(), INICIO,
                INICIO.plusSeconds(1800), prestacion, "Vigente", 30, "Ana", "Pérez", EMAIL,
                "+5493510000000", "Nombre", "Apellido", null), CanceladoPor.ODONTOLOGO, INICIO.minusSeconds(1)));
        assertThat(observacion.confirmados).hasValue(1);
        assertThat(smtp.intentos()).isEqualTo(1);
        var antes = jdbc.queryForMap("SELECT * FROM turno WHERE id=?", id);
        reloj.fijar(INICIO.plusSeconds(1));
        assertThat(cancelar(id, 204).getResponse().getContentAsString()).isEmpty();
        drenar();
        assertThat(jdbc.queryForMap("SELECT * FROM turno WHERE id=?", id)).isEqualTo(antes);
        assertThat(observacion.eventos).hasSize(1);
        assertThat(smtp.intentos()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({"RESERVADO,0", "RESERVADO,1", "ATENDIDO,0", "ATENDIDO,1", "AUSENTE,0", "AUSENTE,1"})
    void tiempoRechazaSinMutarNiPublicar(String estado, int segundos) throws Exception {
        long id = turno(estado, EMAIL);
        var antes = jdbc.queryForMap("SELECT * FROM turno WHERE id=?", id);
        reloj.fijar(INICIO.plusSeconds(segundos));
        cuerpo(cancelar(id, 409), """
                {"codigo":"RN-TU-07","mensaje":"No podés cancelar un turno que ya empezó."}
                """);
        assertThat(jdbc.queryForMap("SELECT * FROM turno WHERE id=?", id)).isEqualTo(antes);
        verify(turnos, never()).flush();
        assertThat(observacion.eventos).isEmpty();
        sinAviso();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ATENDIDO", "AUSENTE"})
    void inconsistenciaFuturaNoActualizaNiPublicaYLogCompletoEsSanitizado(String estado, CapturedOutput salida)
            throws Exception {
        long id = turno(estado, EMAIL);
        jdbc.update("UPDATE paciente SET nombre='PII_NOMBRE_C12', apellido='PII_APELLIDO_C12' WHERE id=?", paciente);
        var antes = jdbc.queryForMap("SELECT * FROM turno WHERE id=?", id);
        cuerpo(cancelar(id, 500), """
                {"codigo":"ERROR_INTERNO","mensaje":"Ocurrió un error inesperado. Probá de nuevo en unos minutos."}
                """);
        assertThat(jdbc.queryForMap("SELECT * FROM turno WHERE id=?", id)).isEqualTo(antes);
        verify(turnos, never()).flush();
        assertThat(observacion.eventos).isEmpty();
        sinAviso();
        assertThat(salida.getAll()).contains("CANCELACION_ODONTOLOGO_ESTADO_INCONSISTENTE estado=" + estado,
                "IllegalStateException: Estado de turno inconsistente para cancelación.")
                .doesNotContain(EMAIL, TOKEN, TokenTurno.hashear(TOKEN), "p12345678@example.com", "+5493510000000",
                        "PII_NOMBRE_C12", "PII_APELLIDO_C12", cuenta.email());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PACIENTE", "ODONTOLOGO"})
    void canceladoPasadoConservaAutorOriginalYTodosLosMetadatos(String autor) throws Exception {
        long id = turno("CANCELADO", EMAIL);
        jdbc.update("UPDATE turno SET cancelado_por=?, cancelado_en=? WHERE id=?", autor, Timestamp.from(AHORA), id);
        reloj.fijar(INICIO.plusSeconds(10));
        var antes = jdbc.queryForMap("SELECT * FROM turno WHERE id=?", id);
        assertThat(cancelar(id, 204).getResponse().getContentAsString()).isEmpty();
        assertThat(jdbc.queryForMap("SELECT * FROM turno WHERE id=?", id)).isEqualTo(antes);
        verify(turnos, never()).flush();
        assertThat(observacion.eventos).isEmpty();
        sinAviso();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void identificadorNoPositivoTieneDetalleExacto(String id) throws Exception {
        cuerpo(cancelar(id, 400), """
                {"codigo":"VALIDACION","mensaje":"Hay datos inválidos. Revisalos y probá de nuevo.",
                 "detalles":[{"campo":"id","mensaje":"Ingresá un identificador mayor a 0."}]}
                """);
        sinAviso();
    }

    @ParameterizedTest
    @ValueSource(strings = {"no-entero", "9223372036854775808", "-9223372036854775809"})
    void identificadorNoConvertibleNoEcoaEntrada(String id) throws Exception {
        cuerpo(cancelar(id, 400), """
                {"codigo":"SOLICITUD_INVALIDA","mensaje":"No pudimos interpretar la solicitud."}
                """);
        sinAviso();
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESERVADO", "ATENDIDO", "AUSENTE", "CANCELADO"})
    void ajenoInexistenteIdenticosAunquePasadoYConDuenoInyectado(String estado) throws Exception {
        long id = turno(estado, EMAIL);
        var antes = jdbc.queryForMap("SELECT * FROM turno WHERE id=?", id);
        var otra = CuentaDePrueba.crear(mvc, jdbc, "otra");
        reloj.fijar(INICIO.plusSeconds(1));
        for (long ruta : new long[] {id, Long.MAX_VALUE}) {
            var resultado = mvc.perform(post("/api/turnos/{id}/cancelar?odontologoId=" + cuenta.id(), ruta)
                    .session(otra.sesion().sesion()).cookie(otra.sesion().csrfCookie())
                    .header("X-XSRF-TOKEN", otra.sesion().csrf()).header("odontologoId", cuenta.id())
                    .contentType("application/json").content("{\"odontologoId\":" + cuenta.id() + "}"))
                    .andExpect(status().isNotFound()).andReturn();
            cuerpo(resultado, """
                    {"codigo":"NO_ENCONTRADO","mensaje":"No encontramos lo que buscabas."}
                    """);
        }
        assertThat(jdbc.queryForMap("SELECT * FROM turno WHERE id=?", id)).isEqualTo(antes);
        assertThat(observacion.eventos).isEmpty();
        sinAviso();
    }

    @Test
    void csrfSesionYRolRealesPrecedenAlEstadoYNoMutan() throws Exception {
        long id = turno("RESERVADO", EMAIL);
        var antes = jdbc.queryForMap("SELECT * FROM turno WHERE id=?", id);
        String denegado = """
                {"codigo":"ACCESO_DENEGADO","mensaje":"No pudimos verificar la solicitud. Recargá la página e intentá de nuevo."}
                """;
        for (var pedido : List.of(
                post("/api/turnos/{id}/cancelar", id),
                post("/api/turnos/{id}/cancelar", id).session(cuenta.sesion().sesion()),
                post("/api/turnos/{id}/cancelar", id).session(cuenta.sesion().sesion())
                        .cookie(cuenta.sesion().csrfCookie()).header("X-XSRF-TOKEN", "invalido"))) {
            var resultado = mvc.perform(pedido).andExpect(status().isForbidden()).andReturn();
            cuerpo(resultado, denegado);
            assertThat(resultado.getResponse().getHeader("Location")).isNull();
        }
        cuerpo(mvc.perform(post("/api/turnos/{id}/cancelar", id).cookie(cuenta.sesion().csrfCookie())
                .header("X-XSRF-TOKEN", cuenta.sesion().csrf())).andExpect(status().isUnauthorized()).andReturn(), """
                {"codigo":"NO_AUTENTICADO","mensaje":"Tu sesión no está iniciada o expiró. Volvé a ingresar."}
                """);
        var sesion = cuenta.sesion().sesion();
        var contexto = (SecurityContext) sesion.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        var sinRol = SecurityContextHolder.createEmptyContext();
        sinRol.setAuthentication(new UsernamePasswordAuthenticationToken(contexto.getAuthentication().getPrincipal(),
                null, List.of(new SimpleGrantedAuthority("ROLE_OTRO"))));
        sesion.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, sinRol);
        cuerpo(cancelar(id, 403), denegado);
        sesion.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto);
        assertThat(jdbc.queryForMap("SELECT * FROM turno WHERE id=?", id)).isEqualTo(antes);
        assertThat(observacion.eventos).isEmpty();
        sinAviso();
        // El body no define contrato ni requiere Content-Type: incluso valores ajenos no cambian el dueño.
        var otra = CuentaDePrueba.crear(mvc, jdbc, "otra");
        assertThat(mvc.perform(solicitud(id).queryParam("odontologoId", "" + otra.id())
                .header("X-Odontologo-Id", otra.id()).content("{\"odontologoId\":" + otra.id() + "}"))
                .andExpect(status().isNoContent()).andReturn().getResponse().getContentAsString()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"NULL", "", "uno@example.test,dos@example.test", "ana@ejemplo_com.test"})
    void snapshotAusenteOInadmisibleConfirmaSinFallbackSmtpNiAuditoria(String entrada) throws Exception {
        long id = turno("RESERVADO", entrada.equals("NULL") ? null : entrada);
        jdbc.update("UPDATE paciente SET email='mutable-valido@example.test' WHERE id=?", paciente);
        cancelar(id, 204);
        assertThat(observacion.eventos).hasSize(1);
        assertThat(observacion.eventos.getFirst().turno().pacienteEmail()).isNull();
        assertThat(observacion.confirmados).hasValue(1);
        assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE id=?", String.class, id)).isEqualTo("CANCELADO");
        sinAviso();
    }

    @Test
    void commitVisibleYHttp204AntesDeSmtpSinCambiarDestinatarioTrasPublicar() throws Exception {
        long id = turno("RESERVADO", EMAIL);
        var llegado = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        smtp.accion(() -> {
            assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE id=?", String.class, id)).isEqualTo("CANCELADO");
            llegado.countDown();
            esperar(liberar);
        });
        try (var pool = Executors.newSingleThreadExecutor()) {
            try {
                var respuesta = pool.submit(() -> cancelar(id, 204));
                esperar(llegado);
                assertThat(respuesta.get(5, TimeUnit.SECONDS).getResponse().getContentAsString()).isEmpty();
                jdbc.update("UPDATE paciente SET email='despues@example.test' WHERE id=?", paciente);
                assertThat(smtp.ultimo().getAllRecipients()[0].toString()).isEqualTo(EMAIL);
                assertThat(smtp.ultimo().getSubject()).isEqualTo("Cancelamos tu turno");
                assertThat(smtp.ultimo().getContent().toString()).contains("27/11/2026", "09:00", "Consulta")
                        .doesNotContain("href=", TOKEN, TokenTurno.hashear(TOKEN), "despues@example.test");
            } finally { liberar.countDown(); }
        }
        drenar();
        assertThat(smtp.intentos()).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT tipo,estado,destinatario FROM notificacion"))
                .containsEntry("tipo", "CANCELACION").containsEntry("estado", "ENVIADO").containsEntry("destinatario", EMAIL);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void falloSmtpInclusoAceptacionInciertaNoRevierteNiReintenta(boolean ambiguo, CapturedOutput salida) throws Exception {
        long id = turno("RESERVADO", EMAIL);
        smtp.accion(() -> {
            reloj.fijar(AHORA.plusSeconds(5));
            throw new org.springframework.mail.MailSendException("secreto-smtp-sintetico " + EMAIL,
                    ambiguo ? new java.net.SocketTimeoutException("timeout tras DATA") : new IllegalStateException("rechazo"));
        });
        cancelar(id, 204);
        drenar();
        assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE id=?", String.class, id)).isEqualTo("CANCELADO");
        assertThat(jdbc.queryForMap("SELECT tipo,estado,error,enviado_en FROM notificacion"))
                .containsEntry("tipo", "CANCELACION").containsEntry("estado", "ERROR")
                // C-08 captura el instante al admitir el aviso, antes del intento SMTP.
                .containsEntry("error", "SMTP_FALLO").containsEntry("enviado_en", Timestamp.from(AHORA));
        assertThat(smtp.intentos()).isEqualTo(1);
        assertThat(salida.getAll()).doesNotContain("secreto-smtp-sintetico", EMAIL, "MailSendException");
    }

    @Test
    void rollbackRealPosteriorAPublishRestauraTurnoYNoEntregaAfterCommit() {
        long id = turno("RESERVADO", EMAIL);
        var antes = jdbc.queryForMap("SELECT * FROM turno WHERE id=?", id);
        var contexto = (SecurityContext) cuenta.sesion().sesion()
                .getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        SecurityContextHolder.setContext(contexto);
        try {
            new TransactionTemplate(gestor).executeWithoutResult(tx -> {
                cancelacion.cancelar(id); // Bean real proxificado, participa en la tx exterior.
                assertThat(observacion.eventos).hasSize(1);
                assertThat(observacion.confirmados).hasValue(0);
                assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE id=?", String.class, id)).isEqualTo("CANCELADO");
                tx.setRollbackOnly();
            });
        } finally { SecurityContextHolder.clearContext(); }
        assertThat(jdbc.queryForMap("SELECT * FROM turno WHERE id=?", id)).isEqualTo(antes);
        assertThat(observacion.confirmados).hasValue(0);
        sinAviso();
        assertThatThrownBy(() -> fx.turno(cuenta.id(), paciente, prestacion, INICIO, INICIO.plusSeconds(1800), "RESERVADO"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @CsvSource({"40P01,409", "55P03,409", "23P01,409", "23505,500"})
    void guardiaTraduceFalloRealDiferidoAlCommitSinConfirmarNiAvisar(String sqlState, int estado) throws Exception {
        long id = turno("RESERVADO", EMAIL);
        var antes = jdbc.queryForMap("SELECT * FROM turno WHERE id=?", id);
        jdbc.execute("""
                CREATE FUNCTION prueba_c12_commit() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN RAISE EXCEPTION USING ERRCODE='%s', MESSAGE='turno_sin_solapamiento'; END; $$
                """.formatted(sqlState));
        try {
            jdbc.execute("""
                    CREATE CONSTRAINT TRIGGER prueba_c12_commit AFTER UPDATE ON turno
                    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW WHEN (NEW.id=%d)
                    EXECUTE FUNCTION prueba_c12_commit()
                    """.formatted(id));
            var resultado = cancelar(id, estado);
            cuerpo(resultado, estado == 409 ? """
                    {"codigo":"RN-TU-01","mensaje":"Ese horario acaba de ser tomado, elegí otro"}
                    """ : """
                    {"codigo":"ERROR_INTERNO","mensaje":"Ocurrió un error inesperado. Probá de nuevo en unos minutos."}
                    """);
            assertThat(observacion.eventos).hasSize(1); // llegó al publish, falló SOLO al cerrar tx
            assertThat(observacion.confirmados).hasValue(0);
            assertThat(jdbc.queryForMap("SELECT * FROM turno WHERE id=?", id)).isEqualTo(antes);
            sinAviso();
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS prueba_c12_commit ON turno");
            jdbc.execute("DROP FUNCTION prueba_c12_commit()");
        }
    }

    @Test
    void guardiaTambienTraduceFalloPesimistaSinPublicar() throws Exception {
        long id = turno("RESERVADO", EMAIL);
        doThrow(new CannotAcquireLockException("fallo sintetico fijo")).when(turnos).bloquearPropio(anyLong(), anyLong());
        cuerpo(cancelar(id, 409), """
                {"codigo":"RN-TU-01","mensaje":"Ese horario acaba de ser tomado, elegí otro"}
                """);
        assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE id=?", String.class, id)).isEqualTo("RESERVADO");
        assertThat(observacion.eventos).isEmpty();
        sinAviso();
    }

    @Test
    void contactoEditadoDespuesDePublishAntesDeProcesarConservaSnapshot() throws Exception {
        long id = turno("RESERVADO", EMAIL);
        var ocupados = new CountDownLatch(2);
        var liberar = new CountDownLatch(1);
        Runnable bloquear = () -> { ocupados.countDown(); esperar(liberar); };
        ejecutor.execute(bloquear);
        ejecutor.execute(bloquear);
        try {
            esperar(ocupados);
            cancelar(id, 204);
            assertThat(observacion.confirmados).hasValue(1);
            assertThat(smtp.intentos()).isZero();
            jdbc.update("UPDATE paciente SET email='editado-despues-publish@example.test' WHERE id=?", paciente);
        } finally { liberar.countDown(); }
        drenar();
        assertThat(smtp.ultimo().getAllRecipients()[0].toString()).isEqualTo(EMAIL);
        assertThat(smtp.intentos()).isEqualTo(1);
    }

    @Test
    void cancelaSucesorC09ConDestinatarioHeredadoAunqueContactoCambie() throws Exception {
        long original = turno("RESERVADO", EMAIL);
        publico(true, 204);
        drenar();
        long sucesor = jdbc.queryForObject("SELECT id FROM turno WHERE reprogramado_desde_id=?", Long.class, original);
        assertThat(jdbc.queryForObject("SELECT email_gestion FROM turno WHERE id=?", String.class, sucesor)).isEqualTo(EMAIL);
        jdbc.update("UPDATE paciente SET email='contacto-posterior@example.test' WHERE id=?", paciente);
        cancelar(sucesor, 204);
        drenar();
        assertThat(observacion.eventos).hasSize(1);
        assertThat(observacion.eventos.getFirst().turno().turnoId()).isEqualTo(sucesor);
        assertThat(observacion.eventos.getFirst().turno().pacienteEmail()).isEqualTo(EMAIL);
        assertThat(smtp.ultimo().getAllRecipients()[0].toString()).isEqualTo(EMAIL);
        assertThat(jdbc.queryForObject("SELECT destinatario FROM notificacion WHERE tipo='CANCELACION'", String.class))
                .isEqualTo(EMAIL);
    }

    int pid(Connection conexion) throws Exception {
        try (var sql = conexion.createStatement(); var fila = sql.executeQuery("SELECT pg_backend_pid()")) {
            assertThat(fila.next()).isTrue();
            return fila.getInt(1);
        }
    }

    void bloquear(Connection conexion, long id) throws Exception {
        conexion.setAutoCommit(false);
        try (var sql = conexion.prepareStatement("SELECT id FROM turno WHERE id=? FOR UPDATE")) {
            sql.setLong(1, id);
            try (var fila = sql.executeQuery()) { assertThat(fila.next()).isTrue(); }
        }
    }

    void esperarBloqueadoPor(int pid) {
        await().atMost(Duration.ofSeconds(5)).until(() -> jdbc.queryForObject("""
                SELECT EXISTS (SELECT FROM pg_stat_activity WHERE datname=current_database()
                    AND ? = ANY(pg_blocking_pids(pid)))
                """, Boolean.class, pid));
    }

    @Test
    void dosPostConContencionReal204204UnaTransicionSinLocksDeAsociaciones() throws Exception {
        long id = turno("RESERVADO", EMAIL);
        var publicado = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var pid = new AtomicInteger();
        observacion.accion = () -> {
            pid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
            publicado.countDown();
            esperar(liberar);
        };
        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var primera = pool.submit(() -> cancelar(id, 204));
                esperar(publicado);
                var segunda = pool.submit(() -> cancelar(id, 204));
                esperarBloqueadoPor(pid.get());
                String sql = jdbc.queryForObject("""
                        SELECT query FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid))
                        AND datname=current_database()
                        """, String.class, pid.get());
                assertThat(sql.toLowerCase(java.util.Locale.ROOT)).contains("from turno", "odontologo_id", "for update")
                        .doesNotContain("join", "paciente ", "prestacion ");
                // Otra conexión puede bloquear asociaciones y advisory mientras C-12 retiene SOLO el turno.
                try (var c = dataSource.getConnection()) {
                    c.setAutoCommit(false);
                    for (var tablaId : Map.of("paciente", paciente, "prestacion", prestacion, "odontologo", cuenta.id()).entrySet()) {
                        try (var sentencia = c.createStatement(); var fila = sentencia.executeQuery(
                                "SELECT id FROM " + tablaId.getKey() + " WHERE id=" + tablaId.getValue() + " FOR UPDATE NOWAIT")) {
                            assertThat(fila.next()).isTrue();
                        }
                    }
                    try (var sentencia = c.prepareStatement("""
                            SELECT pg_try_advisory_xact_lock(hashtext('turno_sin_solapamiento'), hashtext(CAST(? AS text)))
                            """)) {
                        sentencia.setLong(1, cuenta.id());
                        try (var fila = sentencia.executeQuery()) { fila.next(); assertThat(fila.getBoolean(1)).isTrue(); }
                    }
                    liberar.countDown();
                    assertThat(primera.get(5, TimeUnit.SECONDS).getResponse().getContentAsString()).isEmpty();
                    assertThat(segunda.get(5, TimeUnit.SECONDS).getResponse().getContentAsString()).isEmpty();
                    c.rollback();
                }
            } finally { liberar.countDown(); }
        }
        drenar();
        assertThat(observacion.eventos).hasSize(1);
        assertThat(observacion.confirmados).hasValue(1);
        assertThat(smtp.intentos()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion", Long.class)).isEqualTo(1);
        var antes = jdbc.queryForMap("SELECT * FROM turno WHERE id=?", id);
        cancelar(id, 204);
        assertThat(jdbc.queryForMap("SELECT * FROM turno WHERE id=?", id)).isEqualTo(antes);
        assertThat(observacion.eventos).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void relojSeCapturaUnaVezSoloDespuesDeLaEspera(boolean iniciado) throws Exception {
        long id = turno("RESERVADO", EMAIL);
        try (var pool = Executors.newSingleThreadExecutor(); var c = dataSource.getConnection()) {
            bloquear(c, id);
            clearInvocations(zona);
            var respuesta = pool.submit(() -> cancelar(id, iniciado ? 409 : 204));
            try {
                esperarBloqueadoPor(pid(c));
                verify(zona, never()).ahora();
                reloj.fijar(iniciado ? INICIO : INICIO.minusSeconds(1));
            } finally { c.rollback(); }
            var resultado = respuesta.get(5, TimeUnit.SECONDS);
            verify(zona, times(1)).ahora();
            if (iniciado) {
                cuerpo(resultado, """
                        {"codigo":"RN-TU-07","mensaje":"No podés cancelar un turno que ya empezó."}
                        """);
                assertThat(observacion.eventos).isEmpty();
                sinAviso();
            } else {
                assertThat(jdbc.queryForObject("SELECT cancelado_en FROM turno WHERE id=?", Timestamp.class, id))
                        .isEqualTo(Timestamp.from(INICIO.minusSeconds(1)));
                assertThat(observacion.eventos.getFirst().canceladoEn()).isEqualTo(INICIO.minusSeconds(1));
            }
        }
    }

    MvcResult publico(boolean reprogramar, int esperado) throws Exception {
        var pedido = post("/api/public/turnos/{token}/" + (reprogramar ? "reprogramar" : "cancelar"), TOKEN);
        if (reprogramar) {
            pedido.contentType("application/json").content("{\"inicio\":\"2026-11-27T13:00:00Z\"}");
        }
        return mvc.perform(pedido).andExpect(status().is(esperado)).andReturn();
    }

    @ParameterizedTest
    @CsvSource({"false,false,false", "true,false,false", "false,true,false", "true,true,false",
            "false,true,true", "true,true,true"})
    void carrerasC09RespetanGanadorConLocksObservadosSinInversion(boolean reprogramar, boolean ganaPrivado,
            boolean inicioDuranteEspera) throws Exception {
        long id = turno("RESERVADO", EMAIL);
        var publicado = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        var pid = new AtomicInteger();
        var snapshot = new AtomicReference<Map<String, Object>>();
        observacion.accion = () -> {
            pid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
            snapshot.set(jdbc.queryForMap("SELECT * FROM turno WHERE id=?", id));
            publicado.countDown();
            esperar(liberar);
        };
        try (var pool = Executors.newFixedThreadPool(2)) {
            try {
                var ganador = pool.submit(() -> ganaPrivado ? cancelar(id, 204) : publico(reprogramar, 204));
                esperar(publicado);
                var perdedor = pool.submit(() -> ganaPrivado ? publico(reprogramar, 409) : cancelar(id, 204));
                esperarBloqueadoPor(pid.get());
                if (ganaPrivado && reprogramar) {
                    assertThat(jdbc.queryForObject("""
                            SELECT pg_try_advisory_xact_lock(hashtext('turno_sin_solapamiento'), hashtext(CAST(? AS text)))
                            """, Boolean.class, cuenta.id())).as("C-09 ya tiene advisory antes de esperar turno").isFalse();
                }
                if (inicioDuranteEspera) { reloj.fijar(INICIO); }
                liberar.countDown();
                ganador.get(5, TimeUnit.SECONDS);
                var resultado = perdedor.get(5, TimeUnit.SECONDS);
                if (ganaPrivado) {
                    assertThat(json.readTree(resultado.getResponse().getContentAsString()).get("codigo").asText())
                            .isEqualTo(inicioDuranteEspera ? "RN-TU-07" : "RN-SE-02");
                }
            } finally { liberar.countDown(); }
        }
        drenar();
        assertThat(jdbc.queryForMap("SELECT * FROM turno WHERE id=?", id)).isEqualTo(snapshot.get());
        assertThat(observacion.eventos.size() + observacion.reprogramados.get()).isEqualTo(1);
        assertThat(observacion.confirmados.get() + observacion.reprogramadosConfirmados.get()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno WHERE reprogramado_desde_id=?", Long.class, id))
                .isEqualTo(!ganaPrivado && reprogramar ? 1 : 0);
        assertThat(smtp.intentos()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacion", Long.class)).isEqualTo(1);
        var antes = jdbc.queryForMap("SELECT * FROM turno WHERE id=?", id);
        cancelar(id, 204);
        assertThat(jdbc.queryForMap("SELECT * FROM turno WHERE id=?", id)).isEqualTo(antes);
    }

    MvcResult reservar(int esperado) throws Exception {
        return mvc.perform(post("/api/public/{slug}/turnos", cuenta.slug()).contentType("application/json").content("""
                {"prestacionId":%d,"inicio":"2026-11-27T12:00:00Z","dni":"87654321","nombre":"Nueva",
                 "apellido":"Reserva","telefono":"+5493510000001","email":"nueva@example.test"}
                """.formatted(prestacion))).andExpect(status().is(esperado)).andReturn();
    }

    @Test
    void horarioSigueOcupadoAntesDeCommitYSePuedeReservarTrasConfirmar() throws Exception {
        long id = turno("RESERVADO", EMAIL);
        var publicado = new CountDownLatch(1);
        var liberar = new CountDownLatch(1);
        observacion.accion = () -> { publicado.countDown(); esperar(liberar); };
        try (var pool = Executors.newSingleThreadExecutor()) {
            try {
                var cancelada = pool.submit(() -> cancelar(id, 204));
                esperar(publicado);
                assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE id=?", String.class, id)).isEqualTo("RESERVADO");
                assertThat(json.readTree(reservar(409).getResponse().getContentAsString()).get("codigo").asText())
                        .isEqualTo("RN-TU-01");
                liberar.countDown();
                cancelada.get(5, TimeUnit.SECONDS);
            } finally { liberar.countDown(); }
        }
        reservar(201);
        assertThat(jdbc.queryForObject("SELECT estado FROM turno WHERE id=?", String.class, id)).isEqualTo("CANCELADO");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM turno WHERE estado='RESERVADO'", Long.class)).isEqualTo(1);
    }

    void drenar() {
        var fin = new CountDownLatch(2);
        Runnable tarea = () -> { fin.countDown(); esperar(fin); };
        ejecutor.execute(tarea);
        ejecutor.execute(tarea);
        esperar(fin);
    }

    static void esperar(CountDownLatch latch) {
        try { assertThat(latch.await(8, TimeUnit.SECONDS)).as("latch acotado").isTrue(); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Captura {
        @Bean Observacion observacion() { return new Observacion(); }
    }

    static class Observacion {
        final List<TurnoCancelado> eventos = new CopyOnWriteArrayList<>();
        final AtomicInteger confirmados = new AtomicInteger();
        final AtomicInteger reprogramados = new AtomicInteger();
        final AtomicInteger reprogramadosConfirmados = new AtomicInteger();
        volatile Runnable accion = () -> {};
        @EventListener void publicado(TurnoCancelado e) { eventos.add(e); accion.run(); }
        @TransactionalEventListener void confirmado(TurnoCancelado e) { confirmados.incrementAndGet(); }
        @EventListener void publicado(TurnoReprogramado e) { reprogramados.incrementAndGet(); accion.run(); }
        @TransactionalEventListener void confirmado(TurnoReprogramado e) { reprogramadosConfirmados.incrementAndGet(); }
    }
}
