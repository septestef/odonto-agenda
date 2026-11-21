package ar.turnotarde.shared.error;

/** Códigos técnicos de error (los de reglas de negocio son {@code RN-XX-NN} y viven con cada regla). */
public final class CodigosError {

    public static final String VALIDACION = "VALIDACION";
    public static final String SOLICITUD_INVALIDA = "SOLICITUD_INVALIDA";
    public static final String NO_ENCONTRADO = "NO_ENCONTRADO";
    public static final String METODO_NO_PERMITIDO = "METODO_NO_PERMITIDO";
    public static final String NO_AUTENTICADO = "NO_AUTENTICADO";
    public static final String ACCESO_DENEGADO = "ACCESO_DENEGADO";
    public static final String ERROR_INTERNO = "ERROR_INTERNO";

    private CodigosError() {
    }
}
