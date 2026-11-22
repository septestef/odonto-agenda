package ar.turnotarde.notificacion;

import java.util.regex.Pattern;

record CableadoSmtp(String host, String puerto, String usuario, String clave, String nombre) {
    private static final Pattern HOST = Pattern.compile(
            "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*");
    private static final Pattern PUERTO = Pattern.compile("[0-9]{1,5}");

    String variableInvalida() {
        if (host == null || host.length() > 253 || !HOST.matcher(host).matches()) {
            return "MAIL_HOST";
        }
        if (puerto == null || !PUERTO.matcher(puerto).matches() || numeroPuerto() < 1 || numeroPuerto() > 65535) {
            return "MAIL_PORT";
        }
        if (!AvisoCorreo.buzonValido(usuario)) {
            return "MAIL_USERNAME";
        }
        if (!textoSeguro(clave)) {
            return "MAIL_PASSWORD";
        }
        if (!textoSeguro(nombre)) {
            return "MAIL_FROM_NAME";
        }
        return null;
    }

    private static boolean textoSeguro(String valor) {
        return valor != null && !valor.isBlank() && valor.codePoints().noneMatch(Character::isISOControl);
    }

    boolean valida() {
        return variableInvalida() == null;
    }

    int numeroPuerto() {
        return Integer.parseInt(puerto);
    }

    @Override
    public String toString() {
        return "CableadoSmtp[***]";
    }
}
