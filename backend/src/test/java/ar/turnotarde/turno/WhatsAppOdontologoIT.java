package ar.turnotarde.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnotarde.soporte.ConfiguracionRelojDePrueba;
import ar.turnotarde.soporte.CuentaDePrueba;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import ar.turnotarde.soporte.RelojDePrueba;
import ar.turnotarde.turno.eventos.TurnoCancelado;
import ar.turnotarde.turno.eventos.TurnoReprogramado;
import ar.turnotarde.turno.eventos.TurnoReservado;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
@Import(ConfiguracionRelojDePrueba.class)
@RecordApplicationEvents
class WhatsAppOdontologoIT extends IntegracionTest {
    private static final String RUTA = "/api/turnos/{id}/whatsapp";
    private static final Instant AHORA = Instant.parse("2026-11-27T01:15:00Z");
    private static final Instant INICIO = Instant.parse("2026-11-27T01:30:00Z");
    private static final String NO_ENCONTRADO =
            """
            {"codigo":"NO_ENCONTRADO","mensaje":"No encontramos lo que buscabas."}
            """;
    private static final String URL =
            "https://wa.me/5493511234567?text=Te%20recordamos%20tu%20turno%20del%2026%2F11%2F2026"
            + "%20a%20las%2022%3A30%20para%20Limpieza%20%26%20revisi%C3%B3n."
            + "%20Direcci%C3%B3n%3A%20Av.%20Col%C3%B3n%20%2B%20%2320%25.";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RelojDePrueba reloj;
    @MockitoBean private JavaMailSender correo;
    private FixturesJdbc fixtures;
    private CuentaDePrueba.Cuenta dueno;
    private long paciente;
    private long turno;

    @BeforeEach
    void preparar() throws Exception {
        LimpiezaBase.limpiar(jdbc);
        reloj.fijar(AHORA);
        fixtures = new FixturesJdbc(jdbc);
        dueno = CuentaDePrueba.crear(mvc, jdbc, "wa");
        paciente = fixtures.paciente(dueno.id(), "12345678");
        long prestacion = fixtures.prestacion(dueno.id(), "Limpieza & revisión", 30);
        turno = fixtures.turno(dueno.id(), paciente, prestacion, INICIO,
                INICIO.plus(30, ChronoUnit.MINUTES), "RESERVADO");
        jdbc.update("UPDATE odontologo SET direccion_consultorio = ? WHERE id = ?", "Av. Colón + #20%", dueno.id());
    }

    @AfterEach
    void limpiar() {
        LimpiezaBase.limpiar(jdbc);
    }

    @ParameterizedTest
    @EnumSource(OrigenTurno.class)
    void consultaVigenteSinCsrfIgnoraDuenoDeclaradoYNoProduceCambios(OrigenTurno origen, ApplicationEvents eventos)
            throws Exception {
        jdbc.update("UPDATE turno SET origen = ?, creado_en = ?, email_gestion = ? WHERE id = ?",
                origen.name(), Timestamp.from(AHORA), "gestion@example.test", turno);
        jdbc.update("UPDATE paciente SET telefono = ? WHERE id = ?", "+5493511234567", paciente);
        fixtures.notificacion(turno, "CONFIRMACION", "ENVIADO");
        long ajeno = fixtures.odontologo("wa-ajeno");
        var antes = datosPersistidos();

        for (int intento = 0; intento < 2; intento++) {
            json(consultarDeclarandoDueno(turno, ajeno), 200, "{\"url\":\"" + URL + "\"}")
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                    .andExpect(header().doesNotExist(HttpHeaders.LOCATION));
        }

        assertThat(datosPersistidos()).isEqualTo(antes);
        assertThat(eventos.stream(TurnoReservado.class)).isEmpty();
        assertThat(eventos.stream(TurnoCancelado.class)).isEmpty();
        assertThat(eventos.stream(TurnoReprogramado.class)).isEmpty();
        verifyNoInteractions(correo);
    }

    @Test
    void sinSesionSeRechazaAntesDeValidarElId() throws Exception {
        json(mvc.perform(get(RUTA, "abc")), 401, """
                {"codigo":"NO_AUTENTICADO","mensaje":"Tu sesión no está iniciada o expiró. Volvé a ingresar."}
                """);
    }

    @Test
    void sesionSinRolSeRechazaAntesDeValidarElId() throws Exception {
        var sesion = dueno.sesion().sesion();
        var clave = HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;
        var real = (SecurityContext) sesion.getAttribute(clave);
        var sinRol = SecurityContextHolder.createEmptyContext();
        sinRol.setAuthentication(new UsernamePasswordAuthenticationToken(real.getAuthentication().getPrincipal(),
                null, List.of(new SimpleGrantedAuthority("ROLE_OTRO"))));
        sesion.setAttribute(clave, sinRol);

        json(consultar("abc"), 403, """
                {"codigo":"ACCESO_DENEGADO","mensaje":"No pudimos verificar la solicitud. Recargá la página e intentá de nuevo."}
                """);
    }

