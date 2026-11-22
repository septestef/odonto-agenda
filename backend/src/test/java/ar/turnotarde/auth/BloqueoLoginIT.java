package ar.turnotarde.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnotarde.soporte.AutenticacionDePrueba;
import ar.turnotarde.soporte.ConfiguracionRelojDePrueba;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import ar.turnotarde.soporte.RelojDePrueba;
import java.time.Duration;
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
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

/** RN-AU-03: quinto fallo bloquea 15 min, con el reloj inyectado (D7 y D12). */
@AutoConfigureMockMvc
@Import(ConfiguracionRelojDePrueba.class)
class BloqueoLoginIT extends IntegracionTest {

    private static final String CLAVE = "ClaveSegura123!";
    private static final String CLAVE_INCORRECTA = "ClaveIncorrecta";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private RelojDePrueba reloj;
    @Autowired
    private ObjectMapper json;

    private String email;

    @BeforeEach
    void preparar() {
        LimpiezaBase.limpiar(jdbc);
        email = "u-" + UUID.randomUUID() + "@example.com";
        new FixturesJdbc(jdbc).odontologo("demo-" + UUID.randomUUID(), email,
                AutenticacionDePrueba.hash(CLAVE), true);
    }

    @AfterEach
    void dejarLaBaseVacia() {
        LimpiezaBase.limpiar(jdbc);
    }

    @Test
    void elCuartoFalloEs401YElQuintoEs429ConElTiempoRestante() throws Exception {
        for (int intento = 1; intento <= 4; intento++) {
            fallar().andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("RN-AU-01"));
        }

        String cuerpo = fallar()
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("RN-AU-03"))
                .andExpect(jsonPath("$.detalles.reintentarEnSegundos").value(900))
                .andExpect(jsonPath("$.detalles.bloqueadoHasta").isString())
                .andReturn().getResponse().getContentAsString();

        assertThat(cuerpo).contains("900");
    }

    @Test
    void credencialesCorrectasDuranteElBloqueoDan429() throws Exception {
        for (int intento = 1; intento <= 5; intento++) {
            fallar();
        }

        login(email, CLAVE)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("RN-AU-03"));
    }

    @Test
    void alVencerLosQuinceMinutosLaContrasenaCorrectaEntra() throws Exception {
        for (int intento = 1; intento <= 5; intento++) {
            fallar();
        }

        reloj.avanzar(Duration.ofMinutes(15));

        login(email, CLAVE).andExpect(status().isOk());
    }

    @Test
    void unLoginExitosoReiniciaElContador() throws Exception {
        for (int intento = 1; intento <= 3; intento++) {
            fallar();
        }
        login(email, CLAVE).andExpect(status().isOk());

        for (int intento = 1; intento <= 4; intento++) {
            fallar().andExpect(status().isUnauthorized()).andExpect(jsonPath("$.codigo").value("RN-AU-01"));
        }
        fallar().andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.codigo").value("RN-AU-03"));
    }

    @Test
    void emailsDistintosNoCompartenElBloqueo() throws Exception {
        for (int intento = 1; intento <= 5; intento++) {
            fallar();
        }

        String otro = "u-" + UUID.randomUUID() + "@example.com";
        login(otro, CLAVE_INCORRECTA)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("RN-AU-01"));
    }

    @Test
    void unEmailQueNoExisteTambienSeBloquea() throws Exception {
        String inexistente = "u-" + UUID.randomUUID() + "@example.com";

        for (int intento = 1; intento <= 4; intento++) {
            login(inexistente, CLAVE_INCORRECTA)
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.codigo").value("RN-AU-01"));
        }
        login(inexistente, CLAVE_INCORRECTA)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("RN-AU-03"));
    }

    private org.springframework.test.web.servlet.ResultActions fallar() throws Exception {
        return login(email, CLAVE_INCORRECTA);
    }

    private org.springframework.test.web.servlet.ResultActions login(String correo, String clave) throws Exception {
        return mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", correo, "password", clave))));
    }
}
