package ar.turnotarde.shared.error;

/** Un campo inválido y el motivo, dentro de {@code detalles} de un error {@code VALIDACION}. */
public record DetalleCampo(String campo, String mensaje) {
}
