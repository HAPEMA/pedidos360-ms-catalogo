package cl.duoc.catalogo.web;

import cl.duoc.catalogo.dto.ErrorResponse;
import cl.duoc.catalogo.ninja.NinjaNoDisponibleException;
import cl.duoc.catalogo.service.EspecieDuplicadaException;
import cl.duoc.catalogo.service.FichaNoEncontradaException;
import cl.duoc.catalogo.service.RecursoNoEncontradoException;
import cl.duoc.catalogo.service.SolicitudInvalidaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Todos los errores salen como JSON {"status": n, "error": "mensaje"}.
 * Nunca se devuelve el stack trace ni el mensaje de API Ninjas (podría filtrar detalles internos).
 */
@RestControllerAdvice
public class ManejadorErrores {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> noEncontrado(RecursoNoEncontradoException e) {
        return respuesta(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(EspecieDuplicadaException.class)
    public ResponseEntity<ErrorResponse> duplicada(EspecieDuplicadaException e) {
        return respuesta(HttpStatus.CONFLICT, e.getMessage());
    }

    /** Carrera entre dos POST simultáneos: la restricción UNIQUE de la BD tiene la última palabra. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> integridad(DataIntegrityViolationException e) {
        return respuesta(HttpStatus.CONFLICT, "La especie ya está en el catálogo");
    }

    @ExceptionHandler(FichaNoEncontradaException.class)
    public ResponseEntity<ErrorResponse> fichaNoEncontrada(FichaNoEncontradaException e) {
        return respuesta(HttpStatus.UNPROCESSABLE_CONTENT, e.getMessage());
    }

    @ExceptionHandler(NinjaNoDisponibleException.class)
    public ResponseEntity<ErrorResponse> ninjaNoDisponible(NinjaNoDisponibleException e) {
        // Mensaje fijo: no se propaga lo que respondió API Ninjas.
        return respuesta(HttpStatus.BAD_GATEWAY, "El servicio de fichas de animales no está disponible");
    }

    @ExceptionHandler(SolicitudInvalidaException.class)
    public ResponseEntity<ErrorResponse> solicitudInvalida(SolicitudInvalidaException e) {
        return respuesta(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validacion(MethodArgumentNotValidException e) {
        Map<String, String> detalles = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> detalles.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(new ErrorResponse(400, "La solicitud tiene campos inválidos", detalles));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> cuerpoIlegible(HttpMessageNotReadableException e) {
        return respuesta(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es un JSON válido");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> tipoInvalido(MethodArgumentTypeMismatchException e) {
        return respuesta(HttpStatus.BAD_REQUEST, "El parámetro '" + e.getName() + "' tiene un formato inválido");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> parametroFaltante(MissingServletRequestParameterException e) {
        return respuesta(HttpStatus.BAD_REQUEST, "Falta el parámetro '" + e.getParameterName() + "'");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> metodoNoPermitido(HttpRequestMethodNotSupportedException e) {
        return respuesta(HttpStatus.METHOD_NOT_ALLOWED, "Método no permitido");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> tipoContenido(HttpMediaTypeNotSupportedException e) {
        return respuesta(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "El contenido debe ser application/json");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> rutaInexistente(NoResourceFoundException e) {
        return respuesta(HttpStatus.NOT_FOUND, "Recurso no encontrado");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> inesperado(Exception e) {
        log.error("Error no controlado", e);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno del servidor");
    }

    private static ResponseEntity<ErrorResponse> respuesta(HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).body(new ErrorResponse(estado.value(), mensaje));
    }
}
