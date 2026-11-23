package ar.turnotarde.turno;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnotarde.soporte.ConfiguracionRelojDePrueba;
import ar.turnotarde.soporte.CuentaDePrueba;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * C-15: disponibilidad del odontólogo autenticado. Reloj fijo en 2026-11-25T12:00:00Z (09:00 local); el
 * miércoles 25 atiende de 09:00 a 13:00, igual que el escenario del spec.
 */
@AutoConfigureMockMvc
@Import(ConfiguracionRelojDePrueba.class)
class DisponibilidadManualIT extends IntegracionTest {

    private static final String CABECERA_CSRF = "X-XSRF-TOKEN";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;

    private FixturesJdbc fixtures;
    private CuentaDePrueba.Cuenta cuenta;
    private long consulta;

    @BeforeEach
    void preparar() throws Exception {
        LimpiezaBase.limpiar(jdbc);
        cuenta = CuentaDePrueba.crear(mvc, jdbc, "dispman");
        fixtures = new FixturesJdbc(jdbc);
        consulta = fixtures.prestacion(cuenta.id(), "Consulta", 30);
        fixtures.horario(cuenta.id(), 3, "09:00", "13:00");
    }

    @AfterEach
    void limpiar() {
        LimpiezaBase.limpiar(jdbc);
    }

    private ResultActions consultar(Object prestacionId, String fecha) throws Exception {
        return mvc.perform(get("/api/disponibilidad")
                .session(cuenta.sesion().sesion())
                .param("prestacionId", String.valueOf(prestacionId))
                .param("fecha", fecha));
    }

    private MockHttpSession sesionSinRol() {
        var usuario = new User("sin-rol", "sin-usar", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        var contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities()));
        var sesion = new MockHttpSession();
        sesion.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, contexto);
        return sesion;
    }

    @Test
    void hoyIncluyeHorariosFuturosConMenosDe24HorasMientrasLaPublicaNoLosOfrece() throws Exception {
        String cuerpo = consultar(consulta, "2026-11-25")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(4)))
                .andExpect(jsonPath("$.fecha").value("2026-11-25"))
                .andExpect(jsonPath("$.prestacionId").value(consulta))
                .andExpect(jsonPath("$.duracionMinutos").value(30))
                .andExpect(jsonPath("$.horarios", hasSize(14)))
                .andExpect(jsonPath("$.horarios[0]", aMapWithSize(2)))
                .andExpect(jsonPath("$.horarios[0].inicio").value("2026-11-25T09:15:00-03:00"))
                .andExpect(jsonPath("$.horarios[0].hora").value("09:15"))
                .andExpect(jsonPath("$.horarios[13].hora").value("12:30"))
                .andReturn().getResponse().getContentAsString();

        mvc.perform(get("/api/public/{slug}/disponibilidad", cuenta.slug())
                        .param("prestacionId", String.valueOf(consulta)).param("fecha", "2026-11-25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horarios", hasSize(0)));

        org.assertj.core.api.Assertions.assertThat(cuerpo).doesNotContain("paciente", "bloqueo", "motivo");
    }

    @Test
    void excluyeBloqueosTurnosYFechasFueraDelHorizonte() throws Exception {
        long paciente = fixtures.paciente(cuenta.id(), "12345678");
        fixtures.bloqueo(cuenta.id(), Instant.parse("2026-11-25T12:00:00Z"),
                Instant.parse("2026-11-25T12:30:00Z"));
        fixtures.turno(cuenta.id(), paciente, consulta, Instant.parse("2026-11-25T12:30:00Z"),
                Instant.parse("2026-11-25T13:00:00Z"), "RESERVADO", OrigenTurno.MANUAL);
        fixtures.horario(cuenta.id(), 1, "09:00", "13:00");

        String cuerpo = consultar(consulta, "2026-11-25")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horarios[0].hora").value("10:00"))
                .andReturn().getResponse().getContentAsString();

        consultar(consulta, "2027-01-25")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.horarios", hasSize(0)));

        org.assertj.core.api.Assertions.assertThat(cuerpo).doesNotContain("p12345678@example.com", " Vacaciones");
    }

    @Test
    void sinSesionDa401YSinRolDa403SinExponerHorarios() throws Exception {
        Cookie csrf = cuenta.sesion().csrfCookie();
        mvc.perform(get("/api/disponibilidad")
                        .param("prestacionId", String.valueOf(consulta)).param("fecha", "2026-11-25"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));

        mvc.perform(get("/api/disponibilidad")
                        .session(sesionSinRol())
                        .param("prestacionId", String.valueOf(consulta)).param("fecha", "2026-11-25"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));

        // La cookie CSRF acompaña a la sesión real, pero un GET nunca la exige.
        mvc.perform(get("/api/disponibilidad")
                        .session(cuenta.sesion().sesion()).cookie(csrf).header(CABECERA_CSRF, csrf.getValue())
                        .param("prestacionId", String.valueOf(consulta)).param("fecha", "2026-11-25"))
                .andExpect(status().isOk());
    }

    @Test
    void parametrosFaltantesOMalFormadosDan400() throws Exception {
        mvc.perform(get("/api/disponibilidad").session(cuenta.sesion().sesion())
                        .param("fecha", "2026-11-25"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/disponibilidad").session(cuenta.sesion().sesion())
                        .param("prestacionId", String.valueOf(consulta)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/disponibilidad").session(cuenta.sesion().sesion())
                        .param("prestacionId", String.valueOf(consulta)).param("fecha", "25-11-2026"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/disponibilidad").session(cuenta.sesion().sesion())
                        .param("prestacionId", "no-numero").param("fecha", "2026-11-25"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void prestacionAjenaODadaDeBajaDa404Generico() throws Exception {
        CuentaDePrueba.Cuenta otra = CuentaDePrueba.crear(mvc, jdbc, "otradisp");
        long ajena = fixtures.prestacion(otra.id(), "Ajena", 30);

        consultar(ajena, "2026-11-25")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.mensaje").value("No encontramos lo que buscabas."));

        consultar(consulta + 1000, "2026-11-25").andExpect(status().isNotFound());
        jdbc.update("UPDATE prestacion SET activa = false WHERE id = ?", consulta);
        consultar(consulta, "2026-11-25").andExpect(status().isNotFound());
    }
}
