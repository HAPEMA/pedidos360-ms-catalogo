package cl.duoc.catalogo.service;

/** 422: la petición es válida, pero API Ninjas no tiene una ficha con ese nombre exacto. */
public class FichaNoEncontradaException extends RuntimeException {
    public FichaNoEncontradaException(String mensaje) {
        super(mensaje);
    }
}
