package ar.turnotarde.publico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.json.JsonMapper;

class SolicitudReprogramacionTest {
    private final JsonMapper mapper = JsonMapper.builder().build();

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"inicio\":null}"})
    void ausenciaONuloSeDeleganABeanValidation(String json) {
        assertThat(mapper.readValue(json, SolicitudReprogramacion.class).inicio()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "{\"inicio\":12}", "{\"inicio\":\"2026-11-27T10:00:00\"}",
            "{\"extra\":true}", "{\"inicio\":null,\"extra\":true}"})
    void cuerpoFueraDeContratoNoSeDeserializa(String json) {
        assertThatThrownBy(() -> mapper.readValue(json, SolicitudReprogramacion.class))
                .isInstanceOf(DatabindException.class);
    }

    @Test
    void traduceOffsetExplicitoSinInterpretarLaZonaDelServidor() {
        var solicitud = mapper.readValue("""
                {"inicio":"2026-11-27T14:00:00+02:00"}""", SolicitudReprogramacion.class);
        assertThat(solicitud.inicio().toInstant()).isEqualTo(Instant.parse("2026-11-27T12:00:00Z"));
    }
}
