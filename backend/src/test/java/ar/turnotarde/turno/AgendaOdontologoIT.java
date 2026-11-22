package ar.turnotarde.turno;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnotarde.soporte.CuentaDePrueba;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.List;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class AgendaOdontologoIT extends IntegracionTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManagerFactory emf;
    private final ObjectMapper json = new ObjectMapper();
    private CuentaDePrueba.Cuenta cuenta;
    private FixturesJdbc fixtures;
    private long paciente;
    private long prestacion;

    @BeforeEach
    void preparar() throws Exception {
        LimpiezaBase.limpiar(jdbc);
        cuenta = CuentaDePrueba.crear(mvc, jdbc, "agenda");
        fixtures = new FixturesJdbc(jdbc);
        paciente = fixtures.paciente(cuenta.id(), "12345678");
        prestacion = fixtures.prestacion(cuenta.id(), "Consulta", 30);
    }

    @AfterEach
    void limpiar() {
        LimpiezaBase.limpiar(jdbc);
    }

    private MvcResult consultar(String query, int status) throws Exception {
        return mvc.perform(get("/api/turnos" + query).session(cuenta.sesion().sesion()))
                .andExpect(status().is(status)).andReturn();
    }

    private void cuerpo(MvcResult resultado, String esperado) throws Exception {
        assertThat(resultado.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(json.readTree(resultado.getResponse().getContentAsString()))
                .isEqualTo(json.readTree(esperado));
    }

    private long turno(String inicio, String fin, String estado) {
        return fixtures.turno(cuenta.id(), paciente, prestacion, Instant.parse(inicio), Instant.parse(fin), estado);
    }

    @Test
    void diariaIncluyeCruceMedianocheExcluyeBordesYAjenosConJsonExacto() throws Exception {
        turno("2026-11-25T02:30:00Z", "2026-11-25T03:00:00Z", "RESERVADO");
        long cruce = turno("2026-11-25T02:45:00Z", "2026-11-25T03:15:00Z", "CANCELADO");
        turno("2026-11-26T03:00:00Z", "2026-11-26T03:30:00Z", "RESERVADO");
        CuentaDePrueba.Cuenta otra = CuentaDePrueba.crear(mvc, jdbc, "otra");
        fixtures.turno(otra.id(), fixtures.paciente(otra.id(), "12345678"),
                fixtures.prestacion(otra.id(), "Ajena", 30), Instant.parse("2026-11-25T02:45:00Z"),
                Instant.parse("2026-11-25T03:15:00Z"), "RESERVADO");

        cuerpo(consultar("?desde=2026-11-25&hasta=2026-11-26", 200), """
                [{"id":%d,"inicio":"2026-11-24T23:45:00-03:00","fin":"2026-11-25T00:15:00-03:00",
                  "estado":"CANCELADO","origen":"ONLINE",
                  "paciente":{"id":%d,"dni":"12345678","nombre":"Paciente","apellido":"Ficticio",
                    "telefono":"+5493510000000","email":"p12345678@example.com"},
                  "prestacion":{"id":%d,"nombre":"Consulta","duracionMinutos":30},
                  "canceladoPor":null,"canceladoEn":null}]
                """.formatted(cruce, paciente, prestacion));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "?desde=2026-11-25", "?hasta=2026-11-26",
            "?desde=&hasta=2026-11-26", "?desde=2026-11-25&hasta=",
            "?desde=2026-02-30&hasta=2026-11-26", "?desde=2026-11-25&hasta=2026-13-01",
            "?desde=25-11-2026&hasta=2026-11-26", "?desde=2026-11-25T00:00:00&hasta=2026-11-26",
            "?desde=2026-11-25&hasta=2026-11-26T00:00:00-03:00",
            "?desde=2026-11-25Z&hasta=2026-11-26", "?desde=2026-11-25&hasta=2026-11-26-03:00"})
    void bindingInvalidoNoEcoaEntrada(String query) throws Exception {
        cuerpo(consultar(query, 400), """
                {"codigo":"SOLICITUD_INVALIDA","mensaje":"No pudimos interpretar la solicitud."}
                """);
    }

    @ParameterizedTest
    @CsvSource({"2026-11-25, Elegí una fecha posterior a desde.",
            "2026-11-24, Elegí una fecha posterior a desde.",
            "2026-10-01, Elegí una fecha posterior a desde.",
            "2026-12-27, Elegí un rango de hasta 31 días."})
    void rangoSemanticoUsaDetalleExacto(String hasta, String mensaje) throws Exception {
        cuerpo(consultar("?desde=2026-11-25&hasta=" + hasta, 400), """
                {"codigo":"VALIDACION","mensaje":"Hay datos inválidos. Revisalos y probá de nuevo.",
                 "detalles":[{"campo":"hasta","mensaje":"%s"}]}
                """.formatted(mensaje));
    }

    @ParameterizedTest
    @CsvSource({"2026-11-25,2026-11-26", "2026-11-25,2026-12-26",
            "2020-01-01,2020-01-02", "2028-01-01,2028-01-02"})
    void vaciaAdmiteLimitesHistoricoYMasDe60Dias(String desde, String hasta) throws Exception {
        cuerpo(consultar("?desde=" + desde + "&hasta=" + hasta, 200), "[]");
    }

    @Test
    void semanaIntersecaLosDosLimitesUtcConDatos() throws Exception {
        turno("2026-11-23T02:30:00Z", "2026-11-23T03:00:00Z", "CANCELADO");
        long primero = turno("2026-11-23T03:00:00Z", "2026-11-23T03:30:00Z", "RESERVADO");
        long ultimo = turno("2026-11-30T02:45:00Z", "2026-11-30T03:15:00Z", "RESERVADO");
        turno("2026-11-30T03:00:00Z", "2026-11-30T03:30:00Z", "CANCELADO");
        var respuesta = json.readTree(consultar("?desde=2026-11-23&hasta=2026-11-30", 200)
                .getResponse().getContentAsString());
        assertThat(respuesta.size()).isEqualTo(2);
        assertThat(respuesta.get(0).get("id").asLong()).isEqualTo(primero);
        assertThat(respuesta.get(1).get("id").asLong()).isEqualTo(ultimo);
        assertThat(respuesta.get(0).get("inicio").asText()).isEqualTo("2026-11-23T00:00:00-03:00");
        assertThat(respuesta.get(1).get("fin").asText()).isEqualTo("2026-11-30T00:15:00-03:00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2020-01-01", "2028-01-01"})
    void devuelveDatosFueraDeVentanaDeReserva(String fecha) throws Exception {
        long id = turno(fecha + "T12:00:00Z", fecha + "T12:30:00Z", "RESERVADO");
        var hasta = java.time.LocalDate.parse(fecha).plusDays(1);
        var respuesta = json.readTree(consultar("?desde=" + fecha + "&hasta=" + hasta, 200)
                .getResponse().getContentAsString());
        assertThat(respuesta.size()).isEqualTo(1);
        assertThat(respuesta.get(0).get("id").asLong()).isEqualTo(id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"PACIENTE", "ODONTOLOGO"})
    void todosLosEstadosOrigenesDesempateContactosActualesYDuracionFijadaSinSecretos(String autor) throws Exception {
        long cancelado = turno("2026-11-25T12:00:00Z", "2026-11-25T12:30:00Z", "CANCELADO");
        long reservado = turno("2026-11-25T12:00:00Z", "2026-11-25T12:30:00Z", "RESERVADO");
        long atendido = turno("2026-11-25T12:30:00Z", "2026-11-25T13:00:00Z", "ATENDIDO");
        long ausente = turno("2026-11-25T13:00:00Z", "2026-11-25T13:30:00Z", "AUSENTE");
        jdbc.update("UPDATE prestacion SET nombre = 'Nombre vigente', duracion_minutos = 45, activa = false WHERE id = ?",
                prestacion);
        jdbc.update("""
                UPDATE paciente SET nombre = 'Ana', apellido = 'Pérez', telefono = '+5491100000000',
                  email = 'actual@example.test' WHERE id = ?""", paciente);
        String hash = "a".repeat(64);
        String reservadoEmail = "gestion-reservada@example.test";
        jdbc.update("""
                UPDATE turno SET origen = 'MANUAL', cancelado_por = ?,
                  cancelado_en = '2026-11-22T12:00:00Z', email_gestion = ?, token_hash = ? WHERE id = ?""",
                autor, reservadoEmail, hash, cancelado);
        MvcResult resultado = consultar("?desde=2026-11-25&hasta=2026-11-26", 200);
        String pacienteJson = """
                {"id":%d,"dni":"12345678","nombre":"Ana","apellido":"Pérez",
                 "telefono":"+5491100000000","email":"actual@example.test"}
                """.formatted(paciente);
        String prestacionJson = """
                {"id":%d,"nombre":"Nombre vigente","duracionMinutos":30}
                """.formatted(prestacion);
        String fila = """
                {"id":%d,"inicio":"2026-11-25T%s:00-03:00","fin":"2026-11-25T%s:00-03:00",
                 "estado":"%s","origen":"%s","paciente":%s,"prestacion":%s,
                 "canceladoPor":%s,"canceladoEn":%s}
                """;
        cuerpo(resultado, "[" + String.join(",",
                fila.formatted(cancelado, "09:00", "09:30", "CANCELADO", "MANUAL", pacienteJson, prestacionJson,
                        "\"" + autor + "\"", "\"2026-11-22T09:00:00-03:00\""),
                fila.formatted(reservado, "09:00", "09:30", "RESERVADO", "ONLINE", pacienteJson, prestacionJson, "null", "null"),
                fila.formatted(atendido, "09:30", "10:00", "ATENDIDO", "ONLINE", pacienteJson, prestacionJson, "null", "null"),
                fila.formatted(ausente, "10:00", "10:30", "AUSENTE", "ONLINE", pacienteJson, prestacionJson, "null", "null")) + "]");
        assertThat(resultado.getResponse().getContentAsString()).doesNotContain(hash, reservadoEmail,
                "token", "hash", "email_gestion", "emailGestion", "odontologoId", "creadoEn", "actualizadoEn");
    }

    @Test
    void unaConsultaSinEntidadesAlCrecerFilasYAsociaciones() throws Exception {
        turno("2026-11-25T12:00:00Z", "2026-11-25T12:30:00Z", "CANCELADO");
        var estadisticas = emf.unwrap(SessionFactory.class).getStatistics();
        boolean habilitadas = estadisticas.isStatisticsEnabled();
        estadisticas.setStatisticsEnabled(true);
        try {
            estadisticas.clear();
            assertThat(json.readTree(consultar("?desde=2026-11-25&hasta=2026-11-26", 200)
                    .getResponse().getContentAsString()).size()).isEqualTo(1);
            assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(1);
            assertThat(estadisticas.getEntityLoadCount()).isZero();
            for (int i = 0; i < 12; i++) {
                long otroPaciente = fixtures.paciente(cuenta.id(), "200000" + String.format("%02d", i));
                long otraPrestacion = fixtures.prestacion(cuenta.id(), "Prestación " + i, 45);
                fixtures.turno(cuenta.id(), otroPaciente, otraPrestacion,
                        Instant.parse("2026-11-25T12:00:00Z"), Instant.parse("2026-11-25T12:30:00Z"), "CANCELADO");
            }
            estadisticas.clear();
            assertThat(json.readTree(consultar("?desde=2026-11-25&hasta=2026-11-26", 200)
                    .getResponse().getContentAsString()).size()).isEqualTo(13);
            assertThat(estadisticas.getPrepareStatementCount()).isEqualTo(1);
            assertThat(estadisticas.getEntityLoadCount()).isZero();
        } finally {
            estadisticas.setStatisticsEnabled(habilitadas);
            estadisticas.clear();
        }
    }

    @Test
    void loginRealPermiteGetSinCsrfPeroAnonimoYRolIncorrectoNo() throws Exception {
        MvcResult anonimo = mvc.perform(get("/api/turnos?desde=2026-11-25&hasta=2026-11-26"))
                .andExpect(status().isUnauthorized()).andReturn();
        cuerpo(anonimo, """
                {"codigo":"NO_AUTENTICADO","mensaje":"Tu sesión no está iniciada o expiró. Volvé a ingresar."}
                """);
        assertThat(anonimo.getResponse().getHeader("Location")).isNull();
        cuerpo(consultar("?desde=2026-11-25&hasta=2026-11-26", 200), "[]");
        // El modelo solo crea ODONTOLOGO. Para el rechazo de rol, conservar el principal del login real
        // y cambiar únicamente las authorities de esa sesión, pasando por la cadena completa.
        var sesion = cuenta.sesion().sesion();
        var contexto = (SecurityContext) sesion.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        var sinRol = SecurityContextHolder.createEmptyContext();
        sinRol.setAuthentication(new UsernamePasswordAuthenticationToken(contexto.getAuthentication().getPrincipal(),
                null, List.of(new SimpleGrantedAuthority("ROLE_OTRO"))));
        sesion.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, sinRol);
        MvcResult prohibido = consultar("?desde=2026-11-25&hasta=2026-11-26", 403);
        cuerpo(prohibido, """
                {"codigo":"ACCESO_DENEGADO","mensaje":"No pudimos verificar la solicitud. Recargá la página e intentá de nuevo."}
                """);
        assertThat(prohibido.getResponse().getHeader("Location")).isNull();
    }

    @Test
    void sesionAConIdsBEnQueryHeaderYCuerpoNuncaLeeAgendaB() throws Exception {
        CuentaDePrueba.Cuenta otra = CuentaDePrueba.crear(mvc, jdbc, "otra");
        long propio = turno("2026-11-25T12:00:00Z", "2026-11-25T12:30:00Z", "RESERVADO");
        long ajeno = fixtures.turno(otra.id(), fixtures.paciente(otra.id(), "12345678"),
                fixtures.prestacion(otra.id(), "Ajena", 30), Instant.parse("2026-11-25T12:00:00Z"),
                Instant.parse("2026-11-25T12:30:00Z"), "RESERVADO");
        var a = mvc.perform(get("/api/turnos?desde=2026-11-25&hasta=2026-11-26&odontologoId=" + otra.id())
                        .session(cuenta.sesion().sesion()).header("odontologoId", otra.id())
                        .header("X-Odontologo-Id", otra.id()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"odontologoId\":" + otra.id() + "}"))
                .andExpect(status().isOk()).andReturn();
        var b = mvc.perform(get("/api/turnos?desde=2026-11-25&hasta=2026-11-26&odontologoId=" + cuenta.id())
                        .session(otra.sesion().sesion()).header("odontologoId", cuenta.id())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"odontologoId\":" + cuenta.id() + "}"))
                .andExpect(status().isOk()).andReturn();
        var datosA = json.readTree(a.getResponse().getContentAsString());
        var datosB = json.readTree(b.getResponse().getContentAsString());
        assertThat(datosA.size()).isEqualTo(1);
        assertThat(datosA.get(0).get("id").asLong()).isEqualTo(propio);
        assertThat(datosB.size()).isEqualTo(1);
        assertThat(datosB.get(0).get("id").asLong()).isEqualTo(ajeno);
        assertThat(a.getResponse().getContentAsString()).doesNotContain("Ajena");
    }

    @Test
    void errorDeConversionNoEcoaValoresReservadosNiQueryInyectada() throws Exception {
        String hash = "b".repeat(64);
        String emailGestion = "gestion-centinela@example.test";
        long id = turno("2026-11-25T12:00:00Z", "2026-11-25T12:30:00Z", "RESERVADO");
        jdbc.update("UPDATE turno SET token_hash = ?, email_gestion = ? WHERE id = ?", hash, emailGestion, id);
        for (String invalido : List.of(hash, emailGestion)) {
            MvcResult error = consultar("?desde=" + invalido + "&hasta=2026-11-26&odontologoId=" + cuenta.id(), 400);
            cuerpo(error, """
                    {"codigo":"SOLICITUD_INVALIDA","mensaje":"No pudimos interpretar la solicitud."}
                    """);
            assertThat(error.getResponse().getContentAsString()).doesNotContain(hash, emailGestion,
                    "token", "hash", "email_gestion", "emailGestion", "odontologoId");
        }
    }
}
