package cl.duoc.catalogo.dto;

import cl.duoc.catalogo.model.Especie;

import java.util.List;

public record EspecieResponse(
        Long id,
        String nombreApi,
        String nombreEs,
        String nombreCientifico,
        String habitat,
        String dieta,
        String velocidadMaxima,
        String esperanzaVida,
        List<String> ubicaciones,
        Integer precio,
        Integer stock) {

    /** Debe llamarse dentro de una transacción: ubicaciones es una colección perezosa. */
    public static EspecieResponse desde(Especie e) {
        return new EspecieResponse(e.getId(), e.getNombreApi(), e.getNombreEs(), e.getNombreCientifico(),
                e.getHabitat(), e.getDieta(), e.getVelocidadMaxima(), e.getEsperanzaVida(),
                List.copyOf(e.getUbicaciones()), e.getPrecio(), e.getStock());
    }
}
