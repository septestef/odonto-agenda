package ar.turnotarde.publico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.turnotarde.soporte.ConfiguracionRelojDePrueba;
import ar.turnotarde.soporte.FixturesJdbc;
import ar.turnotarde.soporte.IntegracionTest;
import ar.turnotarde.soporte.LimpiezaBase;
import ar.turnotarde.soporte.RelojDePrueba;
import ar.turnotarde.turno.TokenTurno;
import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@AutoConfigureMockMvc
@Import(ConfiguracionRelojDePrueba.class)
class CancelacionTurnoTokenIT extends IntegracionTest {
    private static final Instant AHORA = Instant.parse("2026-11-25T12:00:00Z");
    private static final Instant INICIO = Instant.parse("2026-11-27T12:00:00Z");
    private static final String TOKEN = "C".repeat(42) + "A";
    private static final String RUTA = "/api/public/turnos/{token}/cancelar";

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired RelojDePrueba reloj;
    private FixturesJdbc fx;
    private long od;
    private long pr;
    private long paciente;

    @BeforeEach
    void preparar() {
        LimpiezaBase.limpiar(jdbc);
        reloj.fijar(AHORA);
        fx = new FixturesJdbc(jdbc);
        od = fx.odontologo("demo");
        pr = fx.prestacion(od, "Consulta", 30);
        paciente = fx.paciente(od, "12345678");
        fx.horario(od, 5, "09:00", "13:00");
    }

    @AfterEach
    void limpiar() {
        reloj.fijar(AHORA);
        LimpiezaBase.limpiar(jdbc);
    }

    @Test
    void cancelaUnInstanteAntesAunConInactivosYLiberaElIntervaloEnPostgresql() throws Exception {
        long original = turno("RESERVADO");
        jdbc.update("UPDATE odontologo SET activo = false WHERE id = ?", od);
        jdbc.update("UPDATE prestacion SET activa = false WHERE id = ?", pr);
        Instant antes = INICIO.minusSeconds(1);
        reloj.fijar(antes);

        cancelar(TOKEN).andExpect(status().isNoContent()).andExpect(content().string(""))
                .andExpect(header().doesNotExist("Location"));

        var cambio = jdbc.queryForMap("SELECT estado, cancelado_por FROM turno WHERE id = ?", original);
        assertThat(cambio).containsEntry("estado", "CANCELADO").containsEntry("cancelado_por", "PACIENTE");
        Instant canceladoEn = jdbc.queryForObject("SELECT cancelado_en FROM turno WHERE id = ?",
                (rs, n) -> rs.getObject(1, OffsetDateTime.class).toInstant(), original);
        assertThat(canceladoEn).isEqualTo(antes);
        assertThat(fx.turno(od, paciente, pr, INICIO, INICIO.plusSeconds(1800), "RESERVADO")).isNotEqualTo(original);
    }

    @Test
    void c07VuelveAOfrecerElHorarioSiCumpleLosOtrosFiltros() throws Exception {
        turno("RESERVADO");
        cancelar(TOKEN).andExpect(status().isNoContent());
        mvc.perform(get("/api/public/demo/disponibilidad").param("fecha", "2026-11-27")
                .param("prestacionId", Long.toString(pr))).andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.horarios[0].inicio").value("2026-11-27T09:00:00-03:00"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"RESERVADO", "CANCELADO", "ATENDIDO", "AUSENTE"})
    void inicioIgualOPasadoPrevaleceSobreEstadoYNoEscribe(String estado) throws Exception {
        long id = turno(estado);
        var antes = jdbc.queryForMap("SELECT * FROM turno WHERE id = ?", id);
        for (Instant ahora : new Instant[]{INICIO, INICIO.plusSeconds(1)}) {
            reloj.fijar(ahora);
            cancelar(TOKEN).andExpect(status().isConflict()).andExpect(content().json("""
                    {"codigo":"RN-TU-07","mensaje":"Este turno ya empezó. Solo podés consultarlo."}""",
                    JsonCompareMode.STRICT));
        }
        assertThat(jdbc.queryForMap("SELECT * FROM turno WHERE id = ?", id)).isEqualTo(antes);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CANCELADO", "ATENDIDO", "AUSENTE"})
    void turnoFuturoNoReservadoQuedaSoloParaConsulta(String estado) throws Exception {
        long id = turno(estado);
        var antes = jdbc.queryForMap("SELECT * FROM turno WHERE id = ?", id);
        cancelar(TOKEN).andExpect(status().isConflict()).andExpect(content().json("""
                {"codigo":"RN-SE-02","mensaje":"Este turno solo se puede consultar."}""", JsonCompareMode.STRICT));
        assertThat(jdbc.queryForMap("SELECT * FROM turno WHERE id = ?", id)).isEqualTo(antes);
    }

    @Test
    void tokenInvalidoDesconocidoYHashPresentadoComoTokenNoAlteranElTurno() throws Exception {
        long id = turno("RESERVADO");
        var antes = jdbc.queryForMap("SELECT * FROM turno WHERE id = ?", id);
        for (String token : new String[]{"invalido", "D".repeat(43), TokenTurno.hashear(TOKEN)}) {
            cancelar(token).andExpect(status().isNotFound()).andExpect(content().json("""
                    {"codigo":"NO_ENCONTRADO","mensaje":"No encontramos lo que buscabas."}""", JsonCompareMode.STRICT));
        }
        assertThat(jdbc.queryForMap("SELECT * FROM turno WHERE id = ?", id)).isEqualTo(antes);
    }

    private ResultActions cancelar(String token) throws Exception {
        return mvc.perform(post(RUTA, token))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(result -> assertThat(result.getRequest().getSession(false)).isNull());
    }

    private long turno(String estado) {
        return fx.turno(od, paciente, pr, INICIO, INICIO.plusSeconds(1800), estado, TokenTurno.hashear(TOKEN));
    }
}
