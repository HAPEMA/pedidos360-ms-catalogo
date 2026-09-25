package cl.duoc.catalogo.dto;

import java.util.List;

/** Ficha de un animal según API Ninjas, indicando si está a la venta en el catálogo. */
public record FichaResponse(
        String nombre,
        String nombreCientifico,
        String habitat,
        String dieta,
        String velocidadMaxima,
        String esperanzaVida,
        List<String> ubicaciones,
        Long especieId,
        boolean enVenta) {
}
