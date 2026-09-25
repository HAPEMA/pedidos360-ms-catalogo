package cl.duoc.catalogo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/** Formato único de error: {"status": n, "error": "mensaje"} y "detalles" solo en validación. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(int status, String error, Map<String, String> detalles) {

    public ErrorResponse(int status, String error) {
        this(status, error, null);
    }
}
