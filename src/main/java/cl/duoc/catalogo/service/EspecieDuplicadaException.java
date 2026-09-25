package cl.duoc.catalogo.service;

/** 409: ya existe una especie con ese nombreApi (sin distinguir mayúsculas). */
public class EspecieDuplicadaException extends RuntimeException {
    public EspecieDuplicadaException(String mensaje) {
        super(mensaje);
    }
}
