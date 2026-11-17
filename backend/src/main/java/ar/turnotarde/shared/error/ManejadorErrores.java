package ar.turnotarde.shared.error;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Único {@code @RestControllerAdvice} de la aplicación: traduce toda excepción de MVC al formato
 * {@link ErrorApi}. No usa {@code ProblemDetail}.
 */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorErrores.class);

    private static final String MENSAJE_VALIDACION = "Hay datos inválidos. Revisalos y probá de nuevo.";

    @ExceptionHandler(ReglaNegocioException.class)
    ResponseEntity<ErrorApi> reglaDeNegocio(ReglaNegocioException e) {
        return responder(e.estado(), new ErrorApi(e.codigo(), e.getMessage(), e.detalles()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorApi> validacion(MethodArgumentNotValidException e) {
        List<DetalleCampo> detalles = new ArrayList<>();
        for (FieldError error : e.getBindingResult().getFieldErrors()) {
            detalles.add(new DetalleCampo(error.getField(), error.getDefaultMessage()));
        }
        for (ObjectError error : e.getBindingResult().getGlobalErrors()) {
            detalles.add(new DetalleCampo(error.getObjectName(), error.getDefaultMessage()));
        }
        return responder(HttpStatus.BAD_REQUEST, new ErrorApi(CodigosError.VALIDACION, MENSAJE_VALIDACION, detalles));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorApi> validacionDeParametros(HandlerMethodValidationException e) {
        List<DetalleCampo> detalles = new ArrayList<>();
        for (ParameterValidationResult resultado : e.getParameterValidationResults()) {
            if (resultado instanceof ParameterErrors errores) {
                for (FieldError error : errores.getFieldErrors()) {
                    detalles.add(new DetalleCampo(error.getField(), error.getDefaultMessage()));
                }
            } else {
                String nombre = resultado.getMethodParameter().getParameterName();
                resultado.getResolvableErrors().forEach(error ->
                        detalles.add(new DetalleCampo(nombre, error.getDefaultMessage())));
            }
        }
        return responder(HttpStatus.BAD_REQUEST, new ErrorApi(CodigosError.VALIDACION, MENSAJE_VALIDACION, detalles));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<ErrorApi> solicitudInvalida(Exception e) {
        return responder(HttpStatus.BAD_REQUEST,
                new ErrorApi(CodigosError.SOLICITUD_INVALIDA, "No pudimos interpretar la solicitud."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorApi> tipoDeContenidoNoSoportado(HttpMediaTypeNotSupportedException e) {
        return responder(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                new ErrorApi(CodigosError.SOLICITUD_INVALIDA, "El formato de la solicitud no está soportado."));
    }

    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ResponseEntity<ErrorApi> noEncontrado(Exception e) {
        return responder(HttpStatus.NOT_FOUND,
                new ErrorApi(CodigosError.NO_ENCONTRADO, "No encontramos lo que buscabas."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorApi> metodoNoPermitido(HttpRequestMethodNotSupportedException e) {
        return responder(HttpStatus.METHOD_NOT_ALLOWED,
                new ErrorApi(CodigosError.METODO_NO_PERMITIDO, "Esa operación no está permitida en este recurso."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorApi> inesperado(Exception e) {
        LOG.error("Error inesperado procesando la solicitud", e);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, new ErrorApi(CodigosError.ERROR_INTERNO,
                "Ocurrió un error inesperado. Probá de nuevo en unos minutos."));
    }

    private static ResponseEntity<ErrorApi> responder(HttpStatus estado, ErrorApi cuerpo) {
        return ResponseEntity.status(estado).contentType(MediaType.APPLICATION_JSON).body(cuerpo);
    }
}
