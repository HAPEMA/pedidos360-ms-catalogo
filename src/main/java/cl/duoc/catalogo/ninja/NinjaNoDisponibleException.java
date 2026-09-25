package cl.duoc.catalogo.ninja;

/** API Ninjas falló o no respondió. Se traduce a 502 sin propagar el mensaje original. */
public class NinjaNoDisponibleException extends RuntimeException {

    public NinjaNoDisponibleException(Throwable causa) {
        super("El servicio de fichas de animales no está disponible", causa);
    }
}
