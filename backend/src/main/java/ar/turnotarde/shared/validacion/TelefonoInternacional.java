package ar.turnotarde.shared.validacion;

import java.util.regex.Pattern;

/** Mismo formato de teléfono en la reserva y en el contacto posterior. No normaliza. */
public final class TelefonoInternacional {
    public static final String E164 = "^\\+[1-9][0-9]{7,14}$";
    private static final Pattern PATRON = Pattern.compile(E164);

    private TelefonoInternacional() {
    }

    public static boolean esValido(String telefono) {
        return telefono != null && PATRON.matcher(telefono).matches();
    }
}
