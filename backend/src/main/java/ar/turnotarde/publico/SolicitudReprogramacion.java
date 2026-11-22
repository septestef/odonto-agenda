package ar.turnotarde.publico;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;

/** Contrato estricto local a la gestión: no cambia la tolerancia del mapper global. */
@JsonDeserialize(using = SolicitudReprogramacion.Lector.class)
public record SolicitudReprogramacion(@NotNull(message = "Elegí un horario") OffsetDateTime inicio) {
    static final class Lector extends ValueDeserializer<SolicitudReprogramacion> {
        @Override
        public SolicitudReprogramacion deserialize(JsonParser parser, DeserializationContext contexto) {
            var objeto = contexto.readTree(parser);
            var inicio = objeto.get("inicio");
            if (!objeto.isObject() || objeto.size() > (inicio == null ? 0 : 1)) {
                return contexto.reportInputMismatch(SolicitudReprogramacion.class, "Solo se admite inicio.");
            }
            if (inicio == null || inicio.isNull()) {
                return new SolicitudReprogramacion(null);
            }
            if (inicio.isString()) {
                try {
                    return new SolicitudReprogramacion(OffsetDateTime.parse(inicio.stringValue()));
                } catch (DateTimeParseException invalida) {
                    // Evitar propagar el valor recibido en el diagnóstico.
                }
            }
            return contexto.reportInputMismatch(SolicitudReprogramacion.class, "Se espera una fecha con offset.");
        }
    }
}
