package ar.turnotarde.turno;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * Token de gestión de un turno (RN-SE-02): 32 bytes aleatorios en base64url sin padding. Solo se
 * persiste {@link #hash()}; el {@link #plano()} viaja por el evento al email y nunca se loguea.
 */
public record TokenTurno(String plano, String hash) {

    public static final String FORMATO = "[A-Za-z0-9_-]{43}";
    private static final Pattern SEGMENTO = Pattern.compile(FORMATO);
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final int BYTES = 32;

    public static TokenTurno generar() {
        byte[] bytes = new byte[BYTES];
        ALEATORIO.nextBytes(bytes);
        String plano = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new TokenTurno(plano, hashear(plano));
    }

    static boolean formatoValido(String token) {
        return token != null && SEGMENTO.matcher(token).matches();
    }

    /** SHA-256 del texto UTF-8 del token, en hexadecimal minúscula (64 caracteres). Única función de hash. */
    public static String hashear(String tokenPlano) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256")
                    .digest(tokenPlano.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    @Override
    public String toString() {
        return "TokenTurno[plano=***, hash=" + hash + "]";
    }
}
