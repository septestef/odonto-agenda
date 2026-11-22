package ar.turnotarde.notificacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.turnotarde.shared.config.AppProperties;
import ar.turnotarde.shared.tiempo.ZonaNegocio;
import ar.turnotarde.turno.eventos.ResumenTurno;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PlantillasCorreoTest {

    private static final Instant INICIO = Instant.parse("2026-11-27T01:30:00Z");
    private static final String BASE = "https://example.test/consultorio/";
    private static final String TOKEN = "nuevo_ABC-123";
    private static final String DIRECCION = "Calle <Sur> & \"Norte\"";

    @ParameterizedTest
    @CsvSource({
            "CONFIRMACION, Confirmaste tu turno",
            "CANCELACION, Cancelamos tu turno",
            "REPROGRAMACION, Reprogramamos tu turno"
    })
    void muestraSnapshotEscapadoEnFechaLocal(TipoNotificacion tipo, String titulo) throws Exception {
        String html = renderizar(BASE, tipo, DIRECCION, TOKEN);

        assertThat(html).contains(titulo, "26/11/2026", "22:30", "Limpieza &lt;especial&gt; &amp;",
                        "Calle &lt;Sur&gt; &amp; &quot;Norte&quot;")
                .doesNotContain("27/11/2026", "01:30", "<especial>", "<Sur>", "sintetico@example.test");
        if (tipo == TipoNotificacion.CANCELACION) {
            assertThat(html).doesNotContain("href=", TOKEN);
        } else {
            assertThat(html).containsOnlyOnce("href=\"https://example.test/consultorio/turno/" + TOKEN + "\"")
                    .containsOnlyOnce("href=");
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "  ")
    void omiteLaDireccionCuandoNoEstaInformada(String direccion) throws Exception {
        for (TipoNotificacion tipo : new TipoNotificacion[]{
                TipoNotificacion.CONFIRMACION, TipoNotificacion.CANCELACION, TipoNotificacion.REPROGRAMACION}) {
            assertThat(renderizar(BASE, tipo, direccion, TOKEN)).doesNotContain("Dirección:", "null");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/relativa", "ftp://example.test", "https:/sin-host",
            "https://cuenta@example.test", "https://example.test/?q=1", "https://example.test/#fragmento"})
    void rechazaUnaBaseInvalidaSoloSiElAvisoNecesitaLink(String base) throws Exception {
        for (TipoNotificacion tipo : new TipoNotificacion[]{
                TipoNotificacion.CONFIRMACION, TipoNotificacion.REPROGRAMACION}) {
            assertThatThrownBy(() -> renderizar(base, tipo, DIRECCION, TOKEN))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("URL_INVALIDA");
        }
        assertThat(renderizar(base, TipoNotificacion.CANCELACION, DIRECCION, null)).doesNotContain("href=");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"../segmento", "a%2Fb", "a\" b", "a?b", "a#b", "á"})
    void nuncaInterpolaUnTokenFueraDelAlfabetoDeC07(String token) {
        assertThatThrownBy(() -> renderizar(BASE, TipoNotificacion.CONFIRMACION, DIRECCION, token))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("URL_INVALIDA");
    }

    @Test
    void incluyeUnSoloSeparadorYRepresentaLaBaseComoAtributoHtml() throws Exception {
        assertThat(renderizar("https://example.test/sede&dos///", TipoNotificacion.REPROGRAMACION, DIRECCION, TOKEN))
                .containsOnlyOnce("href=\"https://example.test/sede&amp;dos/turno/" + TOKEN + "\"");
        assertThat(renderizar("https://example.test", TipoNotificacion.CONFIRMACION, DIRECCION, TOKEN))
                .containsOnlyOnce("href=\"https://example.test/turno/" + TOKEN + "\"");
    }

    private String renderizar(String base, TipoNotificacion tipo, String direccion, String token) {
        var propiedades = new AppProperties(ZoneId.of("America/Argentina/Buenos_Aires"), URI.create(base), 60);
        var zona = new ZonaNegocio(Clock.fixed(INICIO, ZoneOffset.UTC), propiedades);
        var turno = new ResumenTurno(1, 2, INICIO, INICIO.plusSeconds(1800), 3, "Limpieza <especial> &", 30,
                "Paciente", "Sintético", "sintetico@example.test", "000", "Dental", "Prueba", direccion);
        return new PlantillasCorreo(zona, propiedades).renderizar(tipo, turno, token);
    }
}
