package ar.turnotarde.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnotarde.soporte.AutenticacionDePrueba;
import ar.turnotarde.soporte.ConfiguracionRelojDePrueba;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/** Escenarios de `autenticacion-odontologo`: login, validación y respuesta genérica. */
@AutoConfigureMockMvc
@Import(ConfiguracionRelojDePrueba.class)
class LoginIT extends IntegracionTest {

    private static final String CLAVE = "ClaveSegura123!";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper json;

    private FixturesJdbc fixtures;
    private String email;
    private long odontologoId;

    @BeforeEach
    void preparar() {
        LimpiezaBase.limpiar(jdbc);
        fixtures = new FixturesJdbc(jdbc);
        email = correoNuevo();
        odontologoId = fixtures.odontologo("demo-" + UUID.randomUUID(), email,
                AutenticacionDePrueba.hash(CLAVE), true);
    }

    @AfterEach
    void dejarLaBaseVacia() {
        LimpiezaBase.limpiar(jdbc);
    }

    @Test
    void credencialesValidasDevuelvenElDtoYDejanSesionIniciada() throws Exception {
        ResultActions respuesta = login(email, CLAVE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(odontologoId))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.nombre").value("Nombre"))
                .andExpect(jsonPath("$.apellido").value("Apellido"))
                .andExpect(jsonPath("$.slug").exists())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.password_hash").doesNotExist());

        String cuerpo = respuesta.andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain("$2").doesNotContain("!sin-hash");
        // MockMvc no emite Set-Cookie: la cookie JSESSIONID se comprueba con Tomcat en SesionCookieIT.

        MockHttpSession sesion = (MockHttpSession) respuesta.andReturn().getRequest().getSession(false);
        assertThat(sesion).isNotNull();
        mvc.perform(get("/api/auth/me").session(sesion))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(odontologoId));
    }

    @Test
    void emailConMayusculasYEspaciosFunciona() throws Exception {
        login("   " + email.toUpperCase() + "   ", CLAVE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(odontologoId));
    }

    @Test
    void emailInexistenteContrasenaIncorrectaYCuentaInactivaDanElMismo401() throws Exception {
        String inexistente = correoNuevo();
        String cuerpoInexistente = login(inexistente, CLAVE)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("RN-AU-01"))
                .andReturn().getResponse().getContentAsString();

        String cuerpoClave = login(email, "ClaveIncorrecta")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("RN-AU-01"))
                .andReturn().getResponse().getContentAsString();

        String emailInactivo = correoNuevo();
        fixtures.odontologo("inactivo-" + UUID.randomUUID(), emailInactivo,
                AutenticacionDePrueba.hash(CLAVE), false);
        String cuerpoInactivo = login(emailInactivo, CLAVE)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("RN-AU-01"))
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpoClave).isEqualTo(cuerpoInexistente).isEqualTo(cuerpoInactivo);
    }

    @Test
    void emailMalFormadoDa400ConDetalleDelCampo() throws Exception {
        login("no-es-un-email", "secreta")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles[0].campo").value("email"));
    }

    @Test
    void contrasenaVaciaDa400ConDetalleDelCampo() throws Exception {
        login(email, "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles[0].campo").value("password"));
    }

    @Test
    void contrasenaDe72CaracteresConEnyeDa400YSinConsumirIntentos() throws Exception {
        String conEnye = "a".repeat(71) + "ñ"; // 72 caracteres, 73 bytes UTF-8
        assertThat(conEnye.getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(73);

        login(email, conEnye)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.detalles[0].campo").value("password"));

        // La validación no evaluó credenciales ni consumió el contador de bloqueo.
        login(email, CLAVE).andExpect(status().isOk());
    }

    @Test
    void contrasenaDe72BytesAsciiPasaLaValidacion() throws Exception {
        // No es la contraseña correcta: si la validación la aceptó, el rechazo es 401 y no 400.
        login(email, "a".repeat(72))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("RN-AU-01"));
    }

    @Test
    void unHashQueNoEsBcryptNuncaAutentica() throws Exception {
        String correo = correoNuevo();
        fixtures.odontologo("pendiente-" + UUID.randomUUID(), correo,
                "!PENDIENTE-DEMO_PASSWORD", true);

        login(correo, CLAVE)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("RN-AU-01"));
    }

    private ResultActions login(String correo, String clave) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", correo, "password", clave))));
    }

    private static String correoNuevo() {
        return "u-" + UUID.randomUUID() + "@example.com";
    }
}
