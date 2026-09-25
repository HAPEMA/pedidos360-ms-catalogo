package cl.duoc.catalogo.service;

/** 400: parámetro de entrada inválido. */
public class SolicitudInvalidaException extends RuntimeException {
    public SolicitudInvalidaException(String mensaje) {
        super(mensaje);
    }
}
