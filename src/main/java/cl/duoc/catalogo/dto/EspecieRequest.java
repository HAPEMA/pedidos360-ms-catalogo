package cl.duoc.catalogo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** Datos que ingresa el administrador; la ficha se copia desde API Ninjas, no del cliente. */
public record EspecieRequest(
        @NotBlank(message = "es obligatorio") String nombreApi,
        @NotBlank(message = "es obligatorio") String nombreEs,
        @NotNull(message = "es obligatorio") @Positive(message = "debe ser mayor que 0") Integer precio,
        @NotNull(message = "es obligatorio") @PositiveOrZero(message = "no puede ser negativo") Integer stock) {
}
