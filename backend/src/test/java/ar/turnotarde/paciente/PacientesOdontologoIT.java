package ar.turnotarde.paciente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnotarde.soporte.CuentaDePrueba;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
class PacientesOdontologoIT extends IntegracionTest {
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    private CuentaDePrueba.Cuenta cuenta;
    private FixturesJdbc fixtures;

    @BeforeEach
    void preparar() throws Exception {
        LimpiezaBase.limpiar(jdbc);
        cuenta = CuentaDePrueba.crear(mvc, jdbc, "pacientes");
        fixtures = new FixturesJdbc(jdbc);
    }

    @AfterEach
    void limpiar() {
        LimpiezaBase.limpiar(jdbc);
    }

    private MvcResult consultar(String query, int esperado) throws Exception {
        return mvc.perform(get("/api/pacientes" + query).session(cuenta.sesion().sesion()))
                .andExpect(status().is(esperado)).andReturn();
    }

    private void cuerpo(MvcResult resultado, String esperado) throws Exception {
        assertThat(resultado.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(json.readTree(resultado.getResponse().getContentAsString()))
                .isEqualTo(json.readTree(esperado));
    }

    @Test
    void sinPacientesDevuelveWrapperExactoConDefaults() throws Exception {
        cuerpo(consultar("", 200), """
                {"contenido":[],"page":0,"size":20,"totalElementos":0,"totalPaginas":0}
                """);
    }

    @ParameterizedTest
    @ValueSource(strings = {"?page=", "?size=", "?page=texto", "?size=texto", "?page=1.5", "?size=2.5",
            "?page=2147483648", "?size=2147483648", "?page=-2147483649", "?size=-2147483649",
            "?page=-1&size=no-entero", "?page=no-entero&size=0"})
    void conversionInvalidaNoUsaDefaultsNiEcoaEntrada(String query) throws Exception {
        cuerpo(consultar(query, 400), """
                {"codigo":"SOLICITUD_INVALIDA","mensaje":"No pudimos interpretar la solicitud."}
                """);
    }

    @ParameterizedTest
    @CsvSource({"?page=-1,page,Ingresá una página mayor o igual a 0.",
            "?size=0,size,Elegí un tamaño entre 1 y 100.", "?size=101,size,Elegí un tamaño entre 1 y 100."})
    void semanticaInvalidaUsaDetalleExacto(String query, String campo, String mensaje) throws Exception {
        cuerpo(consultar(query, 400), """
                {"codigo":"VALIDACION","mensaje":"Hay datos inválidos. Revisalos y probá de nuevo.",
                 "detalles":[{"campo":"%s","mensaje":"%s"}]}
                """.formatted(campo, mensaje));
    }

    @Test
    void erroresDeAmbosCamposIncluyenLosDosDetalles() throws Exception {
        var error = json.readTree(consultar("?page=-1&size=101", 400).getResponse().getContentAsString());
        assertThat(error.size()).isEqualTo(3);
        assertThat(error.get("codigo").asText()).isEqualTo("VALIDACION");
        assertThat(error.get("mensaje").asText()).isEqualTo("Hay datos inválidos. Revisalos y probá de nuevo.");
        assertThat(error.get("detalles").size()).isEqualTo(2);
        assertThat(List.of(error.get("detalles").get(0), error.get("detalles").get(1)))
                .containsExactlyInAnyOrder(
                        json.readTree("""
                                {"campo":"page","mensaje":"Ingresá una página mayor o igual a 0."}
                                """),
                        json.readTree("""
                                {"campo":"size","mensaje":"Elegí un tamaño entre 1 y 100."}
                                """));
    }

    @Test
    void contactoVigenteDePacienteHistoricoConDniCompartidoYAislamientoEnAmbasDirecciones() throws Exception {
        CuentaDePrueba.Cuenta otra = CuentaDePrueba.crear(mvc, jdbc, "otra");
        long propio = fixtures.paciente(cuenta.id(), "12345678");
        long ajeno = fixtures.paciente(otra.id(), "12345678");
        long prestacion = fixtures.prestacion(cuenta.id(), "Consulta", 30);
        String hash = "c".repeat(64);
        String gestion = "reservado-paciente@example.test";
        long turno = fixtures.turno(cuenta.id(), propio, prestacion, Instant.parse("2020-01-01T12:00:00Z"),
                Instant.parse("2020-01-01T12:30:00Z"), "ATENDIDO", hash);
        jdbc.update("UPDATE turno SET email_gestion = ? WHERE id = ?", gestion, turno);
        jdbc.update("""
                UPDATE paciente SET nombre = 'Ana', apellido = 'Pérez', telefono = '+5491100000000',
                  email = 'actual@example.test' WHERE id = ?""", propio);
        MvcResult a = mvc.perform(get("/api/pacientes?odontologoId=" + otra.id())
                        .session(cuenta.sesion().sesion()).header("odontologoId", otra.id())
                        .header("X-Odontologo-Id", otra.id()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"odontologoId\":" + otra.id() + "}"))
                .andExpect(status().isOk()).andReturn();
        cuerpo(a, """
                {"contenido":[{"id":%d,"dni":"12345678","nombre":"Ana","apellido":"Pérez",
                  "telefono":"+5491100000000","email":"actual@example.test"}],
                 "page":0,"size":20,"totalElementos":1,"totalPaginas":1}
                """.formatted(propio));
        MvcResult b = mvc.perform(get("/api/pacientes?odontologoId=" + cuenta.id())
                        .session(otra.sesion().sesion()).header("odontologoId", cuenta.id())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"odontologoId\":" + cuenta.id() + "}"))
                .andExpect(status().isOk()).andReturn();
        cuerpo(b, """
                {"contenido":[{"id":%d,"dni":"12345678","nombre":"Paciente","apellido":"Ficticio",
                  "telefono":"+5493510000000","email":"p12345678@example.com"}],
                 "page":0,"size":20,"totalElementos":1,"totalPaginas":1}
                """.formatted(ajeno));
        for (MvcResult resultado : List.of(a, b)) {
            assertThat(resultado.getResponse().getContentAsString()).doesNotContain(hash, gestion,
                    "token", "hash", "email_gestion", "emailGestion", "odontologoId", "creadoEn", "actualizadoEn",
                    "historia", "odontograma");
        }
        for (String invalido : List.of(hash, gestion)) {
            cuerpo(consultar("?page=" + invalido, 400), """
                    {"codigo":"SOLICITUD_INVALIDA","mensaje":"No pudimos interpretar la solicitud."}
                    """);
        }
    }

