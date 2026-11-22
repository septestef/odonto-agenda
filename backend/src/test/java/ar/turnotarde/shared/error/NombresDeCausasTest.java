package ar.turnotarde.shared.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class NombresDeCausasTest {
    @Test
    void cortaElCicloPorIdentidadSinLeerMensajes() {
        var raiz = new IllegalStateException("mensaje raíz");
        var segundo = new IllegalStateException("otro mensaje");
        var tercero = new IllegalArgumentException("mensaje final");
        raiz.initCause(segundo);
        segundo.initCause(tercero);
        tercero.initCause(raiz);
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertThat(ManejadorErrores.nombresDeCausas(raiz))
                .containsExactly("java.lang.IllegalStateException", "java.lang.IllegalStateException",
                        "java.lang.IllegalArgumentException"));
    }
}
