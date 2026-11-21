package ar.turnotarde.turno;

import ar.turnotarde.shared.error.ConflictoTurnos;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * Envuelve toda escritura de turnos: si la base rechaza por {@code turno_sin_solapamiento}, deadlock o
 * lock no disponible, responde {@code RN-TU-01}; cualquier otra excepción se relanza intacta (D8).
 * La escritura debe ser {@code saveAndFlush} para que la constraint salte acá y no en el commit.
 */
@Component
public class GuardiaSolapamiento {

    public <T> T ejecutar(Supplier<T> escritura) {
        try {
            return escritura.get();
        } catch (RuntimeException e) {
            if (ConflictoTurnos.esSolapamiento(e) || ConflictoTurnos.esFallaDeLock(e)) {
                throw ReglasTurno.horarioTomado();
            }
            throw e;
        }
    }
}