    @Test
    void getUsaSesionRealSinCsrfYRechazaAnonimoORolIncorrecto() throws Exception {
        MvcResult anonimo = mvc.perform(get("/api/pacientes"))
                .andExpect(status().isUnauthorized()).andReturn();
        cuerpo(anonimo, """
                {"codigo":"NO_AUTENTICADO","mensaje":"Tu sesión no está iniciada o expiró. Volvé a ingresar."}
                """);
        assertThat(anonimo.getResponse().getHeader("Location")).isNull();
        cuerpo(consultar("", 200), """
                {"contenido":[],"page":0,"size":20,"totalElementos":0,"totalPaginas":0}
                """);
        var sesion = cuenta.sesion().sesion();
        var contexto = (SecurityContext) sesion.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        var sinRol = SecurityContextHolder.createEmptyContext();
        sinRol.setAuthentication(new UsernamePasswordAuthenticationToken(contexto.getAuthentication().getPrincipal(),
                null, List.of(new SimpleGrantedAuthority("ROLE_OTRO"))));
        sesion.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, sinRol);
        MvcResult prohibido = consultar("", 403);
        cuerpo(prohibido, """
                {"codigo":"ACCESO_DENEGADO","mensaje":"No pudimos verificar la solicitud. Recargá la página e intentá de nuevo."}
                """);
        assertThat(prohibido.getResponse().getHeader("Location")).isNull();
    }

    private List<Long> ids(MvcResult resultado, int page, int size, long total, long paginas) throws Exception {
        var respuesta = json.readTree(resultado.getResponse().getContentAsString());
        assertThat(respuesta.size()).isEqualTo(5);
        assertThat(respuesta.get("page").asInt()).isEqualTo(page);
        assertThat(respuesta.get("size").asInt()).isEqualTo(size);
        assertThat(respuesta.get("totalElementos").asLong()).isEqualTo(total);
        assertThat(respuesta.get("totalPaginas").asLong()).isEqualTo(paginas);
        List<Long> ids = new ArrayList<>();
        for (var paciente : respuesta.get("contenido")) {
            ids.add(paciente.get("id").asLong());
        }
        return ids;
    }

    @Test
    void pagina101PacientesSinTurnosOrdenaApellidoNombreIdYCuentaSoloPropios() throws Exception {
        // Orden de inserción deliberadamente distinto al contrato; grupos con empates reales.
        String[] apellidos = {"Zeta", "Alfa", "Zeta", "Alfa"};
        String[] nombres = {"Zoe", "Ana", "Ana", "Zoe"};
        List<List<Long>> grupos = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        for (int i = 0; i < 101; i++) {
            long id = fixtures.paciente(cuenta.id(), String.valueOf(30000000 + i));
            int grupo = i % 4;
            jdbc.update("UPDATE paciente SET apellido = ?, nombre = ? WHERE id = ?", apellidos[grupo], nombres[grupo], id);
            grupos.get(grupo).add(id);
        }
        CuentaDePrueba.Cuenta otra = CuentaDePrueba.crear(mvc, jdbc, "otra");
        fixtures.paciente(otra.id(), "30000000");
        fixtures.paciente(otra.id(), "30000001");
        var ordenados = new ArrayList<Long>();
        for (int grupo : new int[] {1, 3, 2, 0}) {
            ordenados.addAll(grupos.get(grupo));
        }

        assertThat(ids(consultar("", 200), 0, 20, 101, 6)).containsExactlyElementsOf(ordenados.subList(0, 20));
        assertThat(ids(consultar("?page=1", 200), 1, 20, 101, 6))
                .containsExactlyElementsOf(ordenados.subList(20, 40));
        assertThat(ids(consultar("?size=1", 200), 0, 1, 101, 101)).containsExactly(ordenados.get(0));
        List<Long> primera = ids(consultar("?page=0&size=100", 200), 0, 100, 101, 2);
        List<Long> segunda = ids(consultar("?page=1&size=100", 200), 1, 100, 101, 2);
        assertThat(primera).containsExactlyElementsOf(ordenados.subList(0, 100));
        assertThat(segunda).containsExactly(ordenados.get(100)).doesNotContainAnyElementsOf(primera);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 99, Integer.MAX_VALUE})
    void paginaExcedidaConservaTotalesSinOverflowNiOffsetJpaInvalido(int page) throws Exception {
        fixtures.paciente(cuenta.id(), "12345678");
        cuerpo(consultar("?page=" + page + "&size=100", 200), """
                {"contenido":[],"page":%d,"size":100,"totalElementos":1,"totalPaginas":1}
                """.formatted(page));
    }

    @Test
    void paginaMaximaDeColeccionVaciaConservaTotalesCero() throws Exception {
        cuerpo(consultar("?page=2147483647&size=100", 200), """
                {"contenido":[],"page":2147483647,"size":100,"totalElementos":0,"totalPaginas":0}
                """);
    }
}
