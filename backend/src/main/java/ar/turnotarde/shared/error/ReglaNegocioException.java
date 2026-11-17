package ar.turnotarde.shared.error;

import org.springframework.http.HttpStatus;

/** Rechazo por una regla de negocio ({@code RN-XX-NN}) con el estado HTTP que la regla define. */
public class ReglaNegocioException extends RuntimeException {

    private final String codigo;
    private final HttpStatus estado;
    private final transient Object detalles;

    public ReglaNegocioException(String codigo, String mensaje, HttpStatus estado) {
        this(codigo, mensaje, estado, null);
    }

    public ReglaNegocioException(String codigo, String mensaje, HttpStatus estado, Object detalles) {
        super(mensaje);
        this.codigo = codigo;
        this.estado = estado;
        this.detalles = detalles;
    }

    public String codigo() {
        return codigo;
    }

    public HttpStatus estado() {
        return estado;
    }

    public Object detalles() {
        return detalles;
    }
}
