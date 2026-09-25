package cl.duoc.catalogo.ninja;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.List;

/** Cliente de la Animals API de API Ninjas. */
@Component
public class NinjaClient {

    private static final Logger log = LoggerFactory.getLogger(NinjaClient.class);

    private final RestClient restClient;

    /** Recibe el Builder para que los tests puedan enlazar un MockRestServiceServer. */
    public NinjaClient(RestClient.Builder builder, NinjaProperties propiedades) {
        this.restClient = builder
                .baseUrl(propiedades.baseUrl())
                // La key se agrega aquí, en el servidor; nunca viaja al navegador.
                .defaultHeader("X-Api-Key", propiedades.apiKey())
                .build();
    }

    /** Busca por nombre (en inglés, coincidencia parcial). Nunca devuelve null. */
    public List<NinjaAnimal> buscar(String nombre) {
        try {
            NinjaAnimal[] respuesta = restClient.get()
                    .uri(uri -> uri.path("/animals").queryParam("name", "{nombre}").build(nombre))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(NinjaAnimal[].class);
            return respuesta == null ? List.of() : Arrays.asList(respuesta);
        } catch (RestClientException e) {
            // Solo se registra el tipo de error: el mensaje podría incluir datos de la petición.
            log.warn("Fallo al consultar API Ninjas: {}", e.getClass().getSimpleName());
            throw new NinjaNoDisponibleException(e);
        }
    }
}
