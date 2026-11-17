package ar.turnotarde.shared.error;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Cuerpo uniforme de error de la API: {@code { "codigo", "mensaje", "detalles"? }}.
 * {@code detalles} se omite cuando es nulo o vacío.
 */
public record ErrorApi(String codigo, String mensaje, @JsonInclude(JsonInclude.Include.NON_EMPTY) Object detalles) {

    public ErrorApi(String codigo, String mensaje) {
        this(codigo, mensaje, null);
    }
}
