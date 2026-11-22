package ar.turnotarde.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/**
 * Contador de intentos fallidos del login, en memoria y por nodo (D7). Clave = email normalizado;
 * cada actualización es atómica y la hora sale siempre del {@link Clock} inyectado.
 */
@Component
public class ControlIntentosLogin {

    static final int MAX_INTENTOS = 5;
    static final Duration BLOQUEO = Duration.ofMinutes(15);
    private static final int MAX_ENTRADAS = 1_000;

    private final Clock reloj;
    private final ConcurrentMap<String, Registro> intentos = new ConcurrentHashMap<>();

    public ControlIntentosLogin(Clock reloj) {
        this.reloj = reloj;
    }

    /** Registra un fallo y devuelve el estado resultante; el quinto fallo ya bloquea. */
    public EstadoBloqueo registrarFallo(String email) {
        String clave = normalizar(email);
        Instant ahora = Instant.now(reloj);
        Registro resultado = intentos.compute(clave, (ignorada, anterior) -> {
            Registro vigente = vigente(anterior, ahora);
            if (vigente == null) {
                return new Registro(1, ahora, null);
            }
            if (vigente.bloqueadoHasta() != null) {
                return vigente;
            }
            int fallos = vigente.fallos() + 1;
            Instant hasta = fallos >= MAX_INTENTOS ? ahora.plus(BLOQUEO) : null;
            return new Registro(fallos, ahora, hasta);
        });
        purgarSiEsNecesario(ahora);
        return estadoDe(resultado, ahora);
    }

    /** Un login correcto limpia el historial de ese email. */
    public void registrarExito(String email) {
        intentos.remove(normalizar(email));
    }

    /** Estado actual sin contar un fallo: lo consulta el servicio antes de evaluar credenciales. */
    public EstadoBloqueo estado(String email) {
        String clave = normalizar(email);
        Instant ahora = Instant.now(reloj);
        Registro registro = intentos.get(clave);
        Registro vigente = vigente(registro, ahora);
        if (registro != null && vigente == null) {
            intentos.remove(clave, registro);
        }
        return estadoDe(vigente, ahora);
    }

    private static Registro vigente(Registro registro, Instant ahora) {
        if (registro == null) {
            return null;
        }
        if (registro.bloqueadoHasta() != null && !ahora.isBefore(registro.bloqueadoHasta())) {
            return null;
        }
        return registro;
    }

    private static EstadoBloqueo estadoDe(Registro registro, Instant ahora) {
        if (registro == null || registro.bloqueadoHasta() == null) {
            return new EstadoBloqueo(false, Duration.ZERO, null);
        }
        Instant hasta = registro.bloqueadoHasta();
        return new EstadoBloqueo(true, Duration.between(ahora, hasta), hasta);
    }

    /** Sin esto, un atacante que rota emails haría crecer el mapa sin límite. */
    private void purgarSiEsNecesario(Instant ahora) {
        if (intentos.size() <= MAX_ENTRADAS) {
            return;
        }
        intentos.entrySet().removeIf(entrada ->
                entrada.getValue().ultimoFallo().plus(BLOQUEO).isBefore(ahora));
    }

    private static String normalizar(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }

    public record EstadoBloqueo(boolean bloqueado, Duration restante, Instant bloqueadoHasta) {
    }

    private record Registro(int fallos, Instant ultimoFallo, Instant bloqueadoHasta) {
    }
}
