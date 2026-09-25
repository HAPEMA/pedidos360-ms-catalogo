package cl.duoc.catalogo.ninja;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Respuesta de API Ninjas (/v1/animals). Todos los valores son strings y cualquier
 * campo puede faltar, por eso los accesos derivados son null-safe.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NinjaAnimal(
        String name,
        Taxonomia taxonomy,
        List<String> locations,
        Caracteristicas characteristics) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Taxonomia(@JsonProperty("scientific_name") String scientificName) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Caracteristicas(
            String habitat,
            String diet,
            @JsonProperty("top_speed") String topSpeed,
            String lifespan) {
    }

    public String nombreCientifico() {
        return taxonomy == null ? null : taxonomy.scientificName();
    }

    public String habitat() {
        return characteristics == null ? null : characteristics.habitat();
    }

    public String dieta() {
        return characteristics == null ? null : characteristics.diet();
    }

    public String velocidadMaxima() {
        return characteristics == null ? null : characteristics.topSpeed();
    }

    public String esperanzaVida() {
        return characteristics == null ? null : characteristics.lifespan();
    }

    public List<String> ubicaciones() {
        return locations == null ? List.of() : List.copyOf(locations);
    }
}