    @ParameterizedTest
    @ValueSource(strings = {"texto", "9223372036854775808", "-9223372036854775809"})
    void idNoConvertibleOFueraDeInt64NoEcoaEntrada(String id) throws Exception {
        json(consultar(id), 400, """
                {"codigo":"SOLICITUD_INVALIDA","mensaje":"No pudimos interpretar la solicitud."}
                """);
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1})
    void idNoPositivoTieneDetalleC12(long id) throws Exception {
        json(consultar(id), 400, """
                {"codigo":"VALIDACION","mensaje":"Hay datos inválidos. Revisalos y probá de nuevo.",
                 "detalles":[{"campo":"id","mensaje":"Ingresá un identificador mayor a 0."}]}
                """);
    }

    @Test
    void ajenoCanceladoSinDireccionEsIndistinguibleDeInexistente() throws Exception {
        long ajeno = fixtures.odontologo("wa-ajeno");
        long id = fixtures.turno(ajeno, fixtures.paciente(ajeno, "87654321"),
                fixtures.prestacion(ajeno, "No visible", 30), INICIO,
                INICIO.plus(30, ChronoUnit.MINUTES), "CANCELADO");
        var cuerpoAjeno = json(consultarDeclarandoDueno(id, ajeno), 404, NO_ENCONTRADO).andReturn().getResponse();
        var cuerpoInexistente = json(consultar(Long.MAX_VALUE), 404, NO_ENCONTRADO).andReturn().getResponse();
        assertThat(cuerpoAjeno.getContentAsByteArray()).containsExactly(cuerpoInexistente.getContentAsByteArray());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void turnoPropioConReferenciaAjenaNoDevuelveDatosDeOtroConsultorio(boolean pacienteAjeno) throws Exception {
        long ajeno = fixtures.odontologo("wa-ajeno");
        if (pacienteAjeno) {
            jdbc.update("UPDATE turno SET paciente_id = ? WHERE id = ?", fixtures.paciente(ajeno, "87654321"), turno);
        } else {
            jdbc.update("UPDATE turno SET prestacion_id = ? WHERE id = ?",
                    fixtures.prestacion(ajeno, "No visible", 30), turno);
        }

        json(consultar(turno), 404, NO_ENCONTRADO);
    }

    @Test
    void unTurnoQueYaEmpezoDevuelve409SinUrl() throws Exception {
        reloj.fijar(INICIO);
        json(consultar(turno), 409, """
                {"codigo":"RN-NO-06","mensaje":"Solo podés preparar un WhatsApp para un turno reservado que todavía no empezó."}
                """);
    }

    @Test
    void telefonoNacionalDevuelve422SinUrl() throws Exception {
        jdbc.update("UPDATE paciente SET telefono = ? WHERE id = ?", "3511234567", paciente);
        json(consultar(turno), 422, """
                {"codigo":"RN-NO-07","mensaje":"El paciente no tiene un teléfono internacional válido."}
                """);
    }

    @Test
    void direccionAusenteDevuelve422SinUrl() throws Exception {
        jdbc.update("UPDATE odontologo SET direccion_consultorio = NULL WHERE id = ?", dueno.id());
        json(consultar(turno), 422, """
                {"codigo":"RN-NO-07","mensaje":"Falta la dirección del consultorio."}
                """);
    }

    private ResultActions consultar(Object id) throws Exception {
        return mvc.perform(get(RUTA, id).session(dueno.sesion().sesion()));
    }

    private ResultActions consultarDeclarandoDueno(long id, long duenoDeclarado) throws Exception {
        return mvc.perform(get(RUTA, id).session(dueno.sesion().sesion())
                .queryParam("odontologoId", Long.toString(duenoDeclarado))
                .header("X-Odontologo-Id", Long.toString(duenoDeclarado)));
    }

    private static ResultActions json(ResultActions resultado, int estado, String esperado) throws Exception {
        return resultado.andExpect(status().is(estado))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(esperado, JsonCompareMode.STRICT));
    }

    private List<List<Map<String, Object>>> datosPersistidos() {
        return List.of(jdbc.queryForList("SELECT * FROM turno ORDER BY id"),
                jdbc.queryForList("SELECT * FROM paciente ORDER BY id"),
                jdbc.queryForList("SELECT * FROM notificacion ORDER BY id"));
    }
}
