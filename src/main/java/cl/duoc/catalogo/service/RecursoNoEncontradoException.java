package cl.duoc.catalogo.service;

/** 404: la especie no existe en el catálogo. */
public class RecursoNoEncontradoException extends RuntimeException {
    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
