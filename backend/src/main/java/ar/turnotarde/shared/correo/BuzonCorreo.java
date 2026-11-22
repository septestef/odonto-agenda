package ar.turnotarde.shared.correo;

import java.util.regex.Pattern;

/** Admisibilidad para envío: un solo buzón ASCII, sin normalización ni destinatario alternativo. */
public final class BuzonCorreo {
    private static final String ATOMO = "[a-zA-Z0-9!#$%&'*+/=?^_`{|}~-]+";
    private static final String ETIQUETA = "[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?";
    private static final Pattern BUZON = Pattern.compile(ATOMO + "(?:\\." + ATOMO + ")*@"
            + ETIQUETA + "(?:\\." + ETIQUETA + ")+");

    private BuzonCorreo() {
    }

    public static boolean admisible(String email) {
        return email != null && email.length() <= 254 && email.indexOf('@') <= 64 && BUZON.matcher(email).matches();
    }
}
