package com.pucp.paqrap.comun.error;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.List;

/** Traduce las excepciones de los controllers a respuestas {@link ApiError}. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validacion(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.CampoInvalido> detalles = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.CampoInvalido(error.getField(), error.getDefaultMessage()))
                .toList();
        return responder(HttpStatus.BAD_REQUEST, "La solicitud tiene campos inválidos", request, detalles);
    }

    /** Los records del dominio validan sus invariantes lanzando IllegalArgumentException. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> argumentoInvalido(IllegalArgumentException ex, HttpServletRequest request) {
        return responder(HttpStatus.BAD_REQUEST, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> cuerpoIlegible(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return responder(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es un JSON válido", request, List.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> tipoInvalido(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String mensaje = "Valor inválido para el parámetro '" + ex.getName() + "': " + ex.getValue();
        return responder(HttpStatus.BAD_REQUEST, mensaje, request, List.of());
    }

    @ExceptionHandler({RecursoNoEncontradoException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiError> noEncontrado(Exception ex, HttpServletRequest request) {
        return responder(HttpStatus.NOT_FOUND, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(ReglaNegocioException.class)
    public ResponseEntity<ApiError> reglaNegocio(ReglaNegocioException ex, HttpServletRequest request) {
        return responder(HttpStatus.CONFLICT, ex.getMessage(), request, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integridad(DataIntegrityViolationException ex, HttpServletRequest request) {
        return responder(HttpStatus.CONFLICT, "La operación viola una restricción de la base de datos", request, List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> inesperado(Exception ex, HttpServletRequest request) {
        log.error("Error no controlado en {}", request.getRequestURI(), ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor", request, List.of());
    }

    private ResponseEntity<ApiError> responder(HttpStatus status, String mensaje, HttpServletRequest request,
                                               List<ApiError.CampoInvalido> detalles) {
        ApiError cuerpo = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), mensaje,
                request.getRequestURI(), detalles);
        return ResponseEntity.status(status).body(cuerpo);
    }
}
